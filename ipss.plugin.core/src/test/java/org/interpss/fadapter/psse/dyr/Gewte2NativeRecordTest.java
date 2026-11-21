package org.interpss.fadapter.psse.dyr;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Gewte2NativeRecordTest {
    private static String syntheticRecord() {
        var text = new StringBuilder("2 'USRMDL' 'A1' 'GEWTE2' 4 0 12 67 18 9");
        for (int i = 0; i < 12; i++) text.append(' ').append(i);
        for (int i = 0; i < 67; i++) text.append(' ').append(i).append(".25");
        return text.append(" /").toString();
    }

    private static PsseDyrRecord read(String text) throws Exception {
        return PsseDyrRecordReader.read(new StringReader(text), "synthetic-control.dyr").getFirst();
    }

    @Test void preservesPublishedCardinalityAndEverySlot() throws Exception {
        var data = Gewte2NativeRecord.decode(read(syntheticRecord()));
        assertEquals(2, data.generatorBus());
        assertEquals("A1", data.generatorId());
        assertEquals(12, data.integers().size());
        assertEquals(67, data.constants().size());
        for (int i = 0; i < 12; i++) assertEquals(i, data.integers().get(i));
        for (int i = 0; i < 67; i++) assertEquals(i + 0.25, data.constants().get(i));
        assertTrue(DynamicModelCatalog.find("GEWTE2").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> data.integers().set(0, 3));
        assertThrows(UnsupportedOperationException.class, () -> data.constants().set(0, 3.0));
    }

    @Test void rejectsIncorrectAllocationAndMalformedValues() throws Exception {
        String valid = syntheticRecord();
        for (int i = 4; i < 10; i++) {
            String[] fields = valid.split(" ");
            fields[i] = Integer.toString(Integer.parseInt(fields[i]) + 1);
            reject(String.join(" ", fields));
        }
        for (String text : List.of(valid.replace(" /", " 0 /"),
                valid.replace(" 66.25 /", " /"),
                valid.replace("USRMDL", "USRLOD"),
                valid.replace("GEWTE2", "GEWTECU1"),
                valid.replace("2 'USRMDL'", "-2 'USRMDL'"),
                valid.replace("'A1'", "'ABC'"),
                valid.replace(" 11 0.25", " 11.0 0.25"),
                valid.replace(" 0.25", " NaN"),
                valid.replace(" 0.25", " Infinity"))) reject(text);
    }

    private static void reject(String text) throws Exception {
        var error = assertThrows(IllegalArgumentException.class,
                () -> Gewte2NativeRecord.decode(read(text)));
        assertTrue(error.getMessage().contains("synthetic-control.dyr:1"));
    }
}
