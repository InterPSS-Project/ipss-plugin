package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.degov1.PsseDegov1dGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderDegov1dTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void aliasImportsExactRecordAndInitializesOnTurbineBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("degov1du.dyr");
        Files.writeString(dyr,
                "1 'DEGOV1DU' '1' 1 .1 .2 .05 20 .03 .4 .1 .02 1 .1 .04 .05 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseDegov1dGovernor governor = (PsseDegov1dGovernor) machine.getGovernor();
        assertNotNull(governor);
        assertEquals("DEGOV1D", governor.getName());
        assertEquals(1, governor.getData().getDroopControl());
        assertEquals(.02, governor.getData().getTd(), TOL);
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(6, governor.getNamedStates().size());
        assertEquals(governor.getActuatorOutput(),
                governor.getNamedState("Actuator Output"), TOL);
        assertEquals(50.0, governor.getGovernorBaseMva(machine), TOL);
        assertEquals(1.2, governor.getActuatorOutput(), TOL);
        assertEquals(.6, governor.getOutput(machine), TOL);
        advance(governor, machine, 50, .01);
        assertEquals(.6, governor.getOutput(machine), TOL,
                "A fitted DEGOV1D operating point must remain stationary");
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void electricControlBoxUsesPublishedSecondOrderTransferFunction() throws Exception {
        Fixture f = fixture(0, .1, .2, .05, 1, 0, 0, 0, 0, 0, 0);
        f.machine.setSpeed(1.012);

        advance(f, 1, .01);

        assertEquals(-.00026875, f.governor.getControlBoxOutput(), 1.0e-12);
        assertEquals(.6071993675, f.governor.getOutput(f.machine), 1.0e-12);
    }

    @Test
    void electricPowerDroopUsesItsOwnContinuousTransducer() throws Exception {
        Fixture electric = fixture(1, .1, 0, 0, 10, 0, 0, 0, 0, .1, .05);
        Fixture throttle = fixture(0, .1, 0, 0, 10, 0, 0, 0, 0, .1, .05);
        electric.machine.setPe(.8);
        throttle.machine.setPe(.8);

        advance(electric, 1, .01);
        advance(throttle, 1, .01);

        assertEquals(1.238, electric.governor.getMeasuredElectricalPower(), TOL);
        advance(electric, 1, .01);
        advance(throttle, 1, .01);
        assertTrue(electric.governor.getActuatorOutput() < 1.2);
        assertEquals(1.2, throttle.governor.getActuatorOutput(), TOL,
                "Throttle feedback must not respond to electrical-power changes");
    }

    @Test
    void engineTransportDelayIsTimeBasedAndPrecedesSpeedMultiplier() throws Exception {
        Fixture f = fixture(0, .05, 0, 0, 20, 0, 0, 0, .03, 0, 0);
        f.machine.setSpeed(1.05);

        advance(f, 3, .01);
        assertEquals(.63, f.governor.getOutput(f.machine), TOL,
                "The delayed initial actuator value is multiplied by current speed");
        advance(f, 1, .01);
        assertTrue(f.governor.getOutput(f.machine) < .63);
    }

    @Test
    void timeConstantsLimitsAndDeadbandFollowPowerWorldCorrections() throws Exception {
        Fixture f = fixture(0, .003, .007, 0, 10, .2, 0, .007, .2, .003, 0);
        f.governor.configureIntegrationStep(.01);
        f.governor.getData().setTmax(.5);
        f.governor.getData().setTmin(1.0);
        assertTrue(f.governor.initStates(f.machine.getDStabBus(), f.machine));

        assertEquals(0.0, f.governor.getEffectiveT1(), TOL);
        assertEquals(.01, f.governor.getEffectiveT2(), TOL);
        assertEquals(0.0, f.governor.getEffectiveT4(), TOL,
                "T4 is zeroed whenever T5 is zero");
        assertEquals(.01, f.governor.getEffectiveT6(), TOL);
        assertEquals(0.0, f.governor.getEffectiveTe(), TOL);
        assertEquals(.12, f.governor.getEffectiveTd(), TOL);
        assertEquals(1.2, f.governor.getEffectiveTmax(), TOL,
                "Initialization expands the normalized limits to contain Pmech");
        assertEquals(.5, f.governor.getEffectiveTmin(), TOL);
        assertEquals(0.0, f.governor.applySpeedDeadband(.001), TOL);
    }

    @Test
    void strictImportRejectsInvalidDroopSelector() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("degov1d-invalid.dyr");
        Files.writeString(dyr,
                "1 'DEGOV1D' '1' 2 .1 .2 .05 20 .03 .4 .1 .02 1 .1 .04 .05 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    private static Fixture fixture(int droopControl, double t1, double t2, double t3,
            double k, double t4, double t5, double t6, double td, double te,
            double droop) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        machine.setSpeed(1.0);
        PsseDegov1dGovernor governor = builder.addGovDegov1d("Bus1", "1",
                droopControl, t1, t2, t3, k, t4, t5, t6, td,
                2, 0, droop, te, .002, -.003, 50);
        assertNotNull(governor);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        return new Fixture(machine, governor);
    }

    private static void advance(Fixture f, int steps, double dt) {
        advance(f.governor, f.machine, steps, dt);
    }

    private static void advance(PsseDegov1dGovernor governor, Machine machine,
            int steps, double dt) {
        for (int i = 0; i < steps; i++) {
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 0));
            assertTrue(governor.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER,
                    machine, 1));
        }
    }

    private record Fixture(Machine machine, PsseDegov1dGovernor governor) { }
}
