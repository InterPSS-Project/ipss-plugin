package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.interpss.dstab.hvdc.Cdc6tRectifierProtection;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class Cdc6tRectifierProtectionTest {
    private static Cdc6tRectifierProtection model() {
        return new Cdc6tRectifierProtection(.2,.9,.01,.94,.01,.02);
    }
    @Test void previewsDoNotStartTimersAndRejectedTrialsLeaveHistoryUnchanged() {
        var model=model();double[] before=new double[5],after=new double[5],trial=new double[5];
        model.accept(0,1);model.readHistory(before,0);
        model.preview(.1,.8,trial,0);model.preview(.2,.8,trial,0);
        assertEquals(.21,trial[1],1e-15);model.readHistory(after,0);assertArrayEquals(before,after);
        assertThrows(IllegalArgumentException.class,()->model.accept(.1,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->model.accept(-1,1));
        model.readHistory(after,0);assertArrayEquals(before,after);
        model.accept(.2,.8);assertFalse(model.isBlocked());
        model.accept(.211,.8);assertTrue(model.isBlocked());
    }
    @Test void interruptedPickupAndRecoveryResetAndMinimumBlockingDurationIsEnforced() {
        var model=model();double[] history=new double[5];
        model.accept(0,.8);model.accept(.009,.9);model.readHistory(history,0);
        assertTrue(Double.isNaN(history[1]),"Equality cancels strict undervoltage pickup");
        model.accept(.01,.8);model.accept(.019,.8);assertFalse(model.isBlocked());
        model.accept(.021,.8);assertTrue(model.isBlocked());
        model.accept(.022,1);model.accept(.033,1);assertTrue(model.isBlocked(),"Minimum blocking duration dominates");
        model.accept(.034,.94);model.readHistory(history,0);
        assertTrue(Double.isNaN(history[3]),"Equality cancels strict recovery pickup");
        model.accept(.035,1);model.accept(.044,1);assertTrue(model.isBlocked());
        model.accept(.046,1);assertFalse(model.isBlocked());assertEquals(.046,model.lastRestartTime(),0);
        model.accept(.046,.1);assertTrue(model.isBlocked(),"A new same-time electrical observation can reblock");
    }
    @Test void zeroDelaysAndOutputBoundsHaveExplicitBehavior() {
        var model=new Cdc6tRectifierProtection(.2,.9,0,.94,0,0);
        model.accept(0,.8);assertTrue(model.isBlocked());
        model.accept(0,1);assertFalse(model.isBlocked());
        double[] output={11,12,13,14,15};
        assertThrows(IndexOutOfBoundsException.class,()->model.preview(0,1,output,1));
        assertArrayEquals(new double[]{11,12,13,14,15},output);
        assertThrows(IllegalArgumentException.class,()->new Cdc6tRectifierProtection(.2,.9,-1,.94,0,0));
    }
    @Test void firstDelayedPickupMatchesIndependentFaultWithinOneRecordedInterval() throws Exception {
        var path=Path.of("testData/private/verification-artifacts/cdc6t-blocking-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent blocking captures");
        assertEquals("9a39f37ab6519a3a11f7cc1992a38d1b66c3b301d08cec8635899bce55b286e5",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        var model=model();double firstJava=Double.NaN,firstReference=Double.NaN;
        try(var zip=new ZipFile(path.toFile())) {
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry("delayed/a/native.csv")).readAllBytes());
            assertEquals(1009,rows.size());
            for(var row:rows) {
                double time=row.get("time_s");
                if(Double.isNaN(firstReference)&&row.get("VAR1")<90000)firstReference=time;
                model.accept(time,row.get("V7"));
                if(Double.isNaN(firstJava)&&model.isBlocked())firstJava=time;
            }
        }
        assertEquals(.06,firstReference,1e-6);
        assertEquals(firstReference,firstJava,.000501,
            "Recorded voltage excludes internal half-step and post-block trial observations");
    }
}
