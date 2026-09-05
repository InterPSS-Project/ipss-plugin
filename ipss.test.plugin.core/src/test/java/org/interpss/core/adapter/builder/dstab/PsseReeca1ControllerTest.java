package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.interpss.dstab.DStabGen;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PsseReeca1ControllerTest extends CorePluginTestSetup {

    @Test
    void directParserMapsCompleteFiftyOneParameterRecord(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("reeca1.dyr");
        Files.writeString(dyr,
                "1 'REGCA1' 1 1 .02 10 .9 .4 1.22 1.2 .9 .5 -1.3 .02 0 100 -100 .7 /\n"
                + "1 'REECA1' 1 0 1 0 1 0 1 .7 1.3 .01 -.02 .03 2 .9 -.8 1.01 "
                + ".1 .2 .3 .04 .7 -.6 .5 -.4 1.1 1.2 1.3 1.4 .05 .06 2 -2 1 0 1.2 .07 "
                + ".1 .2 .3 .4 .5 .6 .7 .8 .11 .21 .31 .41 .51 .61 .71 .81 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Regca1Model converter = (Regca1Model) ((DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1")).getDynamicGenDevice();
        Reeca1Model controller = converter.getReeca1Controller();
        assertNotNull(controller);
        assertSame(controller, converter.getActiveElectricalController());
        assertEquals(expectedData(), controller.getData());
    }

    @Test
    void allTexasFlagProfilesInitializeWithoutCommandDiscontinuity() {
        int[][] profiles = {{1, 1, 1}, {1, 1, 0}, {0, 0, 0}};
        for (int[] profile : profiles) {
            Reeca1Model controller = new Reeca1Model(texasData(profile[0], profile[1], profile[2]), null);
            controller.initialize(.8, .2, 1.0);
            assertEquals(.8, controller.getIpcmd(), 1.0e-12);
            assertEquals(-.2, controller.getIqcmd(), 1.0e-12);
            for (int i = 0; i < 20; i++) controller.step(.005, .8, .2, 1.0, 1.0);
            assertEquals(.8, controller.getIpcmd(), 1.0e-9);
            assertEquals(-.2, controller.getIqcmd(), 1.0e-9);
        }
    }

    @Test
    void voltageDipInjectionHonorsQPriorityCircularCurrentLimit() {
        Reeca1Model controller = new Reeca1Model(texasData(1, 1, 1), null);
        controller.initialize(1.0, 0.0, 1.0);
        for (int i = 0; i < 40; i++) controller.step(.005, 1.0, 0.0, .5, 1.0);
        assertTrue(Math.abs(controller.getIqcmd()) > .1);
        assertTrue(Math.hypot(controller.getIpcmd(), controller.getIqcmd()) <= 1.3 + 1.0e-9);
    }

    private static Reeca1Data expectedData() {
        return new Reeca1Data(0, 1, 0, 1, 0, 1,
                .7, 1.3, .01, -.02, .03, 2, .9, -.8, 1.01, .1, .2, .3,
                .04, .7, -.6, .5, -.4, 1.1, 1.2, 1.3, 1.4, .05, .06,
                2, -2, 1, 0, 1.2, .07,
                .1, .2, .3, .4, .5, .6, .7, .8,
                .11, .21, .31, .41, .51, .61, .71, .81);
    }

    private static Reeca1Data texasData(int vFlag, int qFlag, int pFlag) {
        return new Reeca1Data(0, 0, vFlag, qFlag, pFlag, 0,
                .85, 1.15, .02, 0, 0, 5, 1.1, -1.1, 0, 0, 0, .5,
                .02, .436, -.436, 1.1, .9, 1.2, 1.9, 1, 1.1, 0, .02,
                99, -99, 1, 0, 1.3, .02,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0);
    }
}
