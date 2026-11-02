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

/** Independent fixed-current regimes after a nontrivial static-to-dynamic transfer. */
public class Cdc6tTransferredModeBaseTest extends CorePluginTestSetup {
    @TempDir Path folder;
    record Capture(Cdc6tCoupledNetworkTest.Fixture fixture,List<Map<String,Double>> rows) {}
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("bypass/current","bypass/inverter-power","bypass/rectifier-power",
                "switch/inverter-power","switch/rectifier-power").map(Arguments::of);
    }
    static java.util.stream.Stream<Arguments> coupledProfiles(){
        return profiles().flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p.get()[0],s)));
    }
    Capture load(String profile)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-transferred-modes-20261102/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent transferred-mode captures");
        assertEquals("2989ff0be244dad9a96b05cda8c275643414f6e738a7ee6ba1a66e20a9fc397d",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var z=new ZipFile(archive.toFile())){
            String prefix=profile+"/a/";
            byte[] data=z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes();
            assertArrayEquals(data,z.getInputStream(z.getEntry(profile+"/b/native.csv")).readAllBytes());
            var raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            var parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
            return new Capture(Cdc6tCoupledNetworkTest.load(folder,raw,parameters),Tiocr1ResetReferenceTest.csv(data));
        }
    }
    static boolean active(String profile,Map<String,Double> row){
        return profile.startsWith("bypass/")?row.get("VAR9")==0&&row.get("VAR11")>0:row.get("VAR14")<90000;
    }
    static double nominal(String profile){return profile.endsWith("rectifier-power")?1010:1000;}
    static OperatingMode mode(String profile){return profile.startsWith("bypass/")?OperatingMode.BYPASSED:OperatingMode.POWER_TO_CURRENT;}

    @ParameterizedTest @MethodSource("profiles")
    void allActiveReferenceSamplesUseTheSourceBase(String profile)throws Exception {
        var capture=load(profile);var runtime=capture.fixture().runtime();
        double[] state=new double[3],boundary=new double[8],currents=new double[4],maximum=new double[8],rates=new double[3];
        String[] keys={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13","VAR10","VAR9"};
        int samples=0;
        for(var row:capture.rows()){
            if(!active(profile,row))continue;
            samples++;assertEquals(nominal(profile),row.get("VAR11"),.0001,"Independent fixed-current base");
            for(int i=0;i<3;i++)state[i]=row.get("STATE"+i);
            runtime.applyStaggeredState(state,0,0);
            double ar=Math.toRadians(row.get("A7")),ai=Math.toRadians(row.get("A9"));
            runtime.evaluateBoundary(row.get("V7")*Math.cos(ar),row.get("V7")*Math.sin(ar),
                    row.get("V9")*Math.cos(ai),row.get("V9")*Math.sin(ai),0,mode(profile),1,1,currents,0);
            runtime.readBoundary(boundary,0);runtime.evaluateStaggered(.0005,rates,0);
            assertEquals((nominal(profile)-state[1])/.05,rates[1],1e-7,"Delivered base drives current measurement");
            for(int i=0;i<8;i++)maximum[i]=Math.max(maximum[i],Math.abs((i==2||i==5?Math.toDegrees(boundary[i]):boundary[i])-row.get(keys[i])));
        }
        assertTrue(samples>=90,"Exercise the complete active interval");
        System.out.println("CDC6T transferred mode replay "+profile+" "+Arrays.toString(maximum));
        // Separate envelopes for the independently measured bypass and conducting
        // boundaries. The small bypass Q residual is retained, not fitted away;
        // earlier profiles keep their existing stricter gates.
        double[] limits=profile.startsWith("bypass/")
                ?new double[]{6e-9,9e-5,4e-5,1e-12,1e-12,1e-12,1e-8,1e-8}
                :new double[]{2e-6,2e-6,1.7e-5,1.7e-6,1.8e-6,1e-10,.22,.22};
        for(int i=0;i<8;i++)assertTrue(maximum[i]<limits[i],keys[i]+" error="+maximum[i]);
    }

    @ParameterizedTest @MethodSource("coupledProfiles")
    void bothMethodsRetainSourceBaseAfterStartupSettling(String profile,boolean staggered)throws Exception {
        var capture=load(profile);var f=capture.fixture();var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f);double step=.0005;
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(step);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        var solver=staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder);
        a.setSolver(solver);
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus9",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"TerminalFault");
        assertTrue(a.initialization());a.setSimuTime(-2*step);recorder.enabled=true;
        double[] history=new double[23],boundary=new double[8];int activeSamples=0;
        while(a.getSimuTime()<.5-step/2){
            assertTrue(a.solveDEqnStep(true));
            assertTrue(solver.getLastNetworkSolutionDiagnostics().iterations()<=50);
            f.protection().readAcceptedHistory(history,0);assertEquals(a.getSimuTime(),history[0],1e-12);
            f.ports().rectifierPort().getOutputObject();f.runtime().readBoundary(boundary,0);
            if(f.protection().operatingMode()==mode(profile)){
                activeSamples++;
                assertEquals(nominal(profile),boundary[0]*1e8/boundary[6],1e-8);
                if(mode(profile)==OperatingMode.BYPASSED){assertEquals(0,boundary[3],0);assertEquals(0,boundary[4],0);}
            }
        }
        assertTrue(activeSamples>=90);assertEquals(OperatingMode.NORMAL,f.protection().operatingMode());
        recorder.capture(a.getSimuTime());
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-transferred-modes")),profile.replace('/','-')+"-"+(staggered?"staggered-":"euler-"));
        double[] maximum=Cdc6tProtectedNetworkTest.writeComparison(out,capture.rows(),recorder.rows,step);
        if(profile.startsWith("switch/")){
            assertEquals(capture.rows().size(),recorder.rows.size(),"Complete trajectory and scheduled event sides");
            double[] limits=staggered
                    ?new double[]{1.6e-6,.00006,2.6e-6,.0011,2.6,.0038,2.6,.000018,.000029,.00058,.000018,.000012,1e-10,1.4,1.4}
                    :new double[]{.000056,.0048,.0000095,.0054,205,.28,205,.0022,.000047,.016,.0022,.00115,1e-10,2.9,4.8};
            for(int i=0;i<limits.length;i++)assertTrue(maximum[i]<limits[i],Cdc6tCoupledReferenceTest.KEYS[i]+" full-trajectory error="+maximum[i]);
        }
        // Bypass/recovery trajectories remain diagnostic pending internal-event
        // observations and voltage-state qualification; source-base checks above pass.
    }
}
