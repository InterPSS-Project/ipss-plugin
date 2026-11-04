package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Test;

/** Source decoding only; no pitch-controller trajectory acceptance is implied. */
public class Gewtp2NativeRecordTest {
    private static final String HEADER = "7 'USRMDL' 'A1' 'GEWTP2' 505 0 1 10 3 3 ";
    private static final String CONSTANTS = "0.25 140 20 2 28 -3 26 -9 9 0.95";
    private static PsseDyrRecord read(String text) throws Exception {
        return PsseDyrRecordReader.read(new StringReader(text + " /"), "synthetic-pitch.dyr").getFirst();
    }

    @Test void preservesIdentityIntegerAndImmutableConstantOrderWithoutRuntimeClaim() throws Exception {
        var source = read(HEADER + "0 " + CONSTANTS);
        var record = Gewtp2NativeRecord.decode(source);
        assertSame(source, record.source());
        assertEquals(7, record.source().busNumber());
        assertEquals("A1", record.source().deviceId());
        assertEquals(0, record.integerSlot());
        assertEquals(List.of(.25, 140.0, 20.0, 2.0, 28.0, -3.0, 26.0, -9.0, 9.0, .95), record.constants());
        assertThrows(UnsupportedOperationException.class, () -> record.constants().set(0, 99.0));
        assertTrue(DynamicModelCatalog.find("GEWTP2").isEmpty());
        // Preserve the slot without inventing an undocumented enumeration.
        assertEquals(17, Gewtp2NativeRecord.decode(read(HEADER + "17 " + CONSTANTS)).integerSlot());
    }

    @Test void rejectsAllocationCardinalityAndNonintegerSlotWithLocation() throws Exception {
        for (int i = 0; i < 6; i++) {
            String[] fields = HEADER.trim().split(" ");
            fields[4 + i] = "999";
            var source = read(String.join(" ", fields) + " 0 " + CONSTANTS);
            var error = assertThrows(IllegalArgumentException.class, () -> Gewtp2NativeRecord.decode(source));
            assertTrue(error.getMessage().contains("synthetic-pitch.dyr:1"));
        }
        for (String body : new String[]{CONSTANTS, "0 " + CONSTANTS + " 0", "0.5 " + CONSTANTS}) {
            var source = read(HEADER + body);
            assertThrows(IllegalArgumentException.class, () -> Gewtp2NativeRecord.decode(source));
        }
    }

    @Test void rejectsModernAliasInvalidOwnerAndNonfiniteConstants() throws Exception {
        for (String text : new String[]{HEADER.replace("GEWTP2", "GEWTPTU1") + "0 " + CONSTANTS,
                HEADER.replace("7 ", "0 ") + "0 " + CONSTANTS,
                HEADER.replace("'A1'", "'ABC'") + "0 " + CONSTANTS,
                HEADER + "0 " + CONSTANTS.replace("0.25", "NaN"),
                HEADER + "0 " + CONSTANTS.replace("0.25", "Infinity")}) {
            var source = read(text);
            assertThrows(IllegalArgumentException.class, () -> Gewtp2NativeRecord.decode(source));
        }
    }
}
