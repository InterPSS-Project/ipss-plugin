package org.interpss.core.adapter.psse.raw.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseModelRemoveIdvProcessor;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;

public class PsseModelRemoveIdvProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void typeOneRemovesTheCompleteDynamicStackWhileOtherSelectorsRemainScoped()
            throws Exception {
        var network = oneGeneratorNetwork();
        Path idv = tempDir.resolve("case_MODREMOVE.idv");
        Files.writeString(idv, "BAT_PLMOD_REMOVE 1 1 3\n"
                + "BAT_PLMOD_REMOVE 1 '1' 8 // duplicate target, different slot\n"
                + "BAT_PLMOD_REMOVE 1 \"1\" 1\n"
                + "BAT_PLMOD_REMOVE 1 1 1\n");

        var result = PsseModelRemoveIdvProcessor.apply(network, idv.toString());

        assertEquals(3, result.directives().size());
        assertEquals(1, result.removedGeneratorKeys().size());
        assertEquals("Bus1", result.removedGeneratorKeys().iterator().next().busId());
        assertEquals("1", result.removedGeneratorKeys().iterator().next().generatorId());
    }

    @Test
    void removedStackRecordsAreAuditableStrictModeSkips() throws Exception {
        var network = oneGeneratorNetwork();
        Path idv = tempDir.resolve("case_MODREMOVE.idv");
        Files.writeString(idv, "BAT_PLMOD_REMOVE 1 1 1\n");
        var result = PsseModelRemoveIdvProcessor.apply(network, idv.toString());
        Path dyr = tempDir.resolve("case.dyr");
        Files.writeString(dyr,
                "1 'REECA1' 1 0 0 0 0 0 0 .85 1.15 .02 0 0 5 1.1 -1.1 "
                        + "0 0 0 .5 0 .436 -.436 1.1 .9 0 .1 18 5 0 .02 99 -99 "
                        + "1 0 1.3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 /\n"
                        + "1 'REPCA1' 1 0 0 0 0 1 1 0 .05 10 10 0 3 .7 0 0 1 1 "
                        + "-1 0 0 1 -1 10 10 0 0 0 1 -1 2 0 3 20 0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(
                new DStabNetworkBuilder(network)).setStrictImport(true)
                        .setModelRemovedGenerators(result.removedGeneratorKeys());
        parser.parseDynFile(dyr.toString());

        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(2, parser.getLastImportReport().count(
                DynamicModelImportStatus.SKIPPED_MODEL_REMOVE));
        assertTrue(parser.getLastImportReport().failures().isEmpty());
        assertTrue(parser.getLastImportReport().failureSummary()
                .contains("2 intentionally skipped by BAT_PLMOD_REMOVE"));
        assertEquals("dynamic stack intentionally removed by BAT_PLMOD_REMOVE type 1",
                parser.getLastImportReport().entries().get(0).message());
    }

    @Test
    void multiFileLoaderAutoDiscoversSiblingModelRemovalBeforeDyrAttachment()
            throws Exception {
        Path raw = tempDir.resolve("case.raw");
        Files.copy(Path.of("testData", "adpter", "psse", "v33", "SMIB", "SMIB_v33.raw"),
                raw);
        Path dyr = tempDir.resolve("model.dyr");
        Files.writeString(dyr,
                "1 'GENROU' 1 6.0 .033 .54 .078 6.4 0 .8958 .8645 .1189 .1969 "
                        + ".089 .0521 0 0 /\n");
        Files.writeString(tempDir.resolve("model_MODREMOVE.idv"),
                "BAT_PLMOD_REMOVE 1 1 1\n");

        var network = new PSSEMultiFileLoader().loadDStab(raw.toString(), dyr.toString())
                .getDStabilityNet();
        DStabGen generator = (DStabGen) network.getDStabBus("Bus1")
                .getContributeGen("1");

        assertNull(generator.getMach());
        assertNull(generator.getDynamicGenDevice());
        assertNull(network.getMachine("Bus1-mach1"));
    }

    @Test
    void reportsMissingGeneratorAndMalformedCommand() throws Exception {
        var network = oneGeneratorNetwork();
        Path missing = tempDir.resolve("missing_MODREMOVE.idv");
        Files.writeString(missing, "BAT_PLMOD_REMOVE 1 2 1\n");
        assertThrows(InterpssException.class,
                () -> PsseModelRemoveIdvProcessor.apply(network, missing.toString()));

        Path malformed = tempDir.resolve("malformed_MODREMOVE.idv");
        Files.writeString(malformed, "BAT_PLMOD_REMOVE 1 1\n");
        assertThrows(InterpssException.class,
                () -> PsseModelRemoveIdvProcessor.apply(network, malformed.toString()));
    }

    private static com.interpss.dstab.DStabilityNetwork oneGeneratorNetwork()
            throws InterpssException {
        var network = DStabObjectFactory.createDStabilityNetwork();
        AclfNetworkBuilder builder = new AclfNetworkBuilder(network);
        builder.setNetworkInfo("model-remove-test", "model-remove-test", 100000.0,
                OriginalDataFormat.PSSE);
        builder.addBus("Bus1", "generator bus", 1L, 138000.0,
                1.02, 0.0, null, null, null);
        builder.setPVBus("Bus1", .8, 1.02, .5, -.5, true);
        builder.addContributeGen("Bus1", "1", true, .8, .1, 100.0, 1.02,
                .5, -.5, 1.0, 0.0, null, null, 0.0, null, 0.0, 0.0);
        return network;
    }
}
