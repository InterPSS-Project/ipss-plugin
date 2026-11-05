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
                protection.accept(time,row.get("V9"),row.get("VAR9"),row.get("VAR2"));
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
        model.accept(0,.8,500000,1);model.accept(.009,.9,500000,1);model.accept(.01,.8,500000,1);
        model.accept(.019,.8,500000,1);assertFalse(model.isBypassed());
        model.accept(.021,.8,500000,1);assertTrue(model.isBypassed());
        model.accept(.022,1,0,1);model.accept(.033,1,0,1);assertTrue(model.isBypassed());
        model.accept(.034,.92,0,1);model.accept(.035,1,0,1);model.accept(.044,1,0,1);assertTrue(model.isBypassed());
        model.readHistory(before,0);
        assertThrows(IllegalArgumentException.class,()->model.accept(.05,1,Double.NaN,1));
        model.preview(.05,1,0,1,after,0);model.readHistory(after,0);assertArrayEquals(before,after);
        model.accept(.046,1,0,1);assertFalse(model.isBypassed());
    }
    @Test void bypassOutputFailureIsAtomic() {
        var link=link();double[] out={1,2,3,4,5,6,7,8},before=out.clone();
        assertThrows(IllegalArgumentException.class,()->link.evaluateBypassed(.001,1000,out,0));
        assertArrayEquals(before,out);
        assertThrows(IllegalArgumentException.class,()->link.evaluateBypassed(1,-1,out,0));
        assertArrayEquals(before,out);
        link.evaluateBypassed(1,0,out,0);assertEquals(0,out[0],0);assertEquals(0,out[1],0);
    }
    @Test void recoveryInhibitsOnlyDcPickupUntilVoltageRampCompletes() {
        var model=new Cdc6tBypassProtection(490000,.9,.01,.92,0,0);
        model.accept(0,1,480000,1);assertTrue(model.isBypassed());
        model.accept(0,1,0,1);assertTrue(model.isRecovering());
        model.accept(.01,1,100000,.5);assertFalse(model.isBypassed());
        model.accept(.02,.8,100000,.6);assertFalse(model.isBypassed());
        model.accept(.031,.8,100000,.7);assertTrue(model.isBypassed(),"AC pickup remains active during recovery");
        model.accept(.04,1,0,.7);assertTrue(model.isRecovering());
        model.accept(.05,1,300000,.8);assertFalse(model.isBypassed());
        model.accept(.06,1,480000,1);assertTrue(model.isBypassed(),"DC pickup resumes at completed recovery");
        double[] before=new double[5],after=new double[5];model.readHistory(before,0);
        assertThrows(IllegalArgumentException.class,()->model.accept(.07,1,500000,1.1));
        model.readHistory(after,0);assertArrayEquals(before,after);
    }
    @ParameterizedTest @ValueSource(strings={"dc-recovery","ac-recovery","dc-restored"})
    void secondFaultDiscriminatesRecoveryPickupRules(String profile) throws Exception {
        var path=Path.of("testData/private/verification-artifacts/cdc6t-bypass-recovery-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent recovery-mode discriminators");
        assertEquals("c3a170c4297f1238a5b94d524c39cee6fa088f7c6bb47efeac8a2e65cd15d806",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        boolean ac=profile.equals("ac-recovery"),restored=profile.equals("dc-restored");
        var model=new Cdc6tBypassProtection(ac?200:490000,ac?.9:999,ac?.01:999,.92,.01,.02);
        double[] transitions=ac?new double[]{.06,.1105,.21,.2605}:restored?new double[]{.05,.1105,.38,.4405}:new double[]{.05,.1105};
        int interior=0,edgeDifferences=0;
        try(var zip=new ZipFile(path.toFile())) {
            var snapshots=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(
                zip.getEntry(profile+"/a/snapshots.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            for(var element:snapshots) {
                var s=element.getAsJsonObject();String stage=s.get("stage").getAsString();
                int mode=s.getAsJsonArray("icons").get(0).getAsInt();
                if(stage.equals("before-second-fault"))assertEquals(restored?0:2,mode);
                if(stage.equals("before-second-clearing"))assertEquals(!ac&&!restored?2:1,mode);
            }
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes());
            assertEquals(ac?1011:restored?1009:1008,rows.size());
            for(var row:rows) {
                double time=row.get("time_s");
                model.accept(time,row.get("V9"),row.get("VAR9"),row.get("VAR2"));
                boolean referenceBypassed=row.get("VAR9")==0&&row.get("VAR11")>0;
                if(model.isBypassed()!=referenceBypassed) {
                    double distance=Double.POSITIVE_INFINITY;
                    for(double event:transitions)distance=Math.min(distance,Math.abs(time-event));
                    assertTrue(distance<.0011,"Unexpected bypass difference at "+time);edgeDifferences++;
                }
                if(time>(restored?.381:.211)&&time<(restored?.429:.249)) {
                    assertEquals(ac||restored,model.isBypassed());
                    assertEquals(ac||restored,referenceBypassed);interior++;
                    if(!ac&&!restored) {
                        assertTrue(model.isRecovering());
                        assertTrue(row.get("VAR9")<490000*row.get("VAR2"),"Discriminate even a scaled DC threshold");
                    }
                }
            }
        }
        assertTrue(interior>70);assertTrue(edgeDifferences<=12,"Endpoint replay differences must remain local to events");
    }
}
