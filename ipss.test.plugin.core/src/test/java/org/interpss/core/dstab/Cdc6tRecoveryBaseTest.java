package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import com.interpss.dstab.hvdc.model.*;
import com.interpss.dstab.hvdc.model.Cdc6tNormalRuntime.OperatingMode;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Independent recovery-base discriminators after static-to-dynamic control transfer. */
public class Cdc6tRecoveryBaseTest extends CorePluginTestSetup {
    @TempDir Path folder;
    record Capture(Cdc6tCoupledNetworkTest.Fixture fixture,List<Map<String,Double>> rows) {}
    Capture load(String mode)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-transfer-recovery-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent recovery captures");
        assertEquals("61767d749282ba5c1c6399e4fb55e4f6717ae5188bbcbfcb29fd225ca52f62dd",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var zip=new ZipFile(archive.toFile())){
            String prefix=mode+"/a/";
            byte[] bytes=zip.getInputStream(zip.getEntry(prefix+"native.csv")).readAllBytes();
            assertArrayEquals(bytes,zip.getInputStream(zip.getEntry(mode+"/b/native.csv")).readAllBytes());
            var raw=new ArrayList<>(new String(zip.getInputStream(zip.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(zip.getInputStream(zip.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            var parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
            return new Capture(Cdc6tCoupledNetworkTest.load(folder,raw,parameters),Tiocr1ResetReferenceTest.csv(bytes));
        }
    }
    @ParameterizedTest @ValueSource(strings={"current","inverter-power","rectifier-power"})
    void everyRecordedBoundaryUsesScheduledRecoveryBase(String mode)throws Exception {
        var capture=load(mode);var runtime=capture.fixture().runtime();
        double[] state=new double[3],boundary=new double[8],currents=new double[4],maximum=new double[8];
        String[] keys={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13","VAR10","VAR9"};
        double nominal=mode.equals("rectifier-power")?1010:1000;
        int recovering=0,blocked=0;double currentError=0;
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-recovery-base")),"replay-"+mode+"-");
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("trajectory.csv"),2*1024*1024)){
            writer.write("time_s,"+String.join(",",keys)+"\n");
            for(var row:capture.rows()){
                for(int i=0;i<3;i++)state[i]=row.get("STATE"+i);
                runtime.applyStaggeredState(state,0,0);
                var regime=row.get("VAR11")==0?OperatingMode.BLOCKED
                        :row.get("VAR2")<1||row.get("VAR3")<1?OperatingMode.RECOVERING:OperatingMode.NORMAL;
                if(regime==OperatingMode.BLOCKED)blocked++;
                if(regime==OperatingMode.RECOVERING){
                    recovering++;
                    double margin=mode.equals("current")&&row.get("VAR12")==5?.9:1;
                    currentError=Math.max(currentError,Math.abs(row.get("VAR11")-nominal*row.get("VAR3")*margin));
                }
                double ar=Math.toRadians(row.get("A7")),ai=Math.toRadians(row.get("A9"));
                runtime.evaluateBoundary(row.get("V7")*Math.cos(ar),row.get("V7")*Math.sin(ar),
                        row.get("V9")*Math.cos(ai),row.get("V9")*Math.sin(ai),0,regime,
                        regime==OperatingMode.RECOVERING?row.get("VAR2"):1,
                        regime==OperatingMode.RECOVERING?row.get("VAR3"):1,currents,0);
                runtime.readBoundary(boundary,0);writer.write(Double.toString(row.get("time_s")));
                for(int i=0;i<8;i++){
                    double value=i==2||i==5?Math.toDegrees(boundary[i]):boundary[i];
                    maximum[i]=Math.max(maximum[i],Math.abs(value-row.get(keys[i])));writer.write(","+value);
                }
                writer.write("\n");
            }
        }
        System.out.println("CDC6T recovery-base replay "+mode+" "+Arrays.toString(maximum)+" current error="+currentError);
        assertEquals(1006,capture.rows().size());assertTrue(recovering>400);assertTrue(blocked>20);
        assertTrue(currentError<.0001,"Independent current/factor ratio establishes the nominal base");
        double[] limits={3.6e-6,9e-6,7e-5,3.4e-6,3.2e-6,6e-5,.33,.32};
        for(int i=0;i<8;i++)assertTrue(maximum[i]<limits[i],keys[i]+" error="+maximum[i]);
    }
    @ParameterizedTest @ValueSource(strings={"current","inverter-power","rectifier-power"})
    void preparedCoordinatorUsesTheSameRecoveryBaseAndResetsWithoutClockDrift(String mode)throws Exception {
        var capture=load(mode);var protection=capture.fixture().protection();
        double nominal=mode.equals("rectifier-power")?1010:1000;
        for(int repetition=0;repetition<2;repetition++){
            protection.reset();
            protection.beginTrial(0);protection.observe(0,1,0);protection.acceptTrial();
            assertEquals(OperatingMode.BLOCKED,protection.operatingMode());
            protection.beginTrial(.1);protection.observe(1,1,0);protection.acceptTrial();
            protection.beginTrial(.12);protection.observe(1,1,0);protection.acceptTrial();
            assertEquals(OperatingMode.RECOVERING,protection.operatingMode());
            assertEquals(500/nominal,protection.currentFactor(),1e-12);
            double[] before=new double[23],after=new double[23];protection.readAcceptedHistory(before,0);
            protection.beginTrial(.15);protection.observe(1,1,500000);
            assertEquals(500/nominal+.06,protection.currentFactor(),1e-12);
            protection.discardTrial();protection.readAcceptedHistory(after,0);assertArrayEquals(before,after,0);
        }
    }
    static java.util.stream.Stream<Arguments> coupledProfiles(){
        return java.util.stream.Stream.of("current","inverter-power","rectifier-power")
                .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @MethodSource("coupledProfiles")
    void coupledProtectedTransferSharesTheRecoveryBaseAndAcceptedClock(String mode,boolean staggered)throws Exception {
        var capture=load(mode);var f=capture.fixture();var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f);double step=.0005;
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(step);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        var solver=staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder);
        a.setSolver(solver);
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"TerminalFault");
        assertTrue(a.initialization());a.setSimuTime(-2*step);recorder.enabled=true;
        double nominal=mode.equals("rectifier-power")?1010:1000;
        double[] history=new double[23],boundary=new double[8],state=new double[3],rates=new double[3];
        int recovery=0,bound=0;double firstBlock=Double.NaN,firstRecovery=Double.NaN;
        while(a.getSimuTime()<.5-step/2){
            assertTrue(a.solveDEqnStep(true));double time=a.getSimuTime();
            assertTrue(solver.getLastNetworkSolutionDiagnostics().iterations()<=50,"Control actions share the existing network budget");
            f.protection().readAcceptedHistory(history,0);assertEquals(time,history[0],1e-12);
            f.ports().rectifierPort().getOutputObject();f.runtime().readBoundary(boundary,0);
            if(f.protection().operatingMode()==OperatingMode.BLOCKED&&Double.isNaN(firstBlock))firstBlock=time;
            if(f.protection().operatingMode()==OperatingMode.RECOVERING){
                if(Double.isNaN(firstRecovery))firstRecovery=time;
                recovery++;
                double current=boundary[0]*1e8/boundary[6];
                double fraction=mode.equals("current")&&Math.abs(Math.toDegrees(boundary[2])-5)<1e-8?.9:1;
                if(fraction<1)bound++;
                assertEquals(nominal*f.protection().currentFactor()*fraction,current,1e-8);
                f.runtime().readStaggeredState(state,0);f.runtime().evaluateStaggered(step,rates,0);
                assertEquals((current-state[1])/.05,rates[1],1e-7,"Delivered current must drive the measurement derivative");
            }
        }
        recorder.capture(a.getSimuTime());assertTrue(recovery>400);if(mode.equals("current"))assertTrue(bound>20);
        // This observation is taken after solveDEqnStep advances the public clock.
        // The complete recorder below retains the event-side network observations.
        assertEquals(.05+step,firstBlock,1e-10);assertEquals(.07,firstRecovery,1.1*step);
        assertEquals(OperatingMode.NORMAL,f.protection().operatingMode());
        Path out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-recovery-base")),mode+"-"+(staggered?"staggered-":"euler-"));
        double[] maximum=Cdc6tProtectedNetworkTest.writeComparison(out,capture.rows(),recorder.rows,step);
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("maxima.csv"),16384)){
            writer.write("channel,max_absolute\n");
            for(int i=0;i<maximum.length;i++)writer.write(Cdc6tCoupledReferenceTest.KEYS[i]+","+maximum[i]+"\n");
        }
        // Complete protected trajectories remain diagnostic until event-side
        // observation and finite-stage state discrepancies are qualified.
    }
}
