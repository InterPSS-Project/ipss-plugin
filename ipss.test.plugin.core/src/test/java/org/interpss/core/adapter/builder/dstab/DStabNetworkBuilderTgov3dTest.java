package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.tgov3.PsseTgov3dGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderTgov3dTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void aliasImportsExactRecordAndInitializesOnTurbineBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("tgov3du.dyr");
        Files.writeString(dyr,
                "1 'TGOV3DU' '1' 20 .1 .05 .1 1 -1 1 0 .2 .3 .3 .4 .4 .3 .02 .04 .08 2 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.3);
        machine.setPe(.3);
        machine.setSpeed(1.0);
        PsseTgov3dGovernor governor = (PsseTgov3dGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals("TGOV3D", governor.getName());
        assertEquals(.02, governor.getData().getTa(), TOL);
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(.6, governor.getValvePosition(), TOL);
        assertEquals(.3, governor.getOutput(machine), TOL);
        advance(governor, machine, 50, .01);
        assertEquals(.3, governor.getOutput(machine), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void leadLagSpeedPathMatchesPublishedTransferFunction() throws Exception {
        Fixture f = fixture();
        f.machine.setSpeed(1.012);

        advance(f, 1, .01);

        assertEquals(.1095, f.governor.getLeadLagOutput(), 1.0e-12);
        assertTrue(f.governor.getValvePosition() < .6);
    }

    @Test
    void valveVelocityAndPositionLimitsStopOutwardMotion() throws Exception {
        Fixture f = fixture();
        f.governor.getData().setUo(.05);
        f.governor.getData().setUc(-.04);
        f.governor.getData().setPmax(.605);
        f.governor.getData().setPmin(.59);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));
        f.machine.setSpeed(1.1);

        advance(f, 1, .01);
        assertEquals(.5996, f.governor.getValvePosition(), 1.0e-12);
        advance(f, 40, .01);
        assertEquals(.59, f.governor.getValvePosition(), TOL);
    }

    @Test
    void shaftFractionsUseBowlInterceptFlowAndCrossoverSignals() throws Exception {
        Fixture f = fixture();
        f.governor.initiateFastValving();
        advance(f, 1, .01);

        double expected = .3 * f.governor.getSteamBowlOutput()
                + .4 * f.governor.getInterceptFlow()
                + .3 * f.governor.getCrossoverOutput();
        assertEquals(expected, f.governor.getOutput(f.machine), 1.0e-12);
        assertTrue(f.governor.getOutput(f.machine) < .6);
    }

    @Test
    void reheaterIntegratorFeedsBackPreCrossoverInterceptFlow() throws Exception {
        Fixture f = fixture();
        f.governor.initiateFastValving();

        advance(f, 1, .01);

        // During valve closure, flow falls immediately while the T6 crossover
        // state remains higher. The T5 pressure must therefore rise according
        // to bowl minus pre-T6 flow, as shown by the published diagram.
        assertEquals(.6006602547789194, f.governor.getReheaterPressure(), 1.0e-12);
        assertTrue(f.governor.getCrossoverOutput() > f.governor.getInterceptFlow());
    }

    @Test
    void fastValvingClosesHoldsAndReopensOnDocumentedSchedule() throws Exception {
        Fixture f = fixture();
        f.governor.initiateFastValving();
        assertTrue(f.governor.isFastValvingActive());
        assertEquals(1.0, f.governor.getInterceptValvePosition(), TOL);

        advance(f, 1, .01);
        assertEquals(.5, f.governor.getInterceptValvePosition(), TOL);
        assertEquals(.933974522108058, f.governor.getInterceptValveFlowGain(), TOL);
        advance(f, 1, .01);
        assertEquals(0.0, f.governor.getInterceptValvePosition(), TOL);
        advance(f, 2, .01);
        assertEquals(0.0, f.governor.getInterceptValvePosition(), TOL);
        advance(f, 2, .01);
        assertEquals(.5, f.governor.getInterceptValvePosition(), TOL);
        advance(f, 2, .01);
        assertEquals(1.0, f.governor.getInterceptValvePosition(), TOL);
        assertFalse(f.governor.isFastValvingActive());
    }

    @Test
    void fixedExponentialValveCurvePassesPublishedCharacteristicPoint()
            throws Exception {
        Fixture f = fixture();
        f.governor.initiateFastValving();

        advance(f, 1, .014);

        assertEquals(.3, f.governor.getInterceptValvePosition(), TOL);
        assertEquals(.8, f.governor.getInterceptValveFlowGain(), TOL);
    }

    @Test
    void correctionsLimitsAndDeadbandFollowPowerWorldRules() throws Exception {
        Fixture f = fixture();
        f.governor.configureIntegrationStep(.01);
        f.governor.getData().setT3(.001);
        f.governor.getData().setT5(.003);
        f.governor.getData().setUo(-.1);
        f.governor.getData().setUc(.2);
        f.governor.getData().setPmax(.5);
        f.governor.getData().setPmin(.9);
        f.governor.getData().setPrmax(.5);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));

        assertEquals(0.0, f.governor.getEffectiveT3(), TOL);
        assertEquals(.005, f.governor.getEffectiveT5(), TOL);
        assertEquals(.2, f.governor.getEffectiveUo(), TOL);
        assertEquals(-.1, f.governor.getEffectiveUc(), TOL);
        assertEquals(.9, f.governor.getEffectivePmax(), TOL);
        assertEquals(.5, f.governor.getEffectivePmin(), TOL);
        assertEquals(.6, f.governor.getEffectivePrmax(), TOL);
        assertEquals(0.0, f.governor.applySpeedDeadband(.001), TOL);
    }

    @Test
    void strictImportRejectsInvalidFastValveSchedule() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("tgov3d-invalid.dyr");
        Files.writeString(dyr,
                "1 'TGOV3D' '1' 20 .1 .05 .1 1 -1 1 0 .2 .3 .3 .4 .4 .3 .05 .02 .08 2 .002 -.003 50 /\n");
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
        PsseTgov3dGovernor governor = builder.addGovTgov3d("Bus1", "1",
                20, .1, .05, .1, 1, -1, 1, 0, .2, .3, .3, .4,
                .4, .3, .02, .04, .08, 2, .002, -.003, 0);
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static void advance(Fixture f, int steps, double dt) {
        advance(f.governor, f.machine, steps, dt);
    }

    private static void advance(PsseTgov3dGovernor governor, Machine machine,
            int steps, double dt) {
        for (int i = 0; i < steps; i++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 1));
        }
    }

    private record Fixture(Machine machine, PsseTgov3dGovernor governor) { }
}
