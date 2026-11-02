package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.interpss.dstab.hvdc.Cdc6tNormalRuntime.OperatingMode;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Independent repeated-fault coverage through deep terminal voltage during recovery. */
public class Cdc6tDeepRecoveryTest extends CorePluginTestSetup {
    @TempDir Path folder;
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("deep","nearzero").flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void secondFaultDuringRecoveryBlocksAndRestartsWithinTheOriginalNetworkBudget(String profile,boolean staggered)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-"+profile+"-recovery-20261102/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent repeated-fault captures");
        assertEquals(profile.equals("deep")?"9249ae52bd557f2e4f4f8262d1111f825adbaf5aac2c7617e92e1860c33bd0fb":"eaca1970bb56dd385bb18ba4722694448d5173ddaebff83da6aba34d59dcf242",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
        double step,reactance;double[] events=new double[4],referenceEvents=new double[4];
        try(var z=new ZipFile(archive.toFile())){
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            var settings=mapper.readTree(z.getInputStream(z.getEntry("a/manifest.json"))).get("settings");
            step=settings.get("step").doubleValue();reactance=settings.get("second_fault_reactance").doubleValue();
            String[] names={"before-first-fault","before-first-clearing","before-second-fault","before-second-clearing"};
            var snapshots=mapper.readTree(z.getInputStream(z.getEntry("a/snapshots.json")));
            for(int i=0;i<names.length;i++){
                boolean found=false;
                for(var snapshot:snapshots)if(snapshot.get("stage").asText().equals(names[i])){
                    double clock=snapshot.get("time").doubleValue();
                    referenceEvents[i]=clock;
                    events[i]=Math.rint(clock/step)*step;assertEquals(clock,events[i],1e-5);found=true;break;
                }
                assertTrue(found,"Actual reference event time must be retained");
            }
            byte[] data=z.getInputStream(z.getEntry("a/native.csv")).readAllBytes();
            assertArrayEquals(data,z.getInputStream(z.getEntry("b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(data);
            raw=new ArrayList<>(new String(z.getInputStream(z.getEntry("a/network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry("a/dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
        }
        verifyReferenceRecoveryClock(reference,referenceEvents,parameters.copySourceConstants(),step);
        var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f,events);recorder.recordInternalTransitions();
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(step);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        var solver=staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder);a.setSolver(solver);
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,events[0],events[1]-events[0]),"FirstFault");
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,reactance),null,events[2],events[3]-events[2]),"SecondFault");
        assertTrue(a.initialization());a.setSimuTime(-2*step);recorder.enabled=true;
        double[] history=new double[23],boundary=new double[8];int blockedSamples=0;double lowestVoltage=1;
        while(a.getSimuTime()<.5-step/2){
            assertTrue(a.solveDEqnStep(true),"Repeated-fault network at "+a.getSimuTime());
            assertTrue(solver.getLastNetworkSolutionDiagnostics().iterations()<=50);
            f.protection().readAcceptedHistory(history,0);assertEquals(a.getSimuTime(),history[0],1e-12);
            if(a.getSimuTime()>events[2]+2*step&&a.getSimuTime()<events[3]){
                assertEquals(OperatingMode.BLOCKED,f.protection().operatingMode());blockedSamples++;
                f.ports().rectifierPort().getOutputObject();f.runtime().readBoundary(boundary,0);
                assertEquals(0,boundary[0],0);assertEquals(0,boundary[3],0);
                lowestVoltage=Math.min(lowestVoltage,n.getBus("Bus7").getVoltageMag());
            }
        }
        assertTrue(blockedSamples>80);if(profile.equals("nearzero"))assertTrue(lowestVoltage<.01);
        assertEquals(OperatingMode.RECOVERING,f.protection().operatingMode());recorder.capture(a.getSimuTime());
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-deep-recovery")),profile+"-"+(staggered?"staggered-":"euler-"));
        double[] maximum=Cdc6tProtectedNetworkTest.writeComparison(out,reference,recorder.rows,step);
        // Gate complete terminal trajectories, including both sides of every event.
        assertTrue(maximum[0]<(staggered?1.7e-5:3.2e-5),"Full rectifier voltage error "+maximum[0]);
        assertTrue(maximum[1]<(staggered?.0012:.0018),"Full rectifier angle error "+maximum[1]);
        assertTrue(maximum[2]<(staggered?1.3e-5:1.7e-5),"Full inverter voltage error "+maximum[2]);
        assertTrue(maximum[3]<(staggered?.0027:.0035),"Full inverter angle error "+maximum[3]);
        int checked=0;
        for(var error:Tiocr1ResetReferenceTest.csv(Files.readAllBytes(out.resolve("reference-row-errors.csv")))){
            double time=error.get("reference_time");
            if(time<=events[2]+2*step||time>=events[3]-2*step)continue;
            checked++;
            assertTrue(Math.abs(error.get("V7"))<1.2e-8);assertTrue(Math.abs(error.get("V9"))<8e-6);
            assertEquals(0,error.get("VAR5"),0);assertEquals(0,error.get("VAR7"),0);
            assertTrue(Math.abs(error.get("STATE0"))<(staggered?3:42));
            assertTrue(Math.abs(error.get("STATE1"))<(staggered?.003:.06));
        }
        assertTrue(checked>=90,"Independent fault-on samples must remain present");
        // Full measurement-state and converter-output qualification remains open.
    }

    /** Independent timer channels distinguish observation phase from accumulated clock roundoff. */
    private static void verifyReferenceRecoveryClock(List<Map<String,Double>> rows,double[] events,double[] constants,double step){
        double applied=events[2],cleared=events[3];
        var blocked=rows.stream().filter(r->r.get("time_s")>=applied&&r.get("VAR11")==0).findFirst().orElseThrow();
        assertEquals(applied-step/2+constants[6],blocked.get("VAR1"),3e-8,
                "Minimum blocking deadline uses the preceding midpoint");
        var timer=rows.stream().filter(r->r.get("time_s")>cleared&&r.get("VAR15")<90000).findFirst().orElseThrow();
        assertEquals(cleared+step,timer.get("time_s"),3e-8,
                "Recovery timer is first published at the next endpoint");
        double deadline=timer.get("VAR15");
        assertEquals(cleared+step/2+constants[24],deadline,3e-8,
                "Recovery deadline starts at the following midpoint, not at clearing");
        assertTrue(deadline-(cleared+constants[24])>step*.49,
                "The phase difference is much larger than reference-clock roundoff");
        var release=rows.stream().filter(r->r.get("time_s")>cleared&&r.get("VAR11")>0).findFirst().orElseThrow();
        double time=release.get("time_s");
        assertTrue(time>=deadline&&time-step<deadline,"Release occurs on the first recorded endpoint after the deadline");
        var sides=rows.stream().filter(r->r.get("time_s")==time).toList();
        assertEquals(2,sides.size(),"Retain both actual release observations");
        assertEquals(0,sides.get(0).get("VAR11"),0);assertTrue(sides.get(1).get("VAR11")>0);
        for(String state:List.of("STATE0","STATE1","STATE2"))assertEquals(sides.get(0).get(state),sides.get(1).get(state),0,
                "Release does not advance differential storage between event sides");
    }
}
