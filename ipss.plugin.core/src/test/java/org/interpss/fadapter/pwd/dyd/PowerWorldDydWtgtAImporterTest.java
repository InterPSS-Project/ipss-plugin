package org.interpss.fadapter.pwd.dyd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.dstab.renewable.WtgtAData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PowerWorldDydWtgtAImporterTest {
    @Test
    void readsExactPowerWorldFieldOrderAndSourceLocation(@TempDir Path tempDir)
            throws Exception {
        Path source = tempDir.resolve("case.dyd");
        Files.writeString(source, "# heading\nmodels\n"
                + "wtgt_a 1004 \"O DONNELL 1 1\" 230.00 \"1\" : #9 "
                + "0 7.16 1.03 1.78 0.49 1\n");

        var records = PowerWorldDydWtgtAImporter.read(source);

        assertEquals(1, records.size());
        assertEquals(3, records.get(0).lineNumber());
        assertEquals(1004, records.get(0).busNumber());
        assertEquals("1", records.get(0).deviceId());
        assertEquals(new WtgtAData(7.16, 1.03, 1.78, .49, 0, 1),
                records.get(0).data());
    }

    @Test
    void rejectsTruncatedWtgtAInsteadOfGuessingDefaults(@TempDir Path tempDir)
            throws Exception {
        Path source = tempDir.resolve("bad.dyd");
        Files.writeString(source,
                "wtgt_a 1004 \"BUS\" 230.00 \"1\" : #9 0 7.16 1.03\n");

        assertThrows(IllegalArgumentException.class,
                () -> PowerWorldDydWtgtAImporter.read(source));
    }
}
