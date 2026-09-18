package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.mach.Machine;

class DStabNetworkBuilderIeesgodTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir Path tempDir;

    @Test
    void loadsAliasNormalizesLimitsAndInitializesOnTurbineBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ieesgodu.dyr");
        Files.writeString(dyr,
                "1 'IEESGODU' '1' .1 0 .2 .3 5 5 20 .3 .5 0 1 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(.4); machine.setPe(.4);
        PsseIEESGOSteamTurGovernor governor =
                (PsseIEESGOSteamTurGovernor) machine.getGovernor();

        assertNotNull(governor);
        assertEquals("IEESGOD", governor.getName());
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertEquals(50.0, governor.getData().getTrate(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(.5, governor.ratingScale, TOL);
        assertEquals(machine.getPm(), governor.getOutput(machine), TOL);
        governor.speedDeadbandBlock.eulerStep1(-.002, 0.0);
        assertEquals(0.0, governor.speedDeadbandBlock.getY(), TOL);
        governor.speedDeadbandBlock.eulerStep1(-.010, 0.0);
        assertEquals(-.007, governor.speedDeadbandBlock.getY(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }
}
