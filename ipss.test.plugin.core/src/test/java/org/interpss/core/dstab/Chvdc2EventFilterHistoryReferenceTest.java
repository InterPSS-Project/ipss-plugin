package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Source-only discriminator for filter forcing on both sides of a scheduled fault. */
public class Chvdc2EventFilterHistoryReferenceTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-orders-20261102/captures.zip");
    private static final String SHA="e62a9f7e3944adbeaafc98ace40d428a8007b071ac1cd5c3d6f3552ba1480d05";
    private static final String TAG="stiff-legacy-inverter-fault-flags001-h0p0005-dc-limit-";

    @Test void applicationAndClearingUseAnEventStageBeforeFullRightInput()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(ARCHIVE))));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry(TAG+"a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry(TAG+"b/native.csv")).readAllBytes());
            var manifest=new ObjectMapper().readTree(zip.getInputStream(zip.getEntry(TAG+"a/manifest.json")));
            assertEquals("complete",manifest.path("status").asText());
            double[] constants=new ObjectMapper().convertValue(manifest.path("entered_constants"),double[].class);
            double timeConstant=constants[17];
            assertEquals(.01,timeConstant,1e-9);
            List<Map<String,Double>> rows=Tiocr1ResetReferenceTest.csv(first);
            assertEquals(1005,rows.size());
            int events=0;
            for(int i=1;i<rows.size()-1;i++) {
                var left=rows.get(i-1);var right=rows.get(i);var next=rows.get(i+1);
                if(!left.get("time_s").equals(right.get("time_s")))continue;
                double t=right.get("time_s");
                if(Math.abs(t-.05)>2e-6&&Math.abs(t-.1)>2e-6)continue;
                events++;
                assertEquals(left.get("STATE11"),right.get("STATE11"),0.,"No state step at the event");
                double h=next.get("time_s")-t;
                assertEquals(.0005,h,2e-8);
                double x=right.get("STATE11");
                double leftRate=(left.get("V9")-x)/timeConstant;
                double rightRate=(right.get("V9")-x)/timeConstant;
                double eventStage=x+.5*h*(leftRate+rightRate);
                double fullRight=x+h*rightRate;
                assertEquals(eventStage,next.get("STATE11"),1e-5,
                        "First filter state follows the left/right event stage at "+t);
                assertTrue(Math.abs(fullRight-next.get("STATE11"))>1.5e-4,
                        "A full right-input interval must remain distinguishable at "+t);
            }
            assertEquals(2,events,"Fault application and clearing must both be checked");
        }
    }
}
