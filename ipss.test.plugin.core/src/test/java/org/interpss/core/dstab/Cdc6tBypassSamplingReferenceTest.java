package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent observation-stage discriminators, not runtime trajectory acceptance. */
public class Cdc6tBypassSamplingReferenceTest {
    private static ZipFile archive(String name,String hash)throws Exception {
        Path path=Path.of("testData/private/verification-artifacts/"+name+"/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent paired bypass captures");
        assertEquals(hash,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        return new ZipFile(path.toFile());
    }
    private static List<Map<String,Double>> paired(ZipFile zip,String profile)throws Exception {
        byte[] raw=zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes();
        assertArrayEquals(raw,zip.getInputStream(zip.getEntry(profile+"/b/native.csv")).readAllBytes());
        return Tiocr1ResetReferenceTest.csv(raw);
    }
    @ParameterizedTest @ValueSource(strings={"coarse-offset","fine-offset","coarse-zero","fine-zero","threshold-090","threshold-093"})
    void baseSourceDelaysRecoveryObservationUntilTheNextEndpoint(String profile)throws Exception {
        try(var zip=archive("cdc6t-bypass-sampling-20261104","b6fb32f608e3559068910af93bbbb0cc53bf326f95fd8558ad28d2386fc3cba5")){
            var rows=paired(zip,profile);
            var manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.getInputStream(zip.getEntry(profile+"/a/manifest.json")));
            assertEquals("complete",manifest.get("status").asText());
            double h=manifest.get("settings").get("step").asDouble();
            double delay=manifest.get("entered_constants").get(30).asDouble();
            double threshold=manifest.get("entered_constants").get(8).asDouble();
            var clearing=rows.stream().filter(r->Math.abs(r.get("time_s")-.1)<h/4).toList();
            assertEquals(2,clearing.size(),"Retain both clearing observations");
            double clearTime=clearing.get(0).get("time_s");
            assertTrue(clearing.get(1).get("V9")>threshold,"Converged clearing voltage already exceeds recovery threshold");
            for(var row:clearing)assertTrue(row.get("VAR17")>90000,"Timer is still absent at clearing");
            var release=rows.stream().filter(r->r.get("time_s")>clearTime&&r.get("VAR7")<0).findFirst().orElseThrow();
            double releaseTime=release.get("time_s");
            if(delay==0){
                assertEquals(clearTime+h,releaseTime,3e-8,"Even zero delay waits for the next observation");
            }else{
                var timer=rows.stream().filter(r->r.get("time_s")>clearTime&&r.get("VAR17")<.2).findFirst().orElseThrow();
                assertEquals(clearTime+h,timer.get("time_s"),3e-8);
                double deadline=timer.get("VAR17");
                assertEquals(clearTime+h/2+delay,deadline,3e-8,"Timer uses the preceding midpoint of its first observation");
                assertTrue(releaseTime>=deadline&&releaseTime-h<deadline);
            }
            var sides=rows.stream().filter(r->r.get("time_s")==releaseTime).toList();
            assertEquals(2,sides.size(),"Keep both actual release observations");
            assertEquals(0,sides.get(0).get("VAR7"),0);assertTrue(sides.get(1).get("VAR7")<0);
            for(String state:List.of("STATE0","STATE1","STATE2"))assertEquals(sides.get(0).get(state),sides.get(1).get(state),0);
        }
    }
    @Test void transferredSourceCounterexamplesRejectAUniversalCompletedIntervalRule()throws Exception {
        try(var zip=archive("cdc6t-transferred-modes-20261102","2989ff0be244dad9a96b05cda8c275643414f6e738a7ee6ba1a66e20a9fc397d")){
            for(String control:List.of("current","inverter-power","rectifier-power")){
                var rows=paired(zip,"bypass/"+control);double h=.0005;
                var clearing=rows.stream().filter(r->Math.abs(r.get("time_s")-.1)<h/4).toList();
                assertEquals(2,clearing.size());double clearTime=clearing.get(0).get("time_s");
                assertTrue(clearing.get(0).get("VAR17")>90000);
                assertEquals(clearTime-h/2+.01,clearing.get(1).get("VAR17"),3e-8,
                        "This source publishes the recovery timer on the clearing event itself");
                var release=rows.stream().filter(r->r.get("time_s")>clearTime&&r.get("VAR7")<0).findFirst().orElseThrow();
                assertEquals(clearTime+.01,release.get("time_s"),3e-8);
            }
        }
    }
}
