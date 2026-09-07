package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.hygov2.PsseHygov2dGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderHygov2dTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void aliasImportsExactRecordAndInitializesPmaxAsPenstockGain() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("hygov2du.dyr");
        Files.writeString(dyr,
                "1 'HYGOV2DU' '1' 2 1 1 .1 .2 .3 .4 .5 .6 1 .2 .05 .1 1 0 1.2 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.3);
        machine.setPe(.3);
        machine.setSpeed(1.0);
        PsseHygov2dGovernor governor = (PsseHygov2dGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals("HYGOV2D", governor.getName());
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(.5, governor.getGatePosition(), TOL);
        assertEquals(.3, governor.getOutput(machine), TOL);
        advance(governor, machine, 50, .01);
        assertEquals(.3, governor.getOutput(machine), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void piGovernorAndLeadLagPathsRespondToOverspeed() throws Exception {
        Fixture f = fixture();
        f.machine.setSpeed(1.012);

        advance(f, 1, .01);

        assertEquals(.01, f.governor.applySpeedDeadband(.012), TOL);
        assertTrue(f.governor.getPiOutput() > 0.0);
        assertTrue(f.governor.getGovernorOutput() < 0.0);
        assertTrue(f.governor.getGovernorSpeed() < 0.0);
        assertTrue(f.governor.getGatePosition() < .5);
    }

    @Test
    void gateVelocityAndPositionLimitsUsePsseNonWindupBehavior() throws Exception {
        Fixture f = fixture();
        f.governor.getData().setVgmax(.02);
        f.governor.getData().setGmax(.5001);
        f.governor.getData().setGmin(.49);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));
        f.governor.setAuxiliaryInput(10.0);

        advance(f, 100, .01);

        assertEquals(.5001, f.governor.getGatePosition(), TOL);
        advance(f, 20, .01);
        assertEquals(.5001, f.governor.getGatePosition(), TOL);
    }

    @Test
    void nonminimumPhasePenstockInitiallyOpposesGateOpening() throws Exception {
        Fixture f = fixture();
        f.governor.setAuxiliaryInput(2.0);

        advance(f, 20, .01);

        assertTrue(f.governor.getGatePosition() > .5);
        assertTrue(f.governor.getPenstockOutput() < f.governor.getGatePosition());
        assertTrue(f.governor.getOutput(f.machine) < 1.2 * f.governor.getGatePosition());
    }

    @Test
    void temporaryDroopWashoutRespondsOnlyWhileGateChanges() throws Exception {
        Fixture f = fixture();
        assertEquals(0.0, f.governor.getTemporaryDroopOutput(), TOL);
        f.governor.setAuxiliaryInput(2.0);

        advance(f, 20, .01);

        assertTrue(f.governor.getTemporaryDroopOutput() > 0.0);
    }

    @Test
    void correctionsLimitsAndDeadbandFollowPowerWorldRules() throws Exception {
        Fixture f = fixture();
        f.governor.configureIntegrationStep(.01);
        f.governor.getData().setT3(.001);
        f.governor.getData().setT4(.002);
        f.governor.getData().setT6(.003);
        f.governor.getData().setTr(.004);
        f.governor.getData().setVgmax(-.2);
        f.governor.getData().setGmax(.4);
        f.governor.getData().setGmin(.8);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));

        assertEquals(.01, f.governor.getEffectiveT3(), TOL);
        assertEquals(.01, f.governor.getEffectiveT4(), TOL);
        assertEquals(.01, f.governor.getEffectiveT6(), TOL);
        assertEquals(.01, f.governor.getEffectiveTr(), TOL);
        assertEquals(.2, f.governor.getEffectiveVgmax(), TOL);
        assertEquals(.8, f.governor.getEffectiveGmax(), TOL);
        assertEquals(.4, f.governor.getEffectiveGmin(), TOL);
        assertEquals(0.0, f.governor.applySpeedDeadband(.001), TOL);
    }

    @Test
    void strictImportRejectsZeroRequiredTimeConstant() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("hygov2d-invalid.dyr");
        Files.writeString(dyr,
                "1 'HYGOV2D' '1' 2 1 1 .1 .2 0 .4 .5 .6 1 .2 .05 .1 1 0 1.2 .002 -.003 50 /\n");
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
        PsseHygov2dGovernor governor = builder.addGovHygov2d("Bus1", "1",
                2, 1, 1, .1, .2, .3, .4, .5, .6, 1, .2, .05,
                .1, 1, 0, 1.2, .002, -.003, 0);
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static void advance(Fixture f, int steps, double dt) {
        advance(f.governor, f.machine, steps, dt);
    }

    private static void advance(PsseHygov2dGovernor governor, Machine machine,
            int steps, double dt) {
        for (int i = 0; i < steps; i++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 1));
        }
    }

    private record Fixture(Machine machine, PsseHygov2dGovernor governor) { }
}
