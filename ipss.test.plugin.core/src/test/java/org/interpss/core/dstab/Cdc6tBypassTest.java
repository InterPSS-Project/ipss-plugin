package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class Cdc6tBypassTest {
    private static Cdc6tConductingLink link() {
        return new Cdc6tConductingLink(new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8),
            new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8),5,Math.toRadians(5),Math.toRadians(20));
    }
    @ParameterizedTest @ValueSource(strings={"dc","ac"})
    void independentBypassElectricalSamplesAndFirstTimerCycle(String profile) throws Exception {
        var path=Path.of("testData/private/verification-artifacts/cdc6t-bypass-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent bypass captures");
        assertEquals("4af03eff4e7d3699b4eba5bce4088e796625179c81a460ab40362d9eaae2fda7",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        var protection=new Cdc6tBypassProtection(profile.equals("dc")?490000:200,
            profile.equals("ac")?.9:999,profile.equals("ac")?.01:999,.92,.01,.02);
        var link=link();double[] output=new double[8],max=new double[8];
        double firstBypass=Double.NaN,firstUnbypass=Double.NaN,referenceBypass=Double.NaN,referenceUnbypass=Double.NaN;
        double previousDeadline=99999;boolean wasBypassed=false;int samples=0;
        try(var zip=new ZipFile(path.toFile())) {
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes());
            assertEquals(profile.equals("dc")?1006:1007,rows.size());
            for(var row:rows) {
                double time=row.get("time_s"),deadline=row.get("VAR1");
                if(deadline<90000&&Double.isNaN(referenceBypass))referenceBypass=time;
                if(previousDeadline<90000&&deadline>90000&&Double.isNaN(referenceUnbypass))referenceUnbypass=time;
                previousDeadline=deadline;
                protection.accept(time,row.get("V9"),row.get("VAR9"));
                if(protection.isBypassed()&&Double.isNaN(firstBypass))firstBypass=time;
                if(wasBypassed&&!protection.isBypassed()&&Double.isNaN(firstUnbypass))firstUnbypass=time;
                wasBypassed=protection.isBypassed();
                // Electrical qualification applies to every reference sample in the bypass regime.
                if(row.get("VAR9")==0&&row.get("VAR11")>0) {
                    assertEquals(1000,row.get("VAR11"),0,"Scheduled current persists during bypass");
                    link.evaluateBypassed(row.get("V7"),1000,output,0);samples++;
                    String[] keys={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13","VAR10","VAR9"};
                    for(int i=0;i<8;i++)max[i]=Math.max(max[i],Math.abs((i==2||i==5?Math.toDegrees(output[i]):output[i])-row.get(keys[i])));
                }
            }
        }
        assertTrue(samples>=100);
        System.out.println("CDC6T bypass "+profile+" "+java.util.Arrays.toString(max));
        for(int i=0;i<8;i++)assertTrue(max[i]<(i==2||i==5?.0001:i==1?2.5e-5:1e-6),"Bypass coordinate "+i+" error "+max[i]);
        assertEquals(profile.equals("dc")?.05:.06,referenceBypass,1e-6);
        assertEquals(.1105,referenceUnbypass,1e-6);
        assertEquals(referenceBypass,firstBypass,.000501);assertEquals(referenceUnbypass,firstUnbypass,.000501);
    }
    @Test void pickupAndRecoveryResetWithoutMutatingRejectedTrials() {
        var model=new Cdc6tBypassProtection(200,.9,.01,.92,.01,.02);
        double[] before=new double[5],after=new double[5];
        model.accept(0,.8,500000);model.accept(.009,.9,500000);model.accept(.01,.8,500000);
        model.accept(.019,.8,500000);assertFalse(model.isBypassed());
        model.accept(.021,.8,500000);assertTrue(model.isBypassed());
        model.accept(.022,1,0);model.accept(.033,1,0);assertTrue(model.isBypassed());
        model.accept(.034,.92,0);model.accept(.035,1,0);model.accept(.044,1,0);assertTrue(model.isBypassed());
        model.readHistory(before,0);
        assertThrows(IllegalArgumentException.class,()->model.accept(.05,1,Double.NaN));
        model.preview(.05,1,0,after,0);model.readHistory(after,0);assertArrayEquals(before,after);
        model.accept(.046,1,0);assertFalse(model.isBypassed());
    }
    @Test void bypassOutputFailureIsAtomic() {
        var link=link();double[] out={1,2,3,4,5,6,7,8},before=out.clone();
        assertThrows(IllegalArgumentException.class,()->link.evaluateBypassed(.001,1000,out,0));
        assertArrayEquals(before,out);
        assertThrows(IllegalArgumentException.class,()->link.evaluateBypassed(1,-1,out,0));
        assertArrayEquals(before,out);
        link.evaluateBypassed(1,0,out,0);assertEquals(0,out[0],0);assertEquals(0,out[1],0);
    }
}
