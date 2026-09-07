package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.wpidhy.PsseWpidhydGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderWpidhydTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void aliasImportsExactRecordAndInitializesOnTrateBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("wpidhydu.dyr");
        Files.writeString(dyr,
                "1 'WPIDHYDU' '1' .2 -.05 2 1 .1 .2 .3 .1 -.1 1 0 .6 1.2 0 .1 .1 .4 .3 .7 .7 1.1 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.3);
        machine.setPe(.3);
        machine.setSpeed(1.0);
        PsseWpidhydGovernor governor = (PsseWpidhydGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals("WPIDHYD", governor.getName());
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(.625, governor.getGatePosition(), TOL);
        assertEquals(.3, governor.getOutput(machine), TOL);
        advance(governor, machine, 50, .01);
        assertEquals(.3, governor.getOutput(machine), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void electricalPowerFilterAndPidCloseGateForPositivePowerError() throws Exception {
        Fixture f = fixture();
        double gate0 = f.governor.getGatePosition();
        f.machine.setPe(.7);

        advance(f, 80, .01);

        assertTrue(f.governor.getMeasuredPowerDeviation() < 0.0);
        assertTrue(f.governor.getControlError() < 0.0);
        assertTrue(f.governor.getSecondLagOutput() < 0.0);
        assertTrue(f.governor.getGatePosition() < gate0);
    }

    @Test
    void speedDeadbandAndAllPidBranchesDriveGate() throws Exception {
        Fixture f = fixture();
        double gate0 = f.governor.getGatePosition();
        f.machine.setSpeed(1.012);

        advance(f, 60, .01);

        assertEquals(.01, f.governor.applySpeedDeadband(.012), TOL);
        assertTrue(f.governor.getPidOutput() < 0.0);
        assertTrue(f.governor.getFirstLagOutput() < 0.0);
        assertTrue(f.governor.getGatePosition() < gate0);
    }

    @Test
    void oneStepMatchesPublishedPidAndSquaredTaFilterEquations() throws Exception {
        Fixture f = fixture();
        f.machine.setSpeed(1.012);

        advance(f, 1, .01);

        // Transformed first-lag state preserves the s*Kd input without a
        // numerical derivative: x1=z1+(Kd/Ta)*error.
        assertEquals(-.00573375, f.governor.getFirstLagOutput(), 1.0e-10);
        assertEquals(-.0002625, f.governor.getSecondLagOutput(), 1.0e-10);
        assertEquals(-4.166666666666667e-6, f.governor.getServoRate(), 1.0e-12);
        assertEquals(.625, f.governor.getGatePosition(), TOL);
    }

    @Test
    void gateRatePositionAndPowerLimitsAreAppliedInDiagramOrder() throws Exception {
        Fixture f = fixture();
        double gate0 = f.governor.getGatePosition();
        f.governor.getData().setVelmax(.02);
        f.governor.getData().setGmax(gate0 + .001);
        f.governor.getData().setPmax(.5);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));
        assertEquals(.6, f.governor.getEffectivePmax(), TOL);
        f.machine.setSpeed(.98);

        advance(f, 200, .01);

        assertEquals(gate0 + .001, f.governor.getGatePosition(), 1.0e-7);
        assertTrue(f.governor.getServoRate() > f.governor.getEffectiveVelmax());
        assertTrue(f.governor.getOutput(f.machine) > .6);
    }

    @Test
    void nonlinearPowerCurveAndWaterColumnMatchPublishedTransfers() throws Exception {
        Fixture f = fixture();
        assertEquals(.3, f.governor.powerFromGate(.4), TOL);
        assertEquals(.5, f.governor.powerFromGate(.55), TOL);
        assertEquals(.7, f.governor.powerFromGate(.7), TOL);
        assertEquals(.625, f.governor.gateFromPower(.6), TOL);
        f.machine.setSpeed(.98);

        advance(f, 20, .01);

        assertTrue(f.governor.getGatePosition() > .625);
        assertTrue(f.governor.getWaterColumnOutput()
                < f.governor.powerFromGate(f.governor.getGatePosition()));
    }

    @Test
    void powerWorldTimeAndLimitCorrectionsDoNotMutateSourceData() throws Exception {
        Fixture f = fixture();
        f.governor.configureIntegrationStep(.01);
        f.governor.getData().setTreg(.004);
        f.governor.getData().setTa(.003);
        f.governor.getData().setTb(.004);
        f.governor.getData().setTw(.006);
        f.governor.getData().setVelmax(-.2);
        f.governor.getData().setVelmin(.1);
        f.governor.getData().setGmax(.4);
        f.governor.getData().setGmin(.8);
        f.governor.getData().setPmax(.4);
        f.governor.getData().setPmin(.8);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));

        assertEquals(0.0, f.governor.getEffectiveTreg(), TOL);
        assertEquals(.005, f.governor.getEffectiveTa(), TOL);
        assertEquals(.01, f.governor.getEffectiveTb(), TOL);
        assertEquals(.01, f.governor.getEffectiveTw(), TOL);
        assertEquals(.1, f.governor.getEffectiveVelmax(), TOL);
        assertEquals(-.2, f.governor.getEffectiveVelmin(), TOL);
        assertEquals(.8, f.governor.getEffectiveGmax(), TOL);
        assertEquals(.4, f.governor.getEffectiveGmin(), TOL);
        assertEquals(.8, f.governor.getEffectivePmax(), TOL);
        assertEquals(.4, f.governor.getEffectivePmin(), TOL);
        assertEquals(.004, f.governor.getData().getTreg(), TOL);
    }

    @Test
    void zeroTimeConstantsUseDocumentedAlgebraicPaths() throws Exception {
        Fixture f = fixture();
        f.governor.getData().setTreg(0.0);
        f.governor.getData().setTa(0.0);
        f.governor.getData().setTb(0.0);
        f.governor.getData().setTw(0.0);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));
        f.machine.setSpeed(1.012);

        advance(f, 1, .01);

        assertTrue(Double.isFinite(f.governor.getPidOutput()));
        assertTrue(Double.isFinite(f.governor.getFirstLagOutput()));
        assertTrue(Double.isFinite(f.governor.getOutput(f.machine)));
    }

    @Test
    void strictImportRejectsNonmonotonicGatePowerCurve() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("wpidhyd-invalid.dyr");
        Files.writeString(dyr,
                "1 'WPIDHYD' '1' .2 -.05 2 1 .1 .2 .3 .1 -.1 1 0 .6 1.2 0 .1 .1 .7 .3 .4 .7 1.1 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    private static Fixture fixture() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseWpidhydGovernor governor = builder.addGovWpidhyd("Bus1", "1",
                .2, -.05, 2, 1, .1, .2, .3, .1, -.1, 1, 0, .6,
                1.2, 0, .1, .1, .4, .3, .7, .7, 1.1, .002, -.003, 0);
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static void advance(Fixture f, int steps, double dt) {
        advance(f.governor, f.machine, steps, dt);
    }

    private static void advance(PsseWpidhydGovernor governor, Machine machine,
            int steps, double dt) {
        for (int i = 0; i < steps; i++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 1));
        }
    }

    private record Fixture(Machine machine, PsseWpidhydGovernor governor) { }
}
