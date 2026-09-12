package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;

public class PsseDyrRecordReaderTest {

    @Test
    void tokenizesMultilineQuotedCommaAndScientificNotationRecords() throws Exception {
        String dyr = "// header comment\n"
                + "-101, 'GENROU', 'A B', 8.0e0,\n"
                + " 3.0E-2 / // trailing comment\n"
                + "102 'GENCLS' 'A//B' 4.0 0.0 /\n";

        List<PsseDyrRecord> records = PsseDyrRecordReader.read(
                new StringReader(dyr), "memory.dyr");

        assertEquals(2, records.size());
        PsseDyrRecord first = records.get(0);
        assertEquals(101, first.busNumber());
        assertEquals("A B", first.deviceId());
        assertEquals(2, first.startLine());
        assertEquals(3, first.endLine());
        assertEquals(8.0, first.doubleParameter(0));
        assertEquals(0.03, first.doubleParameter(1));
        assertEquals(9.0, first.optionalDoubleParameter(2, 9.0));
        assertEquals("A//B", records.get(1).deviceId());
    }

    @Test
    void typedAccessorsRejectMalformedAndMissingParametersAtTheSourceLocation()
            throws Exception {
        PsseDyrRecord record = PsseDyrRecordReader.read(
                new StringReader("1 'GENCLS' '1' bad 2.5 /"), "bad.dyr").get(0);

        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> record.doubleParameter(0));
        assertTrue(malformed.getMessage().contains("GENCLS parameter 1"));
        assertTrue(malformed.getMessage().contains("bad.dyr:1"));
        assertThrows(IllegalArgumentException.class, () -> record.intParameter(1));
        assertThrows(IllegalArgumentException.class, () -> record.parameter(2));
        assertEquals(7, record.optionalIntParameter(2, 7));
    }

    @Test
    void rejectsMalformedBusQuoteAndUnterminatedRecord() {
        assertThrows(IllegalArgumentException.class, () -> PsseDyrRecordReader.read(
                new StringReader("BUS 'GENCLS' '1' 4.0 0.0 /"), "bus.dyr"));
        assertThrows(IllegalArgumentException.class,
                () -> PsseDyrRecordReader.tokenize("1 'GENCLS 1 4.0 0.0"));
        IOException unterminated = assertThrows(IOException.class,
                () -> PsseDyrRecordReader.read(
                        new StringReader("1 'GENCLS' '1' 4.0 0.0"), "unterminated.dyr"));
        assertTrue(unterminated.getMessage().contains("unterminated.dyr:1"));
    }

    @Test
    void recognizesUserBusWrapperWithoutInventingADeviceId() throws Exception {
        PsseDyrRecord record = PsseDyrRecordReader.read(new StringReader(
                "17 'USRBUS' 'PLNTBU1' 504 0 7 28 7 15 17 17 18 'A' 0 1 1 /"),
                "synthetic.dyr").get(0);
        assertEquals("PLNTBU1", record.canonicalModelName());
        assertEquals("*", record.deviceId());
        assertEquals(13, record.parameterCount());
        assertEquals(504, record.intParameter(0));
    }
}
