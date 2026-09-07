package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.pidgov.PssePidgovdGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderPidgovdTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void aliasImportsExactRecordAndInitializesOnTurbineBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pidgovdu.dyr");
        Files.writeString(dyr,
                "1 'PIDGOVDU' '1' 1 .04 .1 1.5 .5 .2 .02 .1 .1 0 .3 .2 .6 .8 1 1 0 1 .5 .2 -.2 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.3);
        machine.setPe(.3);
        machine.setSpeed(1.0);
        PssePidgovdGovernor governor = (PssePidgovdGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals("PIDGOVD", governor.getName());
        assertEquals(1, governor.getData().getFeedback());
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertEquals(50.0, governor.getData().getTrate(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(.5, governor.getGatePosition(), TOL);
        assertEquals(.3, governor.getOutput(machine), TOL);
        advance(governor, machine, 50, .01);
        assertEquals(.3, governor.getOutput(machine), TOL,
                "A fitted PIDGOVD operating point must remain stationary");
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void gatePowerCurveAndServoFollowPublishedBlocks() throws Exception {
        Fixture f = fixture(1, 0, 0, 1, 0, 0, 0, .1, 0, 0, 0);
        assertEquals(.15, f.governor.powerFromGate(.15), TOL);
        assertEquals(.45, f.governor.gateFromPower(.45), TOL);
        f.machine.setSpeed(1.01);

        advance(f, 1, .01);

        assertEquals(.59924, f.governor.getGatePosition(), 1.0e-12);
        assertEquals(.59924, f.governor.getOutput(f.machine), 1.0e-12);
    }

    @Test
    void selectedFeedbackAndDroopTransducerAreIndependent() throws Exception {
        Fixture electric = fixture(0, .1, .1, 1, 0, 0, 0, .1, 0, 0, 0);
        Fixture gate = fixture(1, .1, .1, 1, 0, 0, 0, .1, 0, 0, 0);
        electric.machine.setPe(.8);
        gate.machine.setPe(.8);

        advance(electric, 1, .01);
        advance(gate, 1, .01);

        assertEquals(-.0019, electric.governor.getDroopFilterOutput(), 1.0e-12);
        assertEquals(0.0, gate.governor.getDroopFilterOutput(), TOL);
    }

    @Test
    void filteredDerivativeAndTwoRegulatorStagesMatchDiagram() throws Exception {
        Fixture f = fixture(1, 0, 0, 0, 0, .2, .1, 1, 0, 0, 0);
        f.machine.setSpeed(1.01);

        advance(f, 1, .01);

        assertEquals(-.01448, f.governor.getDerivativeOutput(), 1.0e-12);
        assertEquals(.59856, f.governor.getRegulatorOutput(), 1.0e-12);
    }

    @Test
    void waterColumnTransferUsesNonminimumPhaseNumerator() throws Exception {
        Fixture f = fixture(1, 0, 0, 1, 0, 0, 0, .1, 0, 1, .5);
        f.machine.setSpeed(1.01);

        advance(f, 1, .01);

        assertEquals(.599984, f.governor.getWaterState(), 1.0e-12);
        assertEquals(.601472, f.governor.getWaterColumnOutput(), 1.0e-12);
        assertEquals(.601472, f.governor.getOutput(f.machine), 1.0e-12);
    }

    @Test
    void turbineDampingSubtractsSpeedDeviation() throws Exception {
        Fixture f = fixture(1, 0, 0, 0, 0, 0, 0, .1, 2, 0, 0);
        f.machine.setSpeed(1.01);

        advance(f, 1, .01);

        assertEquals(.58, f.governor.getOutput(f.machine), 1.0e-12);
    }

    @Test
    void gateVelocityAndPositionLimitsStopOutwardMotion() throws Exception {
        Fixture f = fixture(1, 0, 0, 100, 0, 0, 0, .01, 0, 0, 0);
        f.governor.getData().setVelmax(.05);
        f.governor.getData().setVelmin(-.05);
        f.governor.getData().setGmax(.61);
        f.governor.getData().setGmin(.59);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));
        f.machine.setSpeed(1.1);

        advance(f, 1, .01);
        assertEquals(.5995, f.governor.getGatePosition(), 1.0e-12);
        advance(f, 40, .01);
        assertEquals(.59, f.governor.getGatePosition(), TOL);
    }

    @Test
    void correctionsLimitsAndDeadbandFollowPowerWorldRules() throws Exception {
        Fixture f = fixture(1, 0, 0, 1, 0, 0, .003, .004, 0, 1, .007);
        f.governor.configureIntegrationStep(.01);
        f.governor.getData().setGmax(.5);
        f.governor.getData().setGmin(.9);
        f.governor.getData().setVelmax(-.1);
        f.governor.getData().setVelmin(.2);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));

        assertEquals(.005, f.governor.getEffectiveTa(), TOL);
        assertEquals(.01, f.governor.getEffectiveTb(), TOL);
        assertEquals(.01, f.governor.getEffectiveTw(), TOL);
        assertEquals(.9, f.governor.getEffectiveGmax(), TOL);
        assertEquals(.5, f.governor.getEffectiveGmin(), TOL);
        assertEquals(.2, f.governor.getEffectiveVelmax(), TOL);
        assertEquals(-.1, f.governor.getEffectiveVelmin(), TOL);
        assertEquals(0.0, f.governor.applySpeedDeadband(.001), TOL);
    }

    @Test
    void strictImportRejectsInvalidFeedbackSelector() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pidgovd-invalid.dyr");
        Files.writeString(dyr,
                "1 'PIDGOVD' '1' 2 .04 .1 1.5 .5 .2 .02 .1 .1 0 .3 .2 .6 .8 1 1 0 1 .5 .2 -.2 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    private static Fixture fixture(int feedback, double rperm, double treg,
            double kp, double ki, double kd, double ta, double tb, double dturb,
            double atw, double tw) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PssePidgovdGovernor governor = builder.addGovPidgovd("Bus1", "1",
                feedback, rperm, treg, kp, ki, kd, ta, tb, dturb,
                0, .3, .3, .6, .6, 1, 1, 0, atw, tw, 10, -10,
                .002, -.003, 0);
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static void advance(Fixture f, int steps, double dt) {
        advance(f.governor, f.machine, steps, dt);
    }

    private static void advance(PssePidgovdGovernor governor, Machine machine,
            int steps, double dt) {
        for (int i = 0; i < steps; i++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 1));
        }
    }

    private record Fixture(Machine machine, PssePidgovdGovernor governor) { }
}
