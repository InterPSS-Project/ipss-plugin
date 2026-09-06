package org.interpss.core.adapter.psse.raw.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.DStabObjectFactory;

public class PsseGnetIdvProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void convertsEveryActiveGeneratorToAnEquivalentNegativeLoad() throws Exception {
        var network = DStabObjectFactory.createDStabilityNetwork();
        AclfNetworkBuilder builder = new AclfNetworkBuilder(network);
        builder.setNetworkInfo("gnet-test", "gnet-test", 100000.0, OriginalDataFormat.PSSE);
        builder.addBus("Bus1090", "GNET bus", 1090L, 138000.0,
                1.02, 0.0, null, null, null);
        builder.setPVBus("Bus1090", .8, 1.02, .5, -.5, true);
        builder.addContributeGen("Bus1090", "1", true, .6, .15, 100.0, 1.02,
                .5, -.5, 1.0, 0.0, null, null, 0.0, null, 0.0, 0.0);
        builder.addContributeGen("Bus1090", "2", true, .2, -.05, 100.0, 1.02,
                .5, -.5, 1.0, 0.0, null, null, 0.0, null, 0.0, 0.0);
        builder.addContributeGen("Bus1090", "OFF", false, .1, .02, 100.0, 1.02,
                .5, -.5, 1.0, 0.0, null, null, 0.0, null, 0.0, 0.0);
        builder.addContributeLoad("Bus1090", "1", true, new Complex(.1, .03),
                null, null, null, false);

        Path idv = tempDir.resolve("case_gnet.idv");
        Files.writeString(idv, "GNET\n  1090\n  1090 // duplicate\n0\n");
        PsseGnetIdvProcessor.Result result =
                PsseGnetIdvProcessor.apply(network, idv.toString());

        var bus = network.getBus("Bus1090");
        assertEquals(1, result.requestedBuses());
        assertEquals(1, result.convertedBuses());
        assertEquals(2, result.convertedGenerators());
        assertEquals(2, result.convertedGeneratorKeys().size());
        assertFalse(bus.getContributeGen("1").isActive());
        assertFalse(bus.getContributeGen("2").isActive());
        assertFalse(bus.getContributeGen("OFF").isActive());
        assertEquals(AclfGenCode.NON_GEN, bus.getGenCode());
        assertEquals(new Complex(-.6, -.15), bus.getContributeLoad("GNET-1").getLoadCP());
        assertEquals(new Complex(-.2, .05), bus.getContributeLoad("GNET-2").getLoadCP());
        assertEquals(new Complex(.1, .03), bus.getContributeLoad("1").getLoadCP());
    }

    @Test
    void dynamicRecordsForConvertedGeneratorsAreAuditableStrictModeSkips() throws Exception {
        var network = DStabObjectFactory.createDStabilityNetwork();
        AclfNetworkBuilder builder = new AclfNetworkBuilder(network);
        builder.setNetworkInfo("gnet-report", "gnet-report", 100000.0,
                OriginalDataFormat.PSSE);
        builder.addBus("Bus1090", "GNET bus", 1090L, 138000.0,
                1.02, 0.0, null, null, null);
        builder.setPVBus("Bus1090", .8, 1.02, .5, -.5, true);
        builder.addContributeGen("Bus1090", "1", true, .8, .1, 100.0, 1.02,
                .5, -.5, 1.0, 0.0, null, null, 0.0, null, 0.0, 0.0);
        Path idv = tempDir.resolve("report_gnet.idv");
        Files.writeString(idv, "GNET\n1090\n0\n");
        var result = PsseGnetIdvProcessor.apply(network, idv.toString());
        Path dyr = tempDir.resolve("removed.dyr");
        Files.writeString(dyr,
                "1090 'GENROU' 1 6.5 .05 .4 .05 1.8 1.7 .3 .55 .25 .2 0 0 /\n"
                + "1090 'IEEET1' 1 .06 40 1 -.06 1 1 -.04 5 -5 .2 1 .03 0 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(
                new DStabNetworkBuilder(network)).setStrictImport(true)
                        .setGnetRemovedGenerators(result.convertedGeneratorKeys());
        parser.parseDynFile(dyr.toString());

        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(2, parser.getLastImportReport().count(
                DynamicModelImportStatus.SKIPPED_GNET));
        assertTrue(parser.getLastImportReport().failures().isEmpty());
        assertEquals("generator intentionally removed by GNET preprocessing",
                parser.getLastImportReport().entries().get(0).message());
    }

    @Test
    void reportsAnIdvBusThatDoesNotExistInTheRawNetwork() throws Exception {
        var network = DStabObjectFactory.createDStabilityNetwork();
        Path idv = tempDir.resolve("missing_gnet.idv");
        Files.writeString(idv, "GNET\n9999\n0\n");

        assertThrows(InterpssException.class,
                () -> PsseGnetIdvProcessor.apply(network, idv.toString()));
    }
}
