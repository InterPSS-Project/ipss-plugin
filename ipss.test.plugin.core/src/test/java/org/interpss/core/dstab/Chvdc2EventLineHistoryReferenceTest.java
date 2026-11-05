package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Source-only discriminator for the first dynamic-line update after an event. */
public class Chvdc2EventLineHistoryReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-controller-20261102/captures.zip");
    private static final String SHA="75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511";

    @Test void firstCurrentIncrementAfterEachEventRetainsLeftHistory()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            for(String side:List.of("rectifier","inverter"))for(String step:List.of("h0p0005","h0p00025")) {
                String tag="stiff-legacy-"+side+"-fault-flags001-"+step+"-";
                byte[] first=zip.getInputStream(zip.getEntry(tag+"a/native.csv")).readAllBytes();
                assertArrayEquals(first,zip.getInputStream(zip.getEntry(tag+"b/native.csv")).readAllBytes());
                List<Map<String,Double>> rows=Tiocr1ResetReferenceTest.csv(first);
                int events=0;
                for(int i=1;i<rows.size()-2;i++) {
                    var left=rows.get(i-1);var right=rows.get(i);
                    if(!left.get("time_s").equals(right.get("time_s")))continue;
                    double time=right.get("time_s");
                    if(Math.abs(time-.05)>2e-6&&Math.abs(time-.1)>2e-6)continue;
                    events++;
                    assertEquals(left.get("VAR5"),right.get("VAR5"),0.,"Current is continuous at "+tag+time);
                    double firstIncrement=rows.get(i+1).get("VAR5")-right.get("VAR5");
                    double secondIncrement=rows.get(i+2).get("VAR5")-rows.get(i+1).get("VAR5");
                    assertTrue(Math.abs(secondIncrement)>.05,"Event must distinguish line integration: "+tag+time);
                    double ratio=firstIncrement/secondIncrement;
                    assertTrue(ratio>.49&&ratio<.53,
                            "The first line update must retain an event-side history: "+tag+time+" ratio "+ratio);
                }
                assertEquals(2,events,"Both event sides must be checked: "+tag);
            }
        }
    }
}
