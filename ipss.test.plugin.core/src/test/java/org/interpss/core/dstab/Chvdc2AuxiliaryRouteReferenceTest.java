package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent source-routing contract, distinct from coupled trajectory acceptance. */
public class Chvdc2AuxiliaryRouteReferenceTest {
    @ParameterizedTest @ValueSource(ints={1,2})
    void onlyTheFirstAuxiliarySignalFeedsBothTerminals(int index)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/chvdc2-auxiliary-route-20261104/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent auxiliary-source captures");
        assertEquals("7c2db82ccc067bc31bf6f285b39f7f0cbfdaa7f950cea0f1ad195ccd060883ef",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var z=new ZipFile(archive.toFile())) {
            String prefix="stiff-legacy-rectifier-fault-flags001-h0p0005-aux"+index+"-netiter50-";
            byte[] data=z.getInputStream(z.getEntry(prefix+"a/native.csv")).readAllBytes();
            assertArrayEquals(data,z.getInputStream(z.getEntry(prefix+"b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(data);assertEquals(1005,rows.size());
            double peak=0;int duplicates=0;
            for(int i=0;i<rows.size();i++) {
                var row=rows.get(i);assertTrue(row.values().stream().allMatch(Double::isFinite));
                double auxiliary=row.get("AUX_VAR0");peak=Math.max(peak,Math.abs(auxiliary));
                assertEquals(index==1?auxiliary:0.,row.get("VAR0"),0.);
                assertEquals(index==1?auxiliary:0.,row.get("VAR1"),0.);
                if(i>0&&row.get("time_s").equals(rows.get(i-1).get("time_s")))duplicates++;
            }
            assertTrue(peak>.6,"Real auxiliary controller must be excited");assertEquals(2,duplicates);
        }
    }
}
