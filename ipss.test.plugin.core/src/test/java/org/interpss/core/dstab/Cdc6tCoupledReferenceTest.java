package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.hvdc.Cdc6tParameters;
import org.interpss.fadapter.psse.dyr.Cdc6tRecordMapper;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Independent normal-conduction fault comparison, retaining startup and both event sides. */
public class Cdc6tCoupledReferenceTest extends CorePluginTestSetup {
    @TempDir Path folder;
    private static final Path OUTPUT=createOutput();
    private static Path createOutput(){
        try{return Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-coupled-reference")),"run-");}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    static final String[] KEYS={"V7","A7","V9","A9","STATE0","STATE1","STATE2",
            "VAR5","VAR6","VAR12","VAR7","VAR8","VAR13","VAR10","VAR9"};
    static final class Recorder {
        final Cdc6tCoupledNetworkTest.Fixture f;
        final List<double[]> rows=new ArrayList<>();
        final double[] eventTimes;
        boolean enabled;
        Recorder(Cdc6tCoupledNetworkTest.Fixture f){this(f,.05,.1);}
        Recorder(Cdc6tCoupledNetworkTest.Fixture f,double... eventTimes){this.f=f;this.eventTimes=eventTimes.clone();}
        void before(double t){if(enabled)for(double event:eventTimes)if(Math.abs(t-event)<1e-9){capture(t);break;}}
        void capture(double t){
            var n=f.context().getDStabilityNet();var vr=n.getBus("Bus7").getVoltage();var vi=n.getBus("Bus9").getVoltage();
            double[] state=new double[3],boundary=new double[8];
            f.ports().rectifierPort().getOutputObject();
            f.runtime().readStaggeredState(state,0);f.runtime().readBoundary(boundary,0);
            rows.add(new double[]{t,vr.abs(),Math.toDegrees(vr.getArgument()),vi.abs(),Math.toDegrees(vi.getArgument()),
                    state[0],state[1],state[2],boundary[0],boundary[1],Math.toDegrees(boundary[2]),
                    boundary[3],boundary[4],Math.toDegrees(boundary[5]),boundary[6],boundary[7]});
        }
    }
    static final class Ordinary extends DefaultDynamicSimulationSolver {
        final Recorder r;
        Ordinary(Recorder r){super(r.f.context().getDynSimuAlgorithm());this.r=r;converge_tol=1e-6;}
        @Override public void beforeStep(double t)throws DStabSimuException{r.before(t);super.beforeStep(t);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{
            boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage))r.capture(dstabAlgo.getSimuTime());return ok;
        }
    }
    static final class Shared extends StaggeredEulerSolver {
        final Recorder r;
        Shared(Recorder r){super(r.f.context().getDynSimuAlgorithm());this.r=r;converge_tol=1e-6;setReuseNetworkEndpoint(false);}
        @Override public void beforeStep(double t)throws DStabSimuException{r.before(t);super.beforeStep(t);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{
            boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage))r.capture(dstabAlgo.getSimuTime());return ok;
        }
    }
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("reference:fault","current-order:inverter-power","current-order:rectifier-power",
                "current-order:current","vdcl:equal-slope-fault","vdcl:two-slope-fault","inverter-fault:inverter-power")
                .flatMap(p->java.util.stream.Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void normalFaultMatchesIndependentTerminalAndStateTrajectories(String profile,boolean staggered)throws Exception {
        String group=profile.split(":")[0],name=profile.split(":")[1];
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-"+group+"-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent CDC6T captures");
        String hash=switch(group){
            case "current-order" -> "6d2fe324463db46be0fa0f6fd3f69aa928cb687a527b80ff8236ea1ea307a400";
            case "vdcl" -> "0afeb5bb1968ce9048b86daa4d3061a9a54ef2505424a8824acc89c7b916416d";
            case "inverter-fault" -> "7e18c3851254b3c1b705cc8eafc1a589cd9828cd85a26f34a4c484d421cb2628";
            default -> "69a1ee22d4d9e6426a7228cd96f457fe982a4b988810a7a9e580b7cbba4c8022";
        };
        assertEquals(hash,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        List<Map<String,Double>> reference;List<String> raw;Cdc6tParameters parameters;
        try(var z=new ZipFile(archive.toFile())){
            String prefix=(group.equals("inverter-fault")?"":name+"/")+"a/";
            reference=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes());
            raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")),java.nio.charset.StandardCharsets.UTF_8),"reference/dynamic.dyr");
            var dc=records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).toList();assertEquals(1,dc.size());
            parameters=Cdc6tRecordMapper.prepare(dc.get(0));
        }
        var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);
        var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var recorder=new Recorder(f);
        a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.5);a.setSimuOutputHandler(new StateMonitor());
        a.setSolver(staggered?new Shared(recorder):new Ordinary(recorder));
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(group.equals("inverter-fault")?"Bus9":"Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"TerminalFault");
        assertTrue(a.initialization());a.setSimuTime(-.001);recorder.enabled=true;
        while(a.getSimuTime()<.5-.00025)assertTrue(a.solveDEqnStep(true));
        recorder.capture(a.getSimuTime());assertEquals(1005,reference.size());assertEquals(reference.size(),recorder.rows.size());
        double[] max=new double[KEYS.length],times=new double[KEYS.length];int duplicates=0;
        for(int i=0;i<reference.size();i++){
            var expected=reference.get(i);var actual=recorder.rows.get(i);
            assertEquals(expected.get("time_s"),actual[0],5e-6,"Same integration grid and event side");
            if(i>0&&actual[0]==recorder.rows.get(i-1)[0])duplicates++;
            for(int j=0;j<KEYS.length;j++){
                double error=Math.abs(expected.get(KEYS[j])-actual[j+1]);assertTrue(Double.isFinite(error));
                if(error>max[j]){max[j]=error;times[j]=actual[0];}
            }
        }
        assertEquals(2,duplicates);
        for(int j=0;j<KEYS.length;j++)System.out.println("CDC6T coupled "+profile+" staggered="+staggered+" "+KEYS[j]+" max="+max[j]+" time="+times[j]);
        // Separate measured envelopes: finite-stage event response differs between the two methods.
        double[] tolerance=staggered
                ?new double[]{4.2e-6,.00046,4e-6,.0017,1.8,.0085,1.7,.000115,.00006,.00052,.00011,.00008,.00082,2.9,2.9}
                :new double[]{.00014,.0085,.000008,.0075,125,.031,125,.004,.0007,.022,.004,.0023,.004,10,10};
        var out=OUTPUT.resolve(profile.replace(':','-')).resolve(staggered?"staggered":"modified-euler");
        Files.createDirectories(out);
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"),2*1024*1024)){
            writer.write("time_s,"+String.join(",",KEYS)+"\n");
            for(var row:recorder.rows){for(int i=0;i<row.length;i++){if(i>0)writer.write(",");writer.write(Double.toString(row[i]));}writer.write("\n");}
        }
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("errors.csv"),16384)){
            writer.write("channel,max_absolute,time_s,limit\n");
            for(int j=0;j<KEYS.length;j++)writer.write(KEYS[j]+","+max[j]+","+times[j]+","+tolerance[j]+"\n");
        }
        for(int j=0;j<KEYS.length;j++)assertTrue(max[j]<tolerance[j],KEYS[j]+" maximum error "+max[j]+" at "+times[j]);
    }
}
