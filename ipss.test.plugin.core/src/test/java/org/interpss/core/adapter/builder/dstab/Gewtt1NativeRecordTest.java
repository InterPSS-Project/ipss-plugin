package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.Gewtt1NativeRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;

/** Record validation only; neither shaft equations nor SMIB qualification is claimed. */
public class Gewtt1NativeRecordTest {
    private static final String HEADER = "7 'USRMDL' 'A1' 'GEWTT1' 5 0 1 5 4 3 ";
    private static final String VALUES = "0 4.5 0.02 0.7 1.9 2.2";

    private static PsseDyrRecord read(String text) throws Exception {
        return PsseDyrRecordReader.read(new StringReader(text + " /"), "synthetic.dyr").getFirst();
    }

    @Test void preservesIdentityAndImmutableUninterpretedSlots() throws Exception {
        var source = read(HEADER + VALUES);
        var record = Gewtt1NativeRecord.decode(source);
        assertSame(source, record.source());
        assertEquals(0, record.integerSlot());
        assertEquals(List.of(4.5, .02, .7, 1.9, 2.2), record.constants());
        assertThrows(UnsupportedOperationException.class, () -> record.constants().set(0, 0.0));
        assertTrue(DynamicModelCatalog.find("GEWTT1").isEmpty());
        var numericId = Gewtt1NativeRecord.decode(read(HEADER.replace("'A1'", "1") + VALUES));
        assertEquals("1", numericId.source().deviceId());
    }

    @Test void rejectsWrongAllocationCardinalityAndNonIntegerSlot() throws Exception {
        for (int i = 0; i < 6; i++) {
            String[] fields = HEADER.trim().split(" ");
            fields[4 + i] = "999";
            var source = read(String.join(" ", fields) + " " + VALUES);
            var error = assertThrows(IllegalArgumentException.class, () -> Gewtt1NativeRecord.decode(source));
            assertTrue(error.getMessage().contains("synthetic.dyr:1"));
        }
        for (String values : new String[]{"0 4.5", VALUES + " 0", "0.5 4.5 .02 .7 1.9 2.2"}) {
            var source = read(HEADER + values);
            assertThrows(IllegalArgumentException.class, () -> Gewtt1NativeRecord.decode(source));
        }
    }

    @Test void rejectsAliasInvalidOwnerAndNonfiniteConstants() throws Exception {
        for (String text : new String[]{
                HEADER.replace("GEWTT1", "GEWT2MU1") + VALUES,
                HEADER.replace("7 ", "0 ") + VALUES,
                HEADER.replace("'A1'", "'ABC'") + VALUES,
                HEADER + VALUES.replace("0.02", "NaN"),
                HEADER + VALUES.replace("0.02", "Infinity")}) {
            var source = read(text);
            assertThrows(IllegalArgumentException.class, () -> Gewtt1NativeRecord.decode(source));
        }
    }
}
