package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.DynamicDevice;
import com.interpss.dstab.mach.Machine;

/** Exact PSS/E field-order tests for the legacy controllers used by Texas2k. */
public class PsseLegacyControllerMappingTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void mapsEveryIeeet1FieldAndAcceptsUnusedSwitch(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ieeet1.dyr");
        Files.writeString(dyr, "1 'IEEET1' '1' .01 101 .02 5 -4 .6 .7 .8 .9 7 3 .1 4 .2 1 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee1968Type1Exciter exciter = (Ieee1968Type1Exciter) machine.getExciter();

        assertNotNull(exciter);
        assertEquals(.01, exciter.getData().getTr(), TOL);
        assertEquals(101, exciter.getData().getKa(), TOL);
        assertEquals(.02, exciter.getData().getTa(), TOL);
        assertEquals(5, exciter.getData().getVrmax(), TOL);
        assertEquals(-4, exciter.getData().getVrmin(), TOL);
        assertEquals(.6, exciter.getData().getKe(), TOL);
        assertEquals(.7, exciter.getData().getTe(), TOL);
        assertEquals(.8, exciter.getData().getKf(), TOL);
        assertEquals(.9, exciter.getData().getTf(), TOL);
        assertEquals(3, exciter.getData().getE1(), TOL);
        assertEquals(.1, exciter.getData().getSeE1(), TOL);
        assertEquals(4, exciter.getData().getE2(), TOL);
        assertEquals(.2, exciter.getData().getSeE2(), TOL);
        assertEquals(1, exciter.getData().getSpdmlt(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        machine.setSpeed(1.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        double nominalSpeedOutput = exciter.getOutput(machine);
        machine.setSpeed(.98);
        assertEquals(.98 * nominalSpeedOutput, exciter.getOutput(machine), TOL);
    }

    @Test
    void mapsEveryExst1Field(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("exst1.dyr");
        Files.writeString(dyr, "1 'EXST1' '1' .01 .2 -.3 .4 .5 106 .07 8 -7 .12 .13 .14 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        IEEE1981ST1Exciter exciter = (IEEE1981ST1Exciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();

        assertNotNull(exciter);
        assertEquals(.01, exciter.getData().getTr(), TOL);
        assertEquals(106, exciter.getData().getKa(), TOL);
        assertEquals(.07, exciter.getData().getTa(), TOL);
        assertEquals(.4, exciter.getData().getTc(), TOL);
        assertEquals(.5, exciter.getData().getTb(), TOL);
        assertEquals(8, exciter.getData().getVrmax(), TOL);
        assertEquals(-7, exciter.getData().getVrmin(), TOL);
        assertEquals(.12, exciter.getData().getKc(), TOL);
        assertEquals(.13, exciter.getData().getKf(), TOL);
        assertEquals(.14, exciter.getData().getTf(), TOL);
        assertEquals(.2, exciter.getData().getVimax(), TOL);
        assertEquals(-.3, exciter.getData().getVimin(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void mapsEverySupportedIeeeg1Field(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ieeeg1.dyr");
        Files.writeString(dyr, "1 'IEEEG1' '1' 0 0 21 .11 .12 .13 .14 -.15 1.6 -.2 "
                + ".24 .31 0 .25 .33 0 .26 .35 0 .27 .37 0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        IeeeSteamTCDRGovernor governor = (IeeeSteamTCDRGovernor) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getGovernor();

        assertNotNull(governor);
        assertEquals(21, governor.getData().getK(), TOL);
        assertEquals(.11, governor.getData().getT1(), TOL);
        assertEquals(.12, governor.getData().getT2(), TOL);
        assertEquals(.13, governor.getData().getT3(), TOL);
        assertEquals(.14, governor.getData().getPup(), TOL);
        assertEquals(-.15, governor.getData().getPdown(), TOL);
        assertEquals(1.6, governor.getData().getPmax(), TOL);
        assertEquals(-.2, governor.getData().getPmin(), TOL);
        assertEquals(.24, governor.getData().getTch(), TOL);
        assertEquals(.31, governor.getData().getFvhp(), TOL);
        assertEquals(.25, governor.getData().getTrh1(), TOL);
        assertEquals(.33, governor.getData().getFhp(), TOL);
        assertEquals(.26, governor.getData().getTrh2(), TOL);
        assertEquals(.35, governor.getData().getFip(), TOL);
        assertEquals(.27, governor.getData().getTco(), TOL);
        assertEquals(.37, governor.getData().getFlp(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void texasExst1ZeroTransducerProfileInitializesWithoutDrift(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("texas-exst1.dyr");
        Files.writeString(dyr,
                "1 'EXST1' 1 0 .1 -.1 1 8.7 217 .05 5 -5 .12 0 1 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = initializeMachine(builder);
        IEEE1981ST1Exciter exciter = (IEEE1981ST1Exciter) machine.getExciter();

        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        double initial = exciter.getOutput(machine);
        step(exciter, machine, 20);
        assertEquals(initial, exciter.getOutput(machine), 1.0e-8);
    }

    @Test
    void exst1UsesPtiVoltageDependentFieldLimits(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("exst1-limits.dyr");
        Files.writeString(dyr,
                "1 'EXST1' 1 0 .8 -.8 1 1 80 .04 8 -8 .12 .1 1 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = initializeMachine(builder);
        IEEE1981ST1Exciter exciter = (IEEE1981ST1Exciter) machine.getExciter();
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        machine.getDStabBus().setVoltage(new Complex(.8, 0.0));
        exciter.vrmax = .5;
        exciter.vrmin = -.5;
        exciter.kc = .1;
        double expectedUpper = .8 * .5
                - .1 * machine.calculateIfd(com.interpss.dstab.mach.MachineIfdBase.EXCITER);
        assertEquals(expectedUpper, exciter.getFieldVoltageUpperLimit(), TOL);
        assertEquals(-.8 * .5, exciter.getFieldVoltageLowerLimit(), TOL);
        assertEquals(expectedUpper, exciter.getOutput(machine), TOL,
                "EXST1 output must enforce the voltage/current-dependent upper limit");

        exciter.vrmax = 3.0;
		exciter.vrmin = 2.5;
        exciter.kc = 0.0;
		assertEquals(.8 * 2.5, exciter.getOutput(machine), TOL,
                "EXST1 output must enforce the voltage-dependent lower limit");
    }

    @Test
    void texasIeeet1ZeroKeProfileInitializesWithoutDrift(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("texas-ieeet1.dyr");
        Files.writeString(dyr,
                "1 'IEEET1' 1 0 55.59 .06 1 -1 0 .76 .06 .94 0 2.85 3.27 .07 .24 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = initializeMachine(builder);
        Ieee1968Type1Exciter exciter = (Ieee1968Type1Exciter) machine.getExciter();

        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        double initial = exciter.getOutput(machine);
        step(exciter, machine, 20);
        assertEquals(initial, exciter.getOutput(machine), 1.0e-8);
    }

    @Test
    void texasIeeeg1SingleMachineProfileInitializesWithoutDrift(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("texas-ieeeg1.dyr");
        Files.writeString(dyr,
                "1 'IEEEG1' 1 0 0 21 1 1 .16 1 -10 1 0 .07 .39 0 4.86 .38 0 "
                        + ".35 .23 0 1 0 0 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = initializeMachine(builder);
        IeeeSteamTCDRGovernor governor = (IeeeSteamTCDRGovernor) machine.getGovernor();

        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        double initial = governor.getOutput(machine);
        step(governor, machine, 20);
        assertEquals(initial, governor.getOutput(machine), 1.0e-8);
    }

    private static Machine initializeMachine(DStabNetworkBuilder builder) throws Exception {
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(builder.getDStabNetwork());
        assertTrue(loadflow.loadflow());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(machine.initStates(machine.getDStabBus()));
        return machine;
    }

    private static void step(DynamicDevice device, Machine machine, int count) {
        for (int i = 0; i < count; i++) {
            assertTrue(device.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(device.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
    }
}
