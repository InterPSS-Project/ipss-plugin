package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.WindControlStack;
import org.interpss.dstab.renewable.Wtara1Data;
import org.interpss.dstab.renewable.Wtpta1Data;
import org.interpss.dstab.renewable.Wttqa1Data;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;

class PsseType3WindControllerTest extends CorePluginTestSetup {

    @Test
    void parserMapsAndWiresCompleteWindStackInSourceOrder(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("type3.dyr");
        Files.writeString(dyr,
                "1 'REGCA1' 1 1 .02 10 .9 .4 1.22 1.2 .9 .5 -1.3 .02 0 100 -100 .7 /\n"
                + "1 'REECA1' 1 0 0 1 1 0 0 .85 1.15 .02 0 0 5 1.1 -1.1 0 0 0 .5 .02 .436 -.436 1.1 .9 1.3 2.4 .6 1.5 0 .02 99 -99 1 0 1.3 .02 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 /\n"
                + "1 'WTPTA1' 1 28 137 22.03 1.17 .27 .3 27 0 10 -10 /\n"
                + "1 'WTTQA1' 1 1 1.9 .5 .04 60 1.002 0 .2 .58 .4 .72 .6 .86 .8 1 0 /\n"
                + "1 'WTARA1' 1 .007 0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        assertEquals(5, parser.getLastImportReport().totalRecordCount());

        Regca1Model converter = (Regca1Model) ((DStabGen) builder.getDStabNetwork()
                .getDStabBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        Reeca1Model electrical = converter.getReeca1Controller();
        WindControlStack stack = electrical.getWindControlStack();
        assertNotNull(stack);
        assertTrue(stack.isComplete());
        assertEquals(new Wtara1Data(.007, 0), stack.getAerodynamics().getData());
        assertEquals(new Wtpta1Data(28, 137, 22.03, 1.17, .27, .3, 27, 0, 10, -10),
                stack.getPitchController().getData());
        assertEquals(new Wttqa1Data(1, 1.9, .5, .04, 60, 1.002, 0,
                .2, .58, .4, .72, .6, .86, .8, 1, 0),
                stack.getTorqueController().getData());
    }

    @Test
    void noDriveTrainPathInitializesWithoutPowerReferenceJump() {
        WindControlStack stack = new WindControlStack();
        stack.setAerodynamics(new org.interpss.dstab.renewable.Wtara1Model(
                new Wtara1Data(.007, 0)));
        stack.setPitchController(new org.interpss.dstab.renewable.Wtpta1Model(
                new Wtpta1Data(25, 150, 30, 3, 0, .3, 27, 0, 10, -10)));
        stack.setTorqueController(new org.interpss.dstab.renewable.Wttqa1Model(
                new Wttqa1Data(0, 1, .5, 0, 60, 1.002, 0,
                        .2, .58, .4, .72, .6, .86, .8, 1, 0)));
        stack.initialize(.5);
        assertEquals(.5, stack.getPref(), 1.0e-12);
        assertEquals(.79, stack.getGeneratorSpeed(), 1.0e-12);
        for (int i = 0; i < 20; i++) stack.step(.005, .5, .5);
        assertEquals(.5, stack.getPref(), 1.0e-9);
        assertEquals(0, stack.getPitchController().getPitch(), 1.0e-9);
        assertEquals(.5, stack.getAerodynamics().getMechanicalPower(), 1.0e-9);
    }
}
