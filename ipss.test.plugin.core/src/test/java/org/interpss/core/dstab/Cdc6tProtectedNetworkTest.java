package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import com.interpss.dstab.hvdc.model.*;
import com.interpss.dstab.hvdc.model.Cdc6tNormalRuntime.OperatingMode;
import org.interpss.fadapter.psse.dyr.Cdc6tRecordMapper;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Complete protected network lifecycle. Full independent state/trajectory acceptance remains a separate gate. */
public class Cdc6tProtectedNetworkTest extends CorePluginTestSetup {
    @TempDir Path folder;
    static java.util.stream.Stream<Arguments> profiles(){return java.util.stream.Stream.of("inverter","delay","rectifier","inverter-half","floor200","floor400","negative-recovery","zero-recovery")
            .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));}
    @ParameterizedTest @MethodSource("profiles")
    void protectedFaultCompletesWithOneAcceptedClock(String profile,boolean staggered)throws Exception {
        boolean half=profile.endsWith("-half");double step=half?.00025:.0005;
        boolean floor=profile.startsWith("floor");double minimum=profile.equals("floor200")?200:400;
        boolean sentinel=profile.endsWith("-recovery"),rectifierFault=profile.equals("rectifier")||sentinel;
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-"+(sentinel?"recovery-sentinel":floor?"minimum-current":"coordination"+(half?"-halfstep":""))+"-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent coordinated network inputs");
        assertEquals(sentinel?"1655d2dda780170d4a48bafa5abaabde4c2fb68d991ece298ce9ec52bfce9aef":floor?"9634c30bc479897165d89e1bf97a9fba3c11881614dd7f6d3035eec6258c0639":half?"8137239dd9529a6a9e8d34661ba7050a82e4f9b63b5106bf7bf187d1447a8d29":"40a91608fe41d356c3c6c108da7d548e1d9e530e1d32af70ee47700ee518f6f9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
        try(var z=new ZipFile(archive.toFile())){
            String prefix=half?"a/":profile.replace("-recovery","")+"/a/";
            raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList());
            reference=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")),java.nio.charset.StandardCharsets.UTF_8),"reference/dynamic.dyr");
            var dc=records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).toList();assertEquals(1,dc.size());
            parameters=Cdc6tRecordMapper.prepare(dc.get(0));
        }
        var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);var coordinator=f.protection();
        var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(step);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f);
        a.setSolver(staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder));
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(rectifierFault?"Bus7":"Bus9",n,
                SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"TerminalFault");
        assertTrue(a.initialization());a.setSimuTime(-2*step);recorder.enabled=true;
        var out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-protected-network")),profile+"-"+(staggered?"staggered-":"euler-"));
        double[] history=new double[23],states=new double[3],boundary=new double[8];
        double firstBlock=Double.NaN,firstBypass=Double.NaN,firstRecovery=Double.NaN;
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"),2*1024*1024)){
            writer.write("time_s,mode,accepted_time,V7,V9,STATE0,STATE1,STATE2,Pr,Qr,alpha,Pi,Qi,gamma,Vdr,Vdi\n");
            while(a.getSimuTime()<.5-.5*step){
                assertTrue(a.solveDEqnStep(true),"Protected solve at "+a.getSimuTime());
                double t=a.getSimuTime();coordinator.readAcceptedHistory(history,0);
                assertEquals(t,history[0],1e-12,"One accepted endpoint clock under either solver");
                var mode=coordinator.operatingMode();
                if(mode==OperatingMode.BLOCKED&&Double.isNaN(firstBlock))firstBlock=t;
                if(mode==OperatingMode.BYPASSED&&Double.isNaN(firstBypass))firstBypass=t;
                if(mode==OperatingMode.RECOVERING&&Double.isNaN(firstRecovery))firstRecovery=t;
                f.ports().rectifierPort().getOutputObject();f.runtime().readStaggeredState(states,0);f.runtime().readBoundary(boundary,0);
                if(staggered&&!rectifierFault&&Math.abs(t-firstBlock-step)<1e-10)
                    assertEquals(1000*(1-.75*step/.05),states[1],1e-6,"Internal blocking uses the independently observed discontinuity treatment once");
                for(double value:states)assertTrue(Double.isFinite(value));for(double value:boundary)assertTrue(Double.isFinite(value));
                if(mode==OperatingMode.BLOCKED){assertEquals(0,boundary[0],0);assertEquals(0,boundary[3],0);}
                if(floor&&mode==OperatingMode.RECOVERING&&history[5]*1000<minimum)
                    assertEquals(minimum,boundary[0]*1e8/boundary[6],1e-8,"Recovery cannot bypass the minimum current demand");
                writer.write(t+","+mode+","+history[0]+","+n.getBus("Bus7").getVoltageMag()+","+n.getBus("Bus9").getVoltageMag());
                for(double value:states)writer.write(","+value);for(double value:boundary)writer.write(","+value);writer.write("\n");
            }
        }
        recorder.capture(a.getSimuTime());
        writeComparison(out,reference,recorder.rows,step);
        System.out.println("CDC6T protected "+profile+" staggered="+staggered+" bypass/block/recovery="+firstBypass+"/"+firstBlock+"/"+firstRecovery);
        assertEquals(rectifierFault?.05:.07,firstBlock,2.2*step);
        if(!rectifierFault)assertEquals(.05,firstBypass,2.2*step);
        // A release attempt can reblock at the same time while retaining ramp factors.
        // Require conducting power as well as a partial ramp to identify actual recovery.
        double referenceRecovery=reference.stream().filter(row->row.get("VAR2")<1&&row.get("VAR2")>0&&row.get("VAR5")>0&&row.get("VAR7")!=0)
                .mapToDouble(row->row.get("time_s")).min().orElseThrow();
        assertEquals(referenceRecovery,firstRecovery,2.2*step,"Release within two network intervals of independent capture");
        assertEquals(floor?OperatingMode.RECOVERING:OperatingMode.NORMAL,coordinator.operatingMode());
    }

    /** Diagnostic only: preserve every reference row, including internal transitions and inspection duplicates.
     * An internal reference transition has no synthetic pre-transition sample added to our integration grid. */
    static double[] writeComparison(Path out,List<Map<String,Double>> reference,List<double[]> actual,double step)throws Exception{
        return writeComparison(out,reference,actual,step,1e-5);
    }
    /** Explicit long-run floating-clock bound; never changes either trajectory's timestamps. */
    static double[] writeComparison(Path out,List<Map<String,Double>> reference,List<double[]> actual,double step,double clockTolerance)throws Exception{
        assertTrue(Double.isFinite(clockTolerance)&&clockTolerance>0&&clockTolerance<step/2,
                "Clock bound must still distinguish adjacent integration intervals");
        String[] keys=Cdc6tCoupledReferenceTest.KEYS;double[] maxima=new double[keys.length];
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("trajectory.csv"),2*1024*1024)){
            writer.write("time_s,"+String.join(",",keys)+"\n");
            for(var row:actual){for(int j=0;j<row.length;j++){if(j>0)writer.write(",");writer.write(Double.toString(row[j]));}writer.write("\n");}
        }
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("reference-row-errors.csv"),2*1024*1024)){
            writer.write("reference_row,reference_time,actual_row,actual_time,"+String.join(",",keys)+"\n");
            int cursor=0;
            for(int index=0;index<reference.size();index++){
                var row=reference.get(index);double t=row.get("time_s");
                while(cursor+1<actual.size()&&Math.abs(actual.get(cursor+1)[0]-t)<Math.abs(actual.get(cursor)[0]-t))cursor++;
                int selected=cursor;
                boolean lastSide=index+1==reference.size()||reference.get(index+1).get("time_s")!=t;
                if(lastSide)while(selected+1<actual.size()&&actual.get(selected+1)[0]==actual.get(selected)[0])selected++;
                var observed=actual.get(selected);
                // Retained single-precision reference clocks accumulate up to 9.36 microseconds
                // over the half-step run. Keep both original times; never shift a trajectory.
                assertEquals(Math.round(t/step),Math.round(observed[0]/step),"Same integration interval");
                assertEquals(t,observed[0],clockTolerance,"Bounded floating-clock departure");
                writer.write(index+","+t+","+selected+","+observed[0]);
                for(int j=0;j<keys.length;j++){
                    double error=observed[j+1]-row.get(keys[j]);assertTrue(Double.isFinite(error));
                    maxima[j]=Math.max(maxima[j],Math.abs(error));writer.write(","+error);
                }
                writer.write("\n");cursor=selected;
            }
        }
        System.out.println("CDC6T protected diagnostic maxima "+out.getFileName()+" "+Arrays.toString(maxima));
        return maxima;
    }
}
