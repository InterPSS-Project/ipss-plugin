package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernor;
import org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernorData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

public class DStabNetworkBuilderIeeeg3Test extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir
    Path tempDir;

    @Test
    void importsAllFourteenPsseFieldsAndMaintainsEquilibrium() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ieeeg3.dyr");
        Files.writeString(dyr,
                "1 'IEEEG3' '1' .52 .1 .1 -.1 1 0 .058 .4 8 1.4 .2 1 .6 1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setPm(.6);
        machine.setPe(.6);
        Ieee1981Type3HydroGovernor governor =
                (Ieee1981Type3HydroGovernor) machine.getGovernor();

        assertNotNull(governor);
        assertEquals("IEEEG3", governor.getName());
        assertEquals("PSS/E", governor.getCategory());
        Ieee1981Type3HydroGovernorData data = governor.getData();
        assertEquals(.52, data.getTg(), TOL);
        assertEquals(.1, data.getTp(), TOL);
        assertEquals(.1, data.getVelOpen(), TOL);
        assertEquals(-.1, data.getVelClose(), TOL);
        assertEquals(1, data.getPmax(), TOL);
        assertEquals(0, data.getPmin(), TOL);
        assertEquals(.058, data.getSigma(), TOL);
        assertEquals(.4, data.getDelta(), TOL);
        assertEquals(8, data.getTr(), TOL);
        assertEquals(1.4, data.getTw(), TOL);
        assertEquals(.2, data.getA11(), TOL);
        assertEquals(1, data.getA13(), TOL);
        assertEquals(.6, data.getA21(), TOL);
        assertEquals(1, data.getA23(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));

        for (int i = 0; i < 400; i++) {
            assertTrue(governor.nextStep(.0025, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(governor.nextStep(.0025, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertEquals(.6, governor.getGatePosition(), 1.0e-10);
        assertEquals(.6, governor.getMechanicalPower(), 1.0e-10);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void appliesPowerWorldTimeRateAndInitializationCorrections() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        Ieee1981Type3HydroGovernor governor = builder.addGovIeeeg3("Bus1", "1",
                .001, .001, -.1, .2, .3, .1, .05, .4, .001, .001,
                .2, 1, .6, 1);
        governor.configureIntegrationStep(.01);

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(.01, governor.tg, TOL);
        assertEquals(0, governor.tp, TOL);
        assertEquals(.01, governor.tr, TOL);
        assertEquals(.01, governor.tw, TOL);
        assertEquals(.2, governor.velOpen, TOL);
        assertEquals(-.1, governor.velClose, TOL);
        assertEquals(.6, governor.pmax, TOL);
        assertEquals(.1, governor.pmin, TOL);
        assertEquals(machine.getPm(), governor.getMechanicalPower(), TOL);
    }

    @Test
    void matchesTheDocumentedPermanentDroopSteadyStateAfterOverspeed() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setPm(.6);
        machine.setPe(.6);
        Ieee1981Type3HydroGovernor governor = builder.addGovIeeeg3("Bus1", "1",
                .1, .05, .1, -.1, 2, 0, .05, .05, 1, 1,
                1, 0, 0, 1);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));

        machine.setSpeed(1.01);
        for (int i = 0; i < 200; i++) {
            assertTrue(governor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(governor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        String response = "servo=" + governor.getServoPosition()
                + ", gate=" + governor.getGatePosition()
                + ", pm=" + governor.getMechanicalPower();
        assertTrue(governor.getGatePosition() < .6, response);
        assertTrue(governor.getMechanicalPower() < .6, response);

        // At steady state the transient-droop washout is zero and
        // Rperm * gate = Pref - speedDeviation = .03 - .01.
        for (int i = 0; i < 30_000; i++) {
            assertTrue(governor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(governor.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertEquals(.4, governor.getGatePosition(), 2.0e-4);
        assertEquals(.4, governor.getMechanicalPower(), 2.0e-4);
        assertEquals(governor.getMechanicalPower(), governor.getOutput(machine), TOL);
    }
}
