package org.interpss.fadapter.psse.dyr;

import java.io.StringReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Gewplt2NativeRecordTest {
    private static final String VALID = "90001 'USRMSC' 'GEWPLT2' 512 0 2 0 0 17 2 'A1' /";

    private static PsseDyrRecord read(String text) throws Exception {
        return PsseDyrRecordReader.read(new StringReader(text), "observation-fixture.dyr").getFirst();
    }

    @Test void preservesObservationIdentifierSeparatelyFromMachineTarget() throws Exception {
        var record = read(VALID);
        assertEquals("GEWPLT2", record.sourceModelName());
        assertEquals("*", record.deviceId());
        assertEquals(3, record.parameterOffset());
        assertEquals(new Gewplt2NativeRecord(90001, 2, "A1"), Gewplt2NativeRecord.decode(record));
        assertTrue(DynamicModelCatalog.find("GEWPLT2").isEmpty(), "Schema decoding must not advertise runtime support");
    }

    @Test void rejectsAllocationAndCardinalityVariants() throws Exception {
        String[] fields = VALID.split(" ");
        for (int i = 3; i < 9; i++) {
            String[] bad = fields.clone();
            bad[i] = Integer.toString(Integer.parseInt(bad[i]) + 1);
            var record = read(String.join(" ", bad));
            assertThrows(IllegalArgumentException.class, () -> Gewplt2NativeRecord.decode(record));
        }
        for (String bad : new String[]{VALID.replace(" /", " 0 /"), VALID.replace(" 'A1'", ""),
                VALID.replace("USRMSC", "USRBUS"), VALID.replace("90001", "-90001"),
                VALID.replace("17 2", "17 -2"), VALID.replace("17 2", "17 2.0"),
                VALID.replace("'A1'", "'ABC'"), VALID.replace("'A1'", "''"),
                VALID.replace("'A1'", "A1"), VALID.replace("'USRMSC'", "USRMSC"),
                VALID.replace("'GEWPLT2'", "GEWPLT2")}) {
            var record = read(bad);
            var failure = assertThrows(IllegalArgumentException.class, () -> Gewplt2NativeRecord.decode(record));
            assertTrue(failure.getMessage().contains("observation-fixture.dyr:1"));
        }
    }

    @Test void retainsUnknownMiscellaneousModelIdentityWithoutAssumingBusOwnership() throws Exception {
        var record = read(VALID.replace("GEWPLT2", "UNKNOWNMSC"));
        assertEquals("UNKNOWNMSC", record.sourceModelName());
        assertEquals("*", record.deviceId());
        assertEquals(90001, record.busNumber());
        assertThrows(IllegalArgumentException.class, () -> Gewplt2NativeRecord.decode(record));
    }

    @Test void commasWhitespaceAndUnitPaddingDoNotChangeTheTarget() throws Exception {
        var record = read("90001, 'USRMSC', 'GEWPLT2', 512,0,2,0,0,17,\n 2, '1 ' /");
        assertEquals(new Gewplt2NativeRecord(90001, 2, "1"), Gewplt2NativeRecord.decode(record));
        assertEquals(2, record.endLine());
    }
}
