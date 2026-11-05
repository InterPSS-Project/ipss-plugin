package org.interpss.fadapter.psse.dyr;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Gewtg2NativeRecordTest {
    // Synthetic identities and distinct slot values, not a supplied case record.
    private static final String VALID = "2 'USRMDL' 'A1' 'GEWTG2' 1 1 4 18 3 5 "
            + "0 7 1 0 1.1 2.2 3.3 4.4 5.5 6.6 7.7 8.8 9.9 10.1 11.2 12.3 13.4 14.5 15.6 16.7 17.8 18.9 /";

    private static PsseDyrRecord read(String text) throws Exception {
        return PsseDyrRecordReader.read(new StringReader(text), "synthetic-converter.dyr").getFirst();
    }

    @Test void preservesExactLegacySlotsWithoutAdvertisingRuntimeSupport() throws Exception {
        var data = Gewtg2NativeRecord.decode(read(VALID));
        assertEquals(2, data.generatorBus());
        assertEquals("A1", data.generatorId());
        assertEquals(List.of(0, 7, 1, 0), data.integers());
        assertEquals(List.of(1.1, 2.2, 3.3, 4.4, 5.5, 6.6, 7.7, 8.8, 9.9,
                10.1, 11.2, 12.3, 13.4, 14.5, 15.6, 16.7, 17.8, 18.9), data.constants());
        assertEquals(2.2, data.equivalentReactance());
        assertTrue(DynamicModelCatalog.find("GEWTG2").isEmpty());
    }

    @Test void rejectsMalformedAllocationsAndDataWithoutDefaulting() throws Exception {
        var fields = VALID.split(" ");
        for (int i = 4; i < 10; i++) {
            var bad = fields.clone();
            bad[i] = Integer.toString(Integer.parseInt(bad[i]) + 1);
            reject(String.join(" ", bad));
        }
        for (String bad : new String[]{VALID.replace(" /", " 0 /"), VALID.replace(" 18.9", ""),
                VALID.replace("USRMDL", "USRLOD"), VALID.replace("GEWTG2", "GEWTGCU1"),
                VALID.replace("2 'USRMDL'", "-2 'USRMDL'"), VALID.replace("'A1'", "'ABC'"),
                VALID.replace(" 7 ", " 7.0 "), VALID.replace("1.1", "NaN"),
                VALID.replace("1.1", "Infinity")}) reject(bad);
    }

    private static void reject(String text) throws Exception {
        var record = read(text);
        var failure = assertThrows(IllegalArgumentException.class, () -> Gewtg2NativeRecord.decode(record));
        assertTrue(failure.getMessage().contains("synthetic-converter.dyr:1"));
    }

    @Test void freezesInputListsAndAllowsNoMutationThroughAccessors() throws Exception {
        var parsed = Gewtg2NativeRecord.decode(read(VALID));
        var integers = new ArrayList<>(parsed.integers());
        var constants = new ArrayList<>(parsed.constants());
        var frozen = new Gewtg2NativeRecord(2, "A1", integers, constants);
        integers.set(0, 999);
        constants.set(0, 999.0);
        assertEquals(parsed, frozen);
        assertThrows(UnsupportedOperationException.class, () -> frozen.integers().set(0, 3));
        assertThrows(UnsupportedOperationException.class, () -> frozen.constants().set(0, 3.0));
    }

    @Test void acceptsCommaWrappedAndUnquotedNumericUnitSyntax() throws Exception {
        var data = Gewtg2NativeRecord.decode(read(VALID.replace("'A1'", "1")
                .replace(" 0 7 1 0 ", "\n0,7,1,0, ")));
        assertEquals("1", data.generatorId());
        assertEquals(4, data.integers().size());
        assertEquals(18.9, data.constants().getLast());
    }
}
