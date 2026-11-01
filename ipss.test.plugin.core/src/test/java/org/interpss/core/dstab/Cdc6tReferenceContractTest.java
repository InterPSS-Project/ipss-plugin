package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Establishes reference units and physical balance; does not claim a controller implementation. */
public class Cdc6tReferenceContractTest {
    @ParameterizedTest
    @ValueSource(strings={"flat","fault"})
    void coldCapturesPreserveDcVoltageAndPowerBalance(String profile) throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-reference-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain CDC6T independent reference captures");
        assertEquals("69a1ee22d4d9e6426a7228cd96f457fe982a4b988810a7a9e580b7cbba4c8022",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry(profile+"/b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(first);
            assertEquals(1005,rows.size());
            int duplicates=0;double previous=Double.NEGATIVE_INFINITY;
            for(var row:rows) {
                double time=row.get("time_s");assertTrue(time>=previous);
                if(time==previous)duplicates++;previous=time;
                double vr=row.get("VAR10"),vi=row.get("VAR9"),current=row.get("VAR11");
                assertEquals(5*current,vr-vi,.1,"Five-ohm DC-link voltage drop in volts");
                assertEquals(vr*current/1e8,row.get("VAR5"),2e-6,"Rectifier power on 100 MVA base");
                assertEquals(-vi*current/1e8,row.get("VAR7"),2e-6,"Inverter power sign and base");
                assertTrue(row.get("VAR6")>=0);assertTrue(row.get("VAR8")>=0);
                if(profile.equals("flat")) {
                    assertEquals(500000,row.get("STATE0"),0);
                    assertEquals(1000,row.get("STATE1"),0);
                    assertEquals(505000,row.get("STATE2"),0);
                }
            }
            assertEquals(2,duplicates,"Keep segment/event-side duplicates even in the flat control");
        }
    }
}
