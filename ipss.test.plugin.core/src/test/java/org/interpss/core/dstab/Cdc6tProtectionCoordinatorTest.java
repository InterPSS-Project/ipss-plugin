package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.interpss.dstab.hvdc.*;
import org.interpss.dstab.hvdc.Cdc6tNormalRuntime.OperatingMode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Transaction and independent event controls; complete solver attachment is a separate gate. */
public class Cdc6tProtectionCoordinatorTest {
    private static Cdc6tProtectionCoordinator coordinator(double inverterDelay,double inverterRecovery){
        return coordinator(inverterDelay,inverterRecovery,Cdc6tProtectionCoordinator.TimerSampling.ENDPOINT);
    }
    private static Cdc6tProtectionCoordinator coordinator(double inverterDelay,double inverterRecovery,Cdc6tProtectionCoordinator.TimerSampling sampling){
        return new Cdc6tProtectionCoordinator(new Cdc6tRectifierProtection(.9,-999,999,.94,.01,.02),
                new Cdc6tInverterProtection(.9,.02,inverterRecovery,inverterDelay,.02),
                new Cdc6tBypassProtection(490000,.9,.01,inverterRecovery,.01,.02),
                new Cdc6tModeSwitch(true,0,.02),new Cdc6tRecoveryRamp(250000,500000,500,1000,2,2),sampling);
    }
    private static void accept(Cdc6tProtectionCoordinator c,double t,double vr,double vi,double dc){c.beginTrial(t);c.observe(vr,vi,dc);c.acceptTrial();}
    private static double[] history(Cdc6tProtectionCoordinator c){double[] h=new double[23];c.readAcceptedHistory(h,0);return h;}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sampledBlockingSeparatesImmediatePickupRecoveryObservationAndPhysicalCommit(boolean inverterCause){
        var c=new Cdc6tProtectionCoordinator(new Cdc6tRectifierProtection(inverterCause?0:.9,-999,999,.94,.01,.02),
                new Cdc6tInverterProtection(inverterCause?.9:0,0,.94,.01,.02),
                new Cdc6tBypassProtection(0,0,0,.94,0,.02),new Cdc6tModeSwitch(false,0,0),
                new Cdc6tRecoveryRamp(250000,500000,500,1000,2,2),Cdc6tProtectionCoordinator.TimerSampling.BLOCK_INTERVAL_MIDPOINT);
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->c.configureIntegrationStep(invalid));
        c.configureIntegrationStep(.0005);
        c.beginTrial(.2,false);c.observe(inverterCause?1:.8,inverterCause?.8:1,500000);
        assertThrows(IllegalStateException.class,()->c.configureIntegrationStep(.001));c.acceptTrial();
        int offset=inverterCause?11:6;
        assertEquals(.21975,history(c)[offset+2],1e-15,"Immediate block uses the preceding midpoint for its minimum deadline");
        assertEquals(.2,history(c)[0],0,"Accepted history keeps the physical event time");
        c.beginTrial(.25,false);c.observe(1,1,0);c.observe(1,1,0);c.acceptTrial();
        assertTrue(Double.isNaN(history(c)[offset+3]),"Repeated zero-duration event observations cannot start recovery");
        var before=history(c);
        c.beginTrial(.2505,true);c.observe(1,1,0);c.discardTrial();assertArrayEquals(before,history(c));
        c.beginTrial(.2505,true);c.observe(1,1,0);c.acceptTrial();
        assertEquals(.26025,history(c)[offset+3],1e-15,"First completed interval samples the following midpoint after clearing");
        accept(c,.26,1,1,0);assertEquals(OperatingMode.BLOCKED,c.operatingMode());
        accept(c,.2605,1,1,0);assertEquals(OperatingMode.RECOVERING,c.operatingMode());
        assertEquals(.2605,history(c)[3],0,"Recovery ramps start at physical release, not at the timer origin");
        assertEquals(.5,c.currentFactor(),0);assertEquals(.5,c.voltageFactor(),0);
        c.reset();c.beginTrial(.2,false);c.observe(inverterCause?1:.8,inverterCause?.8:1,500000);c.acceptTrial();
        assertEquals(.21975,history(c)[offset+2],1e-15,"Reinitialization retains the prepared network interval");
    }
    @Test void failedTrialAndItsPendingCommunicationDoNotLeakIntoAcceptedHistory(){
        var c=coordinator(.01,.92);accept(c,0,1,1,500000);double[] before=history(c);
        c.beginTrial(.05);c.observe(1,.8,480000);assertEquals(OperatingMode.BYPASSED,c.operatingMode());
        assertArrayEquals(before,history(c));c.discardTrial();assertEquals(OperatingMode.NORMAL,c.operatingMode());
        accept(c,.08,1,1,500000);assertEquals(OperatingMode.NORMAL,c.operatingMode(),"Discarded communication request must not fire");
        before=history(c);c.beginTrial(.09);c.observe(.8,.8,480000);
        assertThrows(IllegalArgumentException.class,()->c.observe(1,Double.NaN,0));
        assertThrows(IllegalStateException.class,c::acceptTrial);assertThrows(IllegalStateException.class,c::operatingMode);
        assertArrayEquals(before,history(c));c.discardTrial();assertArrayEquals(before,history(c));
        c.beginTrial(.1);assertThrows(IllegalStateException.class,c::acceptTrial);assertThrows(IllegalStateException.class,c::reset);c.discardTrial();
        assertThrows(IllegalArgumentException.class,()->c.beginTrial(.07));
    }
    @Test void inverterBlockClearsBypassAndOwnsBothRecoveryRamps(){
        var c=coordinator(.01,.92);accept(c,0,1,1,500000);accept(c,.05,1,.8,480000);
        assertEquals(OperatingMode.BYPASSED,c.operatingMode());accept(c,.071,1,.8,0);
        assertEquals(OperatingMode.BLOCKED,c.operatingMode());assertEquals(0,history(c)[16],0,"Bypass flag is cleared");
        accept(c,.1,1,1,0);c.beginTrial(.112);c.observe(1,1,0);
        assertEquals(OperatingMode.RECOVERING,c.operatingMode());assertEquals(.5,c.voltageFactor(),0);assertEquals(.5,c.currentFactor(),0);
        c.observe(1,1,250000);assertEquals(OperatingMode.RECOVERING,c.operatingMode(),"Block recovery must inhibit DC bypass pickup");c.acceptTrial();
        accept(c,.212,1,1,350000);assertEquals(.7,c.voltageFactor(),1e-14);assertEquals(.7,c.currentFactor(),1e-14);
        accept(c,.363,1,1,500000);assertEquals(OperatingMode.NORMAL,c.operatingMode());
        c.reset();assertEquals(OperatingMode.NORMAL,c.operatingMode());assertEquals(1,c.voltageFactor(),0);accept(c,-.001,1,1,500000);
    }
    @Test void rectifierCauseUsesItsOwnVoltageAndPreservesRejectedReblocking(){
        var c=coordinator(.04,.99);accept(c,0,1,.965,500000);accept(c,.05,.84,.956,480000);
        accept(c,.051,.98,.932,0);var before=history(c);
        c.beginTrial(.071);c.observe(.98,.932,0);
        assertEquals(OperatingMode.RECOVERING,c.operatingMode(),"Unrelated inverter recovery threshold must not hold a rectifier block");
        c.observe(.84,.932,250000);assertEquals(OperatingMode.BLOCKED,c.operatingMode());
        c.discardTrial();assertArrayEquals(before,history(c));
        accept(c,.071,.98,.932,0);assertEquals(OperatingMode.RECOVERING,c.operatingMode());
    }
    @Test void predictedRampCompletionKeepsTheRecoveryRegimeUntilObservation(){
        var c=coordinator(.01,.92);accept(c,0,1,1,500000);
        accept(c,.05,.8,1,500000);accept(c,.051,1,1,0);accept(c,.071,1,1,0);
        assertEquals(OperatingMode.RECOVERING,c.operatingMode());
        double[] committed=history(c);
        c.beginTrial(.4);
        assertEquals(1,c.voltageFactor(),0);assertEquals(1,c.currentFactor(),0);
        assertEquals(OperatingMode.RECOVERING,c.operatingMode(),"Predictor can reach the ramp limit without changing the current-control regime");
        assertArrayEquals(committed,history(c));c.discardTrial();assertArrayEquals(committed,history(c));
        c.beginTrial(.4);c.observe(1,1,500000);
        assertEquals(OperatingMode.NORMAL,c.operatingMode());c.acceptTrial();assertEquals(.4,history(c)[0],0);
    }
    @ParameterizedTest @ValueSource(strings={"inverter","delay"})
    void independentOverlappingBypassAndBlockingRetainCauseSpecificRelease(String profile)throws Exception{
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-coordination-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain coordinated independent captures");
        assertEquals("40a91608fe41d356c3c6c108da7d548e1d9e530e1d32af70ee47700ee518f6f9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        var c=coordinator(profile.equals("delay")?.04:.01,.92);
        double bypass=Double.NaN,block=Double.NaN,recovery=Double.NaN,referenceBypass=Double.NaN,referenceBlock=Double.NaN,referenceRecovery=Double.NaN;
        int mismatches=0,rowsSeen=0,duplicates=0;double previous=Double.NEGATIVE_INFINITY;
        try(var zip=new ZipFile(archive.toFile())){
            var raw=zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes();
            assertArrayEquals(raw,zip.getInputStream(zip.getEntry(profile+"/b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(raw);
            for(var r:rows){
                double t=r.get("time_s");if(t==previous)duplicates++;previous=t;rowsSeen++;
                OperatingMode expected=r.get("VAR11")==0?OperatingMode.BLOCKED:r.get("VAR9")==0?OperatingMode.BYPASSED
                        :r.get("VAR2")<1||r.get("VAR3")<1?OperatingMode.RECOVERING:OperatingMode.NORMAL;
                if(expected==OperatingMode.BYPASSED&&Double.isNaN(referenceBypass))referenceBypass=t;
                if(expected==OperatingMode.BLOCKED&&Double.isNaN(referenceBlock))referenceBlock=t;
                if(expected==OperatingMode.RECOVERING&&Double.isNaN(referenceRecovery))referenceRecovery=t;
                accept(c,t,r.get("V7"),r.get("V9"),r.get("VAR9"));var mode=c.operatingMode();
                if(mode==OperatingMode.BYPASSED&&Double.isNaN(bypass))bypass=t;
                if(mode==OperatingMode.BLOCKED&&Double.isNaN(block))block=t;
                if(mode==OperatingMode.RECOVERING&&Double.isNaN(recovery))recovery=t;
                if(mode!=expected){
                    mismatches++;
                    double release=profile.equals("delay")?.1405:.1105;
                    assertTrue(Math.abs(t-.07)<.0011||Math.abs(t-release)<.0011||Math.abs(t-release-.25)<.0011,
                            "Unexplained mode discrepancy at "+t+": "+mode+" vs "+expected);
                }
                if(mode==OperatingMode.BLOCKED)assertEquals(0,history(c)[16],0);
            }
        }
        assertTrue(rowsSeen>1005);assertTrue(duplicates>2);assertTrue(mismatches<=12,"Endpoint replay transition discrepancies "+mismatches);
        assertEquals(.05,referenceBypass,1e-6);assertEquals(.07,referenceBlock,1e-6);
        assertEquals(profile.equals("delay")?.1405:.1105,referenceRecovery,1e-6);
        assertEquals(referenceBypass,bypass,.000501);assertEquals(referenceBlock,block,.000501);assertEquals(referenceRecovery,recovery,.000501);
        System.out.println("CDC6T coordinated "+profile+" first bypass/block/recovery="+bypass+"/"+block+"/"+recovery+" differing transition rows="+mismatches);
    }
    @Test void independentRectifierRecoveryDoesNotRequireTheInverterRecoveryVoltage()throws Exception{
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-coordination-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain coordination discriminator captures");
        assertEquals("40a91608fe41d356c3c6c108da7d548e1d9e530e1d32af70ee47700ee518f6f9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var zip=new ZipFile(archive.toFile())){
            var raw=zip.getInputStream(zip.getEntry("rectifier/a/native.csv")).readAllBytes();
            assertArrayEquals(raw,zip.getInputStream(zip.getEntry("rectifier/b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(raw);int recovery=0;
            var manifest=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("rectifier/a/manifest.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(.99,manifest.getAsJsonArray("entered_constants").get(8).getAsDouble(),0);
            for(var r:rows)if(r.get("VAR11")>0&&r.get("VAR2")<1){
                assertTrue(r.get("V9")<.99,"Inverter is below its recovery threshold throughout this control");recovery++;
            }
            assertTrue(recovery>400);
            var snapshots=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("rectifier/a/snapshots.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            boolean sawRecovery=false;
            for(var item:snapshots){var sample=item.getAsJsonObject();if(sample.get("stage").getAsString().equals("inspection-0.111")){
                assertEquals(0,sample.getAsJsonArray("icons").get(0).getAsInt());
                assertEquals(2,sample.getAsJsonArray("icons").get(1).getAsInt());sawRecovery=true;
            }}
            assertTrue(sawRecovery);
        }
    }
    @Test void bothIntegrationOwnersCanUseTheSameRegimeWithoutCommittingOnEvaluation(){
        for(boolean staggered:new boolean[]{false,true}){
            var c=coordinator(.01,.92);var r=Cdc6tOperatingBoundaryTest.runtime();
            accept(c,0,1,1,500000);accept(c,.05,.84,.95,480000);double[] before=history(c);
            double[] current=new double[4],states=new double[3],rates=new double[3];
            r.evaluateBoundary(1,0,1,0,0,c.operatingMode(),c.voltageFactor(),c.currentFactor(),current,0);
            if(staggered){r.readStaggeredState(states,0);r.evaluateStaggered(.0005,rates,0);for(int i=0;i<3;i++)states[i]+=.0005*rates[i];r.applyStaggeredState(states,0,.0005);}
            else {r.step(.0005,0);r.evaluateBoundary(1,0,1,0,0,c.operatingMode(),c.voltageFactor(),c.currentFactor(),current,0);r.step(.0005,1);}
            assertArrayEquals(before,history(c),"Integration or current evaluation cannot accept a protection event");
            r.readStaggeredState(states,0);assertTrue(states[0]<500000);assertTrue(states[1]<1000);assertTrue(states[2]<505000);
        }
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(Cdc6tProtectionCoordinator.TimerSampling.class)
    void warmedNormalTrialsAndRollbacksReuseHistoryBuffers(Cdc6tProtectionCoordinator.TimerSampling sampling){
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        var c=coordinator(.01,.92,sampling);c.configureIntegrationStep(.0005);accept(c,0,1,1,500000);
        for(int i=0;i<30000;i++){c.beginTrial(1);c.observe(1,1,500000);c.discardTrial();}
        long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
        for(int i=0;i<100000;i++){c.beginTrial(1);c.observe(1,1,500000);c.discardTrial();}
        long bytes=bean.getThreadAllocatedBytes(thread)-before;
        System.out.println("CDC6T coordinator warmed allocation bytes="+bytes);assertTrue(bytes<=1024,"History trials must reuse storage: "+bytes);
    }
}
