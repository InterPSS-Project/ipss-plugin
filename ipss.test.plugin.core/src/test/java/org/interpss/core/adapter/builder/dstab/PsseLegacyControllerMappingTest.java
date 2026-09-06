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
}
