package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.Gewta2NativeRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;

/** Decoder checks only; no aerodynamic trajectory acceptance is implied. */
public class Gewta2NativeRecordTest {
    private static final String HEADER = "7 'USRMDL' 'A1' 'GEWTA2' 505 0 0 9 1 4 ";
    private static final String CONSTANTS = "18 2 25 -3 0.02 1.2 50 100 1200";

    private static PsseDyrRecord read(String text) throws Exception {
        return PsseDyrRecordReader.read(new StringReader(text + " /"), "synthetic.dyr").getFirst();
    }

    @Test
    void preservesSourceIdentityOrderAndImmutableConstantsWithoutClaimingSupport() throws Exception {
        PsseDyrRecord source = read(HEADER + CONSTANTS);
        var decoded = Gewta2NativeRecord.decode(source);
        assertSame(source, decoded.source());
        assertEquals(7, decoded.source().busNumber());
        assertEquals("A1", decoded.source().deviceId());
        assertEquals(java.util.List.of(18.0, 2.0, 25.0, -3.0, .02, 1.2, 50.0, 100.0, 1200.0), decoded.constants());
        assertThrows(UnsupportedOperationException.class, () -> decoded.constants().set(0, 0.0));
        assertTrue(DynamicModelCatalog.find("GEWTA2").isEmpty());
    }

    @Test
    void rejectsEveryWrongAllocationAndCardinalityWithSourceLocation() throws Exception {
        for (int position = 0; position < 6; position++) {
            String[] fields = HEADER.trim().split(" ");
            fields[4 + position] = "999";
            PsseDyrRecord source = read(String.join(" ", fields) + " " + CONSTANTS);
            var error = assertThrows(IllegalArgumentException.class, () -> Gewta2NativeRecord.decode(source));
            assertTrue(error.getMessage().contains("synthetic.dyr:1"));
        }
        for (String constants : new String[]{"18 2", CONSTANTS + " 0"}) {
            var source = read(HEADER + constants);
            assertThrows(IllegalArgumentException.class, () -> Gewta2NativeRecord.decode(source));
        }
    }

    @Test
    void rejectsModernAliasNonfiniteConstantsAndInvalidOwner() throws Exception {
        for (String text : new String[]{
                HEADER.replace("GEWTA2", "GEWTARU1") + CONSTANTS,
                HEADER.replace("7 ", "0 ") + CONSTANTS,
                HEADER.replace("'A1'", "'ABC'") + CONSTANTS,
                HEADER + CONSTANTS.replace("0.02", "NaN"),
                HEADER + CONSTANTS.replace("0.02", "Infinity")}) {
            var source = read(text);
            assertThrows(IllegalArgumentException.class, () -> Gewta2NativeRecord.decode(source));
        }
    }
}
