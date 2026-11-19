package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Independent source behavior, not a required zero-time update of the runtime. */
public class Tiocr1ReferenceSegmentationContractTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/tiocr-reset-contract-20261104/captures.zip");
    private static final String BASE="target/tiocr-reset04-20261104/tiocr1-0-short-repeated-faults-h0.0005-";
    private static final String SPLIT="target/tiocr-reset04-split-20261104/tiocr1-0-short-repeated-faults-h0.0005-";
    private record Sample(double time,int occurrence) { }
    private static Map<Sample,Map<String,Double>> indexed(List<Map<String,Double>> rows) {
        var counts=new HashMap<Double,Integer>();var result=new LinkedHashMap<Sample,Map<String,Double>>();
        for(var row:rows) { double time=row.get("time_s");int occurrence=counts.getOrDefault(time,0);
            result.put(new Sample(time,occurrence),row);counts.put(time,occurrence+1); }
        return result;
    }
    @Test void extraRunBoundaryChangesOnlyRelayHistoryAndScheduledDeadline()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain complete independent source-contract captures");
        assertEquals("df3281f92bf68e00e6b34fc397c9fc7f6490a570b3e6e2069adec0afbc720933",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            byte[] ordinary=zip.getInputStream(zip.getEntry(BASE+"a/native.csv")).readAllBytes();
            byte[] segmented=zip.getInputStream(zip.getEntry(SPLIT+"a/native.csv")).readAllBytes();
            assertArrayEquals(ordinary,zip.getInputStream(zip.getEntry(BASE+"b/native.csv")).readAllBytes());
            assertArrayEquals(segmented,zip.getInputStream(zip.getEntry(SPLIT+"b/native.csv")).readAllBytes());
            for(String file:List.of("dynamic.dyr","network.raw"))assertArrayEquals(
                    zip.getInputStream(zip.getEntry(BASE+"a/"+file)).readAllBytes(),zip.getInputStream(zip.getEntry(SPLIT+"a/"+file)).readAllBytes());
            var base=indexed(Tiocr1ResetReferenceTest.csv(ordinary));var split=indexed(Tiocr1ResetReferenceTest.csv(segmented));
            assertEquals(1007,base.size());assertEquals(1008,split.size());
            assertTrue(split.keySet().containsAll(base.keySet()),"No baseline sample is discarded");
            for(var key:base.keySet())for(String field:base.get(key).keySet())
                if(!field.equals("VAR1")&&!field.equals("VAR2"))assertEquals(base.get(key).get(field),split.get(key).get(field),field+" "+key);
            var extra=new HashSet<>(split.keySet());extra.removeAll(base.keySet());assertEquals(1,extra.size());
            var key=extra.iterator().next();assertEquals(.055,key.time(),5e-8);assertEquals(1,key.occurrence());
            var before=split.get(new Sample(key.time(),0));var after=split.get(key);
            assertEquals(before.get("VAR0"),after.get("VAR0"));
            assertEquals(.024080455303,after.get("VAR1")-before.get("VAR1"),1e-12);
            double originalDeadline=base.values().stream().mapToDouble(r->r.get("VAR2")).filter(v->v<9999).findFirst().orElseThrow();
            double splitDeadline=split.values().stream().mapToDouble(r->r.get("VAR2")).filter(v->v<9999).findFirst().orElseThrow();
            assertEquals(.0005,originalDeadline-splitDeadline,2e-8,"Segmentation alone advances the scheduled action by one interval");
        }
    }
}
