package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.wesgov.PsseWesgovdGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderWesgovdTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir
    Path tempDir;

    @Test
    void aliasImportsExactRecordAndInitializesOnTurbineBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("wesgovdu.dyr");
        Files.writeString(dyr,
                "1 'WESGOVDU' '1' .05 .1 .05 2 1 .2 .3 .01 .1 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseWesgovdGovernor governor = (PsseWesgovdGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals("WESGOVD", governor.getName());
        assertEquals(.05, governor.getData().getDeltaTc(), TOL);
        assertEquals(.1, governor.getData().getDeltaTp(), TOL);
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertEquals(50.0, governor.getData().getTrate(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(Set.of("PE Measured", "Control", "Valve", "Mechanical Power"),
                governor.getNamedStates().keySet());
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(1.2, governor.getHeldControl(), TOL);
        assertEquals(.6, governor.getOutput(machine), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void speedDeadbandSamplingAndAlimAreDiscrete() throws Exception {
        Fixture f = fixture(.05, .1, .01, 0, 0, 0);

        f.machine.setSpeed(1.001);
        advance(f.governor, f.machine, 5, .01);
        assertEquals(0.0, f.governor.getSampledSpeedDeviation(), TOL);
        assertEquals(.6, f.governor.getOutput(f.machine), TOL);

        f.machine.setSpeed(1.012);
        advance(f.governor, f.machine, 4, .01);
        assertEquals(0.0, f.governor.getSampledSpeedDeviation(), TOL,
                "The control input must remain held before DeltaTC expires");
        advance(f.governor, f.machine, 1, .01);
        assertEquals(.01, f.governor.getSampledSpeedDeviation(), TOL);
        assertEquals(1.19, f.governor.getHeldControl(), TOL,
                "Alim must cap one digital control update to 0.01 pu");
        assertEquals(.595, f.governor.getOutput(f.machine), TOL);
    }

    @Test
    void electricalPowerUsesIndependentFilterAndSampleHold() throws Exception {
        Fixture f = fixture(.05, .1, 1.0, 0, 0, .05);
        f.machine.setPe(.8);

        advance(f.governor, f.machine, 5, .01);
        assertEquals(1.2, f.governor.getSampledElectricalPower(), TOL);
        assertEquals(1.2, f.governor.getHeldControl(), TOL,
                "DeltaTC alone must not refresh the DeltaTP power sample");

        advance(f.governor, f.machine, 5, .01);
        assertTrue(f.governor.getSampledElectricalPower() > 1.5);
        assertTrue(f.governor.getHeldControl() < 1.2,
                "The newly sampled higher Pelec must reduce the control command");
    }

    @Test
    void zeroSamplePeriodsUseContinuousInputs() throws Exception {
        Fixture f = fixture(0, 0, 1.0, 0, 0, 0);
        f.machine.setSpeed(1.012);
        f.machine.setPe(.8);

        advance(f.governor, f.machine, 1, .01);

        assertEquals(.01, f.governor.getSampledSpeedDeviation(), TOL);
        assertEquals(1.6, f.governor.getSampledElectricalPower(), TOL);
        assertEquals(1.1997, f.governor.getIntegratorState(), TOL);
        assertEquals(1.1397, f.governor.getHeldControl(), TOL);
        assertEquals(.56985, f.governor.getOutput(f.machine), TOL);
    }

    @Test
    void twoSerialTurbineLagsUseModifiedEulerAndTimeCorrections() throws Exception {
        Fixture f = fixture(.01, 0, 1.0, .003, .007, 0);
        f.governor.configureIntegrationStep(.01);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));
        assertEquals(0.0, f.governor.getEffectiveT1(), TOL);
        assertEquals(.01, f.governor.getEffectiveT2(), TOL);

        f.machine.setSpeed(1.012);
        advance(f.governor, f.machine, 1, .01);
        assertTrue(f.governor.getHeldControl() < 1.2);
        assertEquals(.6, f.governor.getOutput(f.machine), TOL,
                "The control sample is committed after the continuous corrector");
        advance(f.governor, f.machine, 1, .01);
        assertTrue(f.governor.getOutput(f.machine) < .6);
    }

    @Test
    void strictImportRejectsInvalidDeadband() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("wesgovd-invalid.dyr");
        Files.writeString(dyr,
                "1 'WESGOVD' '1' .05 .1 .05 2 1 .2 .3 .01 .1 -.002 .003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    private static Fixture fixture(double deltaTc, double deltaTp, double alim,
            double t1, double t2, double tpe) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseWesgovdGovernor governor = builder.addGovWesgovd("Bus1", "1",
                deltaTc, deltaTp, .05, 2, 1, t1, t2, alim, tpe,
                .002, -.003, 50);
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static void advance(PsseWesgovdGovernor governor, Machine machine,
            int steps, double dt) {
        for (int i = 0; i < steps; i++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
    }

    private record Fixture(Machine machine, PsseWesgovdGovernor governor) { }
}
