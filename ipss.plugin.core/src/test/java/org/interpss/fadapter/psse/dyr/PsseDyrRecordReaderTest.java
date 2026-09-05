package org.interpss.fadapter.psse.dyr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PsseDyrRecordReaderTest {
    @Test
    void readsMultilineCommaAndMultipleRecordsWithLocations() throws Exception {
        String dyr = "// ignored\n"
                + " 1001, 'GENROE', 'A 1', 3.0,\n"
                + " 4.0 / 1002 'HYGOV' 1 0.05 0.1 / // trailing comment\n"
                + "/ legacy comment\n";

        List<PsseDyrRecord> records = PsseDyrRecordReader.read(new StringReader(dyr), "memory.dyr");

        assertEquals(2, records.size());
        PsseDyrRecord first = records.get(0);
        assertEquals(1001, first.busNumber());
        assertEquals("GENROE", first.sourceModelName());
        assertEquals("GENROU", first.canonicalModelName());
        assertEquals("A 1", first.deviceId());
        assertEquals(2, first.parameterCount());
        assertEquals(2, first.startLine());
        assertEquals(3, first.endLine());
        assertEquals(3, records.get(1).startLine());
    }

    @Test
    void exposesUserModelNameInsteadOfWrapper() throws Exception {
        PsseDyrRecord record = PsseDyrRecordReader.read(new StringReader(
                "2001 'USRMDL' '1' 'REGFMA1' 1 2 3 /"), "user.dyr").get(0);
        assertEquals("REGFMA1", record.canonicalModelName());
        assertEquals(3, record.parameterCount());
    }

    @Test
    void rejectsUnterminatedRecordAndQuote() {
        assertThrows(IOException.class, () -> PsseDyrRecordReader.read(
                new StringReader("1 'GENROU' 1 2"), "bad.dyr"));
        assertThrows(IllegalArgumentException.class, () -> PsseDyrRecordReader.tokenize(
                "1 'GENROU 1 2"));
    }

    @Test
    void inventoryTracksCountsLengthsAndDistinctParameters() throws Exception {
        String dyr = "1 'GENROU' 1 1.0 2.0 /\n"
                + "2 'GENROE' 1 1.0 2.0 /\n"
                + "3 'GENROU' 1 1.0 3.0 /\n";
        PsseDyrInventory inventory = PsseDyrInventory.fromRecords(
                PsseDyrRecordReader.read(new StringReader(dyr), "inventory.dyr"));

        assertEquals(3, inventory.totalRecordCount());
        assertEquals(3, inventory.count("GENROU"));
        PsseDyrInventory.ModelStatistics stats = inventory.byModel().get("GENROU");
        assertEquals(Set.of(2), stats.parameterCounts());
        assertEquals(2, stats.distinctParameterSetCount());
    }
}
