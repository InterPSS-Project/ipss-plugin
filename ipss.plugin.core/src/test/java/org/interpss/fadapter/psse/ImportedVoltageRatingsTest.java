package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.interpss.fadapter.psse.export.PSSERawExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.aclf.AclfEmergencyVoltageLimits;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.snapshot.BaseAclfNetworkSnapshotFactory;

public class ImportedVoltageRatingsTest {
    @TempDir Path temporary;

    @Test public void positionalRawKeepsDistinctRatingsAndLeavesControlVoltageLimitsAlone() throws Exception {
        String buses = """
                1,'SWING',230,3,1,1,1,1.01,0.0,1.05,0.95,1.10,0.90
                2,'NORMAL',230,1,1,1,1,1.00,0.0,1.04,0.96,1.10,
                3,'INVALID',230,1,1,1,1,1.00,0.0,0.94,0.96,1.10,0.0
                4,'MISSING',230,1,1,1,1,1.01,0.0
                """;
        AclfNetwork net = parseRaw(33, buses);
        assertBands(net);
        var replay = BaseAclfNetworkSnapshotFactory.createReplayCopy(net);
        var ratings = (AclfEmergencyVoltageLimits) replay.getExtraInfo().get(AclfEmergencyVoltageLimits.NETWORK_EXTRA_INFO_KEY);
        assertEquals(new PsseBusVoltageRatings.VoltageBand(0.90, 1.10), ratings.forBus("Bus1"));
        assertEquals(net.getBus("Bus4").getVLimit().getMin(), net.getBus("Bus1").getVLimit().getMin(), 0.0);
        assertEquals(net.getBus("Bus4").getVLimit().getMax(), net.getBus("Bus1").getVLimit().getMax(), 0.0);
    }

    @Test public void namedRawxRatingsAreIndependentOfColumnOrderAndMissingBounds() throws Exception {
        Path input = temporary.resolve("voltage-ratings.rawx");
        Files.writeString(input, """
                {"network": {"bus": {
                  "fields": ["ibus", "name", "baskv", "ide", "NVLO", "EVHI", "NVHI", "evlo"],
                  "data": [[1,"SWING",230,3,0.95,1.10,1.05,0.90],
                           [2,"NORMAL",230,1,0.96,1.10,1.04,null],
                           [3,"INVALID",230,1,0.96,1.10,0.94,0.0],
                           [4,"MISSING",230,1]]
                }}}
                """);
        assertBands(new PSSEJsonDirectParser().parse(input.toString()));
    }

    @Test public void exportAndReimportPreservesDistinctExplicitBands() throws Exception {
        AclfNetwork original = parseRaw(33,
                "1,'SWING',230,3,1,1,1,1.01,0.0,1.05,0.95,1.12,0.88\n");
        var expected = PsseBusVoltageRatings.find(original, "Bus1").orElseThrow();

        Path raw = temporary.resolve("round-trip.raw");
        new PSSERawExporter(original, 33).export(raw);
        assertEquals(expected, PsseBusVoltageRatings.find(new PSSEDirectParser(33).parse(raw.toString()), "Bus1")
                .orElseThrow());

        Path rawx = temporary.resolve("round-trip.rawx");
        new PSSEJsonExporter(original).export(rawx);
        assertEquals(expected, PsseBusVoltageRatings.find(new PSSEJsonDirectParser().parse(rawx.toString()), "Bus1")
                .orElseThrow());
    }

    @Test public void earlierRawLayoutsDoNotCreateVoltageRatings() throws Exception {
        AclfNetwork net = parseRaw(31, "1,'SWING',230,3,1,1,1,1.01,0.0\n");
        assertTrue(PsseBusVoltageRatings.find(net, "Bus1").isEmpty());
    }

    private static AclfNetwork parseRaw(int version, String buses) throws Exception {
        var reader = new BufferedReader(new StringReader("0,100.0," + version + ",0,0,60.0\n"
                + "SMALL VOLTAGE RATING FIXTURE\nSECOND COMMENT\n" + buses + "0 / END OF BUS DATA\n"));
        return new PSSEDirectParser(version).parseFromReader(reader);
    }

    private static void assertBands(AclfNetwork net) {
        var bus = PsseBusVoltageRatings.find(net, "Bus1").orElseThrow();
        assertEquals(new PsseBusVoltageRatings.VoltageBand(0.95, 1.05), bus.normal());
        assertEquals(new PsseBusVoltageRatings.VoltageBand(0.90, 1.10), bus.emergency());
        var contingencyLimits = (AclfEmergencyVoltageLimits) net.getExtraInfo()
                .get(AclfEmergencyVoltageLimits.NETWORK_EXTRA_INFO_KEY);
        assertEquals(bus.emergency(), contingencyLimits.forBus("Bus1"));
        assertNull(contingencyLimits.forBus("Bus2"));
        assertNull(contingencyLimits.forBus("Bus3"));
        assertNull(contingencyLimits.forBus("Bus4"));
        var noEmergency = PsseBusVoltageRatings.find(net, "Bus2").orElseThrow();
        assertEquals(new PsseBusVoltageRatings.VoltageBand(0.96, 1.04), noEmergency.normal());
        assertNull(noEmergency.emergency());
        assertTrue(PsseBusVoltageRatings.find(net, "Bus3").isEmpty());
        assertTrue(PsseBusVoltageRatings.find(net, "Bus4").isEmpty());
    }
}
