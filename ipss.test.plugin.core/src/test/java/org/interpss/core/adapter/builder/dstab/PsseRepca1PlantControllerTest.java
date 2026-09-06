package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PsseRepca1PlantControllerTest extends CorePluginTestSetup {

    @Test
    void directParserRetainsAllParametersAndAttachesToReeca1(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regca1Model converter = addRenewableChain(builder);
        Path dyr = tempDir.resolve("repca1.dyr");
        Files.writeString(dyr,
                "1 'REPCA1' 1 2 3 4 'X' 1 0 1 .01 .02 .03 .04 .05 .06 .07 .08 .09 "
                + "2 -2 -.1 .1 1 -1 .11 .12 .13 -.01 .01 .2 -.2 3 -3 .14 4 5 1 /\n");

        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

        Reeca1Model electrical = converter.getReeca1Controller();
        Repca1Model plant = electrical.getPlantController();
        assertNotNull(plant);
        assertSame(plant, converter.getActiveElectricalController().getPlantController());
        assertEquals(expectedData(), plant.getData());
    }

    @Test
    void optionalPuFlagDefaultsToSystemBase(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regca1Model converter = addRenewableChain(builder);
        Path dyr = tempDir.resolve("repca1-default-puflag.dyr");
        Files.writeString(dyr,
                "1 'REPCA1' 1 2 3 4 'X' 1 0 1 .01 .02 .03 .04 .05 .06 .07 .08 .09 "
                + "2 -2 -.1 .1 1 -1 .11 .12 .13 -.01 .01 .2 -.2 3 -3 .14 4 5 /\n");

        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

        assertEquals(0, converter.getReeca1Controller().getPlantController().getData().puFlag());
    }

    @Test
    void rejectsInvalidControlFlags() {
        Repca1Data valid = expectedData();
        assertThrows(IllegalArgumentException.class, () -> copyWithFlags(valid, 2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> copyWithFlags(valid, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> copyWithFlags(valid, 1, 0, 3));
    }

    private static Regca1Model addRenewableChain(DStabNetworkBuilder builder) {
        Regca1Model converter = builder.addRegca1("Bus1", "1",
                new Regca1Data(0, .02, 10, .9, .4, 1.22, 1.2, .8,
                        .4, -1.3, .02, .7, 0, 0, .8));
        builder.addReeca1("Bus1", "1", new Reeca1Data(0, 0, 0, 0, 0, 0,
                .8, 1.2, 0, 0, 0, 0, 1, -1, 1, 0, 0, 0,
                0, 1, -1, 1, -1, 0, 0, 0, 0, 0, 0,
                1, -1, 2, -2, 10, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0));
        return converter;
    }

    private static Repca1Data expectedData() {
        return new Repca1Data(2, 3, 4, "X", 1, 0, 1,
                .01, .02, .03, .04, .05, .06, .07, .08, .09,
                2, -2, -.1, .1, 1, -1, .11, .12, .13,
                -.01, .01, .2, -.2, 3, -3, .14, 4, 5, 1);
    }

    private static Repca1Data copyWithFlags(Repca1Data d, int vc, int ref, int freq) {
        return new Repca1Data(d.remoteBus(), d.branchFromBus(), d.branchToBus(), d.branchId(),
                vc, ref, freq, d.tfltr(), d.kp(), d.ki(), d.tft(), d.tfv(), d.vfrz(),
                d.rc(), d.xc(), d.kc(), d.emax(), d.emin(), d.dbd1(), d.dbd2(),
                d.qmax(), d.qmin(), d.kpg(), d.kig(), d.tp(), d.fdbd1(), d.fdbd2(),
                d.femax(), d.femin(), d.pmax(), d.pmin(), d.tg(), d.ddn(), d.dup(),
                d.puFlag());
    }
}
