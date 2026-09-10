package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.mach.Machine;

class DStabNetworkBuilderIeeeg1dTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir
    Path tempDir;

    @Test
    void loadsSingleShaftAliasCombinesPowerFractionsAndUsesTurbineBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("ieeeg1sdu.dyr");
        Files.writeString(dyr,
                "1 'IEEEG1SDU' '1' 20 .1 0 .2 .1 -.1 1 0 .2 .2 .1 5 .2 .1 5 "
                        + ".1 .05 5 .03 .02 .002 -.003 50 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0); machine.setPm(0.4); machine.setPe(0.4);
        IeeeSteamTCDRGovernor governor = (IeeeSteamTCDRGovernor) machine.getGovernor();

        assertNotNull(governor);
        assertEquals("IEEEG1D", governor.getName());
        assertEquals(.3, governor.getData().getFvhp(), TOL);
        assertEquals(.3, governor.getData().getFhp(), TOL);
        assertEquals(.15, governor.getData().getFip(), TOL);
        assertEquals(.05, governor.getData().getFlp(), TOL);
        assertEquals(.002, governor.getData().getDbH(), TOL);
        assertEquals(-.003, governor.getData().getDbL(), TOL);
        assertEquals(50.0, governor.getData().getTrate(), TOL);
        assertTrue(governor.initStates(machine.getDStabBus(), machine));
        assertEquals(.5, governor.ratingScale, TOL);
        assertEquals(machine.getPm(), governor.getOutput(machine), TOL);
        assertEquals(6, governor.getNamedStates().size());
        assertEquals(governor.getGovernorSignal(), governor.getNamedState("filterBlock"), TOL);
        assertEquals(governor.getValvePosition(), governor.getNamedState("intBlock"), TOL);
        assertEquals(governor.getFirstStageOutput(), governor.getNamedState("chDelayBlock"), TOL);
        assertEquals(governor.getSecondStageOutput(), governor.getNamedState("rh1DelayBlock"), TOL);
        assertEquals(governor.getThirdStageOutput(), governor.getNamedState("rh2DelayBlock"), TOL);
        assertEquals(governor.getFourthStageOutput(), governor.getNamedState("coDelayBlock"), TOL);
        governor.speedDeadbandBlock.eulerStep1(-.002, 0.0);
        assertEquals(0.0, governor.speedDeadbandBlock.getY(), TOL);
        governor.speedDeadbandBlock.eulerStep1(-.010, 0.0);
        assertEquals(-.007, governor.speedDeadbandBlock.getY(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }
}
