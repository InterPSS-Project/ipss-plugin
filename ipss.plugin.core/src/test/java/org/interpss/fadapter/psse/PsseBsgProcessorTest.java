package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.dstab.renewable.ActivePowerResponseLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PsseBsgProcessorTest {
    @TempDir Path directory;
    @Test void legacyAndExplicitModesPreserveGeneratorIdentity() throws Exception {
        var file=directory.resolve("case.bsg");
        Files.writeString(file,"// response list\n120493 \"A LONG BUS NAME\" 13.8 \"2\"\n"
                +"120494 \"SECOND\" 18 \"1\" 1\n120495 \"THIRD\" 18 \"1\" 0\n"
                +"120493 \"A LONG BUS NAME\" 13.8 \"2\" // identical duplicate\n");
        var entries=PsseBsgProcessor.read(file);
        assertEquals(3,entries.size());
        assertEquals(ActivePowerResponseLimits.FIXED,entries.get(new PsseGnetIdvProcessor.GeneratorKey("Bus120493","2")).response());
        assertEquals(ActivePowerResponseLimits.DOWN_ONLY,entries.get(new PsseGnetIdvProcessor.GeneratorKey("Bus120494","1")).response());
        assertEquals(ActivePowerResponseLimits.NORMAL,entries.get(new PsseGnetIdvProcessor.GeneratorKey("Bus120495","1")).response());
        assertThrows(UnsupportedOperationException.class,entries::clear);
    }
    @Test void invalidOrConflictingRecordsFailWithLineNumber() throws Exception {
        for(String row:new String[]{"1 X 13.8 1","1 \"X\" NaN \"1\"","0 \"X\" 13.8 \"1\"","1 \"X\" 13.8 \"1\" 9",
                "1 \"X\" 13.8 \"1\" 0\n1 \"X\" 13.8 \"1\" 2"}) {
            var file=directory.resolve("invalid.bsg");Files.writeString(file,row);
            var error=assertThrows(java.io.IOException.class,()->PsseBsgProcessor.read(file));
            assertTrue(error.getMessage().contains("invalid.bsg:"));
        }
    }
}
