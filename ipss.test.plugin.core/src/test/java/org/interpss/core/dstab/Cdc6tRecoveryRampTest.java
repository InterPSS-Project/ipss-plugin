package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import com.interpss.dstab.hvdc.model.Cdc6tRecoveryRamp;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Recovery-factor checks with independently recorded event ownership. */
public class Cdc6tRecoveryRampTest {
    @ParameterizedTest @ValueSource(strings={"instant","delayed"})
    void recordedRestartsDriveBothRecoveryFactors(String profile) throws Exception {
        var path=Path.of("testData/private/verification-artifacts/cdc6t-blocking-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent blocking captures");
        assertEquals("9a39f37ab6519a3a11f7cc1992a38d1b66c3b301d08cec8635899bce55b286e5",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        var ramp=new Cdc6tRecoveryRamp(250000,500000,500,1000,2,2);
        double previousDeadline=99999,start=Double.NaN,max=0;
        int restarts=0,recovery=0,blocked=0;
        try(var zip=new ZipFile(path.toFile())) {
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes());
            // Protection transitions add event-side records beyond the regular output grid.
            assertEquals(profile.equals("instant")?1008:1009,rows.size());
            for(var row:rows) {
                double time=row.get("time_s"),deadline=row.get("VAR1");
                if(previousDeadline<90000&&deadline>90000) {start=time;restarts++;}
                if(deadline<90000) {
                    // The delayed-event record publishes its deadline before refreshing electrical outputs.
                    if(row.get("VAR11")!=0) {
                        assertEquals("delayed",profile);
                        assertTrue(previousDeadline>90000,"Only the event-side record retains conducting current");
                    } else blocked++;
                }
                else if(!Double.isNaN(start)) {
                    max=Math.max(max,Math.abs(ramp.voltageFactor(time-start)-row.get("VAR2")));
                    max=Math.max(max,Math.abs(ramp.currentFactor(time-start)-row.get("VAR3")));
                    recovery++;
                } else {
                    assertEquals(1,row.get("VAR2"),0);assertEquals(1,row.get("VAR3"),0);
                }
                previousDeadline=deadline;
            }
        }
        assertTrue(restarts>=2,"Exercise repeated blocking and recovery");
        assertTrue(recovery>700);assertTrue(blocked>0);
        assertTrue(max<5e-7,"Recovery factor error "+max);
    }
    @Test void independentRatesPlateausAndRepeatedTrials() {
        var ramp=new Cdc6tRecoveryRamp(200,500,300,1000,1,3);
        assertEquals(.4,ramp.voltageFactor(0),0);assertEquals(.3,ramp.currentFactor(0),0);
        assertEquals(.5,ramp.voltageFactor(.1),1e-15);assertEquals(.6,ramp.currentFactor(.1),1e-15);
        assertEquals(1,ramp.currentFactor(1),0);
        assertEquals(.5,ramp.voltageFactor(.1),1e-15,"Trials do not commit time");
        assertEquals(1,ramp.voltageFactor(Double.MAX_VALUE),0);
        assertThrows(IllegalArgumentException.class,()->ramp.currentFactor(-.1));
        assertThrows(IllegalArgumentException.class,()->ramp.voltageFactor(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new Cdc6tRecoveryRamp(1,0,1,1,1,1));
    }
}
