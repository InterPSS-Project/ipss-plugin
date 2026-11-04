package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.hvdc.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Full network fault comparison through typed ports; preserve failed gates and every event side. */
public class Chvdc2TerminalReferenceTest extends CorePluginTestSetup {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-controller-20261102/captures.zip");
    private static final double[] LIMITS={1e-5,1e-5,.01,.01,1e-4,1e-4,1e-4,1e-4,.002,.002,
            1e-5,1e-5,1e-5,1e-5,.002,.002,.002,.002,1e-5,1e-5,1e-5,1e-5,1e-5,1e-5,.002,.01};
    @TempDir Path folder;
    @BeforeAll static void integrity()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent coupled reference captures");
        assertEquals("75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
    }
    static Stream<Arguments> profiles(){return Stream.of("0p0005","0p00025").flatMap(h->Stream.of(false,true).map(s->Arguments.of(h,s)));}
    static Stream<Arguments> protectedProfiles(){
        var raml=Stream.of("0p0005","0p00025").map(h->new String[]{"chvdc2-converter-20261102/protection-captures.zip",
                "5245279e56e57373bdd8afc23fc5e033b73aef0b5e667f41a9d50c4e7ae53b16",
                "chvdc2-protection-20261102/stiff-legacy-rectifier-fault-flags011-h"+h+"-raml-a"});
        var commutation=Stream.of(new String[][]{{"chvdc2-protection-20261102/captures.zip",
                "32079591003689230139fa893b5c3e07a315dc4d411442372838e10da3b658ea",
                "chvdc2-protection-short-hold-20261102/stiff-legacy-inverter-fault-flags001-h0p0005-gamma12-faultx0p02-netiter50-cfhold0p005-a"}});
        return Stream.concat(raml,commutation).flatMap(p->Stream.of(false,true).map(s->Arguments.of(p[0],p[1],p[2],s)));
    }
    private static final class Recorder {
        final com.interpss.dstab.algo.DynamicSimuAlgorithm algorithm;
        final Chvdc2NetworkPreparation.Binding binding;final Chvdc2TerminalPorts ports;
        final List<double[]> rows=new ArrayList<>();boolean enabled;
        final List<Map<String,Double>> histories=new ArrayList<>();
        final double[] states=new double[16],boundary=new double[10];
        Recorder(com.interpss.dstab.algo.DynamicSimuAlgorithm a,Chvdc2NetworkPreparation.Binding b,Chvdc2TerminalPorts p){algorithm=a;binding=b;ports=p;}
        void before(double time){if(enabled&&(Math.abs(time-.05)<1e-9||Math.abs(time-.1)<1e-9))capture(time);}
        void capture(double time){
            var r=ports.rectifierPort().getDStabBus().getVoltage();var i=ports.inverterPort().getDStabBus().getVoltage();
            // Compare the actual published terminal injection, not a fresh electrical-history evaluation.
            var rp=r.multiply(((Complex)ports.rectifierPort().getOutputObject()).conjugate()).negate();
            var ip=i.multiply(((Complex)ports.inverterPort().getOutputObject()).conjugate()).negate();
            binding.runtime().readPublishedStates(states,0);binding.runtime().readBoundary(boundary,0);
            double[] row=new double[27];row[0]=time;row[1]=r.abs();row[2]=i.abs();row[3]=boundary[2];row[4]=boundary[3];
            row[5]=rp.getReal();row[6]=rp.getImaginary();row[7]=ip.getReal();row[8]=ip.getImaginary();row[9]=states[6];row[10]=states[7];
            System.arraycopy(states,0,row,11,16);rows.add(row);histories.add(binding.runtime().getIntegrationHistories());
        }
    }
    private static final class Ordinary extends DefaultDynamicSimulationSolver {
        final Recorder r;Ordinary(Recorder r){super(r.algorithm);this.r=r;converge_tol=1e-10;setMaxIterationTimes(50);}
        @Override public void beforeStep(double time)throws DStabSimuException{r.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage))r.capture(dstabAlgo.getSimuTime());return ok;}
    }
    private static final class Shared extends StaggeredEulerSolver {
        final Recorder r;Shared(Recorder r){super(r.algorithm);this.r=r;converge_tol=1e-10;setMaxIterationTimes(50);setReuseNetworkEndpoint(false);}
        @Override public void beforeStep(double time)throws DStabSimuException{r.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage))r.capture(dstabAlgo.getSimuTime());return ok;}
    }
    @ParameterizedTest @MethodSource("profiles")
    void completeAttachedNetworkFaultComparedAgainstIndependentCaptures(String interval,boolean staggered)throws Exception {
        String tag="stiff-legacy-inverter-fault-flags001-h"+interval+"-a";
        compare(ARCHIVE,tag,staggered,false);
    }
    @ParameterizedTest @MethodSource("protectedProfiles")
    void completeProtectedNetworkFaultComparedAgainstIndependentCaptures(String archive,String digest,String tag,boolean staggered)throws Exception {
        Path source=Path.of("testData/private/verification-artifacts").resolve(archive);
        Assumptions.assumeTrue(Files.isRegularFile(source),"Retain independent protected network captures");
        assertEquals(digest,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        compare(source,tag,staggered,true);
    }
    private void compare(Path archive,String tag,boolean staggered,boolean protectedProfile)throws Exception {
        List<Map<String,Double>> reference;com.fasterxml.jackson.databind.JsonNode manifest;
        Path raw=folder.resolve("network.raw"),dyr=folder.resolve("machines.dyr");
        try(var z=new ZipFile(archive.toFile())) {
            manifest=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
            assertEquals("complete",manifest.path("status").asText());assertTrue(manifest.path("initialization_and_network_valid").asBoolean());
            assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
            byte[] csv=z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes();
            assertArrayEquals(csv,z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
            reference=Tiocr1ResetReferenceTest.csv(csv);
            Files.write(raw,z.getInputStream(z.getEntry(tag+"/network.raw")).readAllBytes());
            String source=new String(z.getInputStream(z.getEntry(tag+"/dynamic.dyr")).readAllBytes(),StandardCharsets.UTF_8);
            var machines=source.lines().filter(line->line.contains("'GENCLS'")).toList();assertEquals(4,machines.size());Files.write(dyr,machines);
        }
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString(),dyr.toString());var a=context.getDynSimuAlgorithm();var n=a.getNetwork();
        var flow=a.getAclfAlgorithm();flow.setTolerance(1e-10);flow.setMaxIterations(80);flow.getLfAdjAlgo().setApplyAdjustAlgo(false);assertTrue(flow.loadflow());
        double h=manifest.path("settings").path("step").asDouble();
        var p=new Chvdc2Parameters("1",Chvdc2Parameters.Dialect.CHVDC2U1,
                JSON.convertValue(manifest.path("settings").path("flags"),int[].class),JSON.convertValue(manifest.path("entered_constants"),double[].class));
        var binding=Chvdc2NetworkPreparation.prepare(n,p,h,-2*h);var ports=new Chvdc2TerminalPorts(binding);ports.attach();
        var recorder=new Recorder(a,binding,ports);a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(h);a.setTotalSimuTimeSec(.5);
        a.setSimuOutputHandler(new StateMonitor());a.setSolver(staggered?new Shared(recorder):new Ordinary(recorder));
        String faultBus=manifest.path("settings").path("profile").asText().equals("rectifier-fault")?"Bus7":"Bus9";
        double reactance=manifest.path("settings").path("fault_reactance").asDouble(.2);
        n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(faultBus,n,SimpleFaultCode.GROUND_3P,new Complex(0,reactance),null,.05,.05),"TerminalFault");
        assertTrue(a.initialization());a.setSimuTime(-2*h);recorder.enabled=true;
        Exception stopped=null;
        try{while(a.getSimuTime()<.5-h/2)if(!a.solveDEqnStep(true))throw new IllegalStateException("Attached network step did not converge");recorder.capture(a.getSimuTime());}
        catch(Exception failure){stopped=failure;}
        Path output=Files.createTempDirectory(Files.createDirectories(Path.of("../target/chvdc2-terminal-reference")),staggered?"staggered-":"euler-");
        double[] maximum=new double[26],times=new double[26];int duplicates=0,step=0;
        float referenceClock=(float)(-2*h);
        var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
        try(var w=BenchmarkOutputGuard.newWriter(output.resolve("comparison.csv"),2*1024*1024)) {
            w.write("time_s,reference_time_s");for(int k=0;k<26;k++)w.write(",actual_"+k+",reference_"+k+",error_"+k);w.write("\n");
            for(int row=0;row<recorder.rows.size();row++) {
                var actual=recorder.rows.get(row);var expected=reference.get(row);
                boolean duplicate=row>0&&actual[0]==recorder.rows.get(row-1)[0];
                if(duplicate)duplicates++;
                else if(row>0){step++;referenceClock+=(float)h;}
                // Validate each simulator's clock independently at the same step/event side.
                // The capture accumulates a binary32 clock; do not shift or interpolate signals.
                double sourceTime=expected.get("time_s"),expectedSourceTime=referenceClock,expectedTime=(step-2)*h;
                boolean sourceDuplicate=row>0&&sourceTime==reference.get(row-1).get("time_s");
                int index=row;
                checks.add(()->assertEquals(expectedTime,actual[0],1e-12,"Integration grid at row "+index));
                checks.add(()->assertEquals(expectedSourceTime,sourceTime,1e-12,"Captured clock at row "+index));
                checks.add(()->assertEquals(sourceDuplicate,duplicate,"Event side at row "+index));
                double[] boundary={expected.get("V7"),expected.get("V9"),expected.get("VAR5"),expected.get("VAR7"),
                        expected.get("VAR9")/100,expected.get("VAR10")/100,expected.get("VAR11")/100,expected.get("VAR12")/100,expected.get("STATE6"),expected.get("STATE7")};
                w.write(actual[0]+","+expected.get("time_s"));
                for(int k=0;k<26;k++){double target=k<10?boundary[k]:expected.get("STATE"+(k-10)),error=actual[k+1]-target;
                    int channel=k;
                    checks.add(()->assertTrue(Double.isFinite(actual[channel+1])&&Double.isFinite(target),"Finite sample/channel "+index+"/"+channel));
                    if(Math.abs(error)>maximum[k]){maximum[k]=Math.abs(error);times[k]=actual[0];}w.write(","+actual[k+1]+","+target+","+error);}
                w.write("\n");
            }
        }
        var report=new LinkedHashMap<String,Object>();report.put("profile",tag);report.put("staggered",staggered);report.put("completed_samples",recorder.rows.size());
        report.put("reference_samples",reference.size());report.put("event_duplicates",duplicates);report.put("trajectory_complete",stopped==null);
        report.put("maximum_absolute_errors",maximum);report.put("maximum_error_times",times);report.put("limits",LIMITS);
        report.put("automatic_import",false);report.put("full_network_terminal_attachment",true);report.put("recorded_state_or_input_forcing",false);
        report.put("protected_profile",protectedProfile);report.put("fault_bus",faultBus);report.put("fault_reactance_pu",reactance);
        if(protectedProfile){
            int flagMismatches=0;double markerError=0,transitionError=0;
            try(var w=BenchmarkOutputGuard.newWriter(output.resolve("protection.csv"),2*1024*1024)){
                w.write("time_s,reference_time_s,raml_active,raml_marker_s,reference_marker_s,shorted,reference_shorted,transition_observed,transition_s,reference_transition_s\n");
                for(int k=0;k<recorder.rows.size();k++){
                    var history=recorder.histories.get(k);var expected=reference.get(k);
                    double shorted=history.get("Inverter shorted"),sourceShorted=expected.get("VAR22")<0?1:0;
                    if(shorted!=sourceShorted)flagMismatches++;
                    markerError=Math.max(markerError,Math.abs(history.get("RAML timer marker (s)")-expected.get("VAR18")));
                    boolean observed=history.get("Commutation transition observed")!=0,sourceObserved=expected.get("VAR21")!=999;
                    if(observed!=sourceObserved)flagMismatches++;
                    if(observed&&sourceObserved)transitionError=Math.max(transitionError,Math.abs(history.get("Commutation transition time (s)")-expected.get("VAR21")));
                    w.write(recorder.rows.get(k)[0]+","+expected.get("time_s")+","+history.get("RAML active")+","+history.get("RAML timer marker (s)")+","+expected.get("VAR18")+","+shorted+","+sourceShorted+","+history.get("Commutation transition observed")+","+history.get("Commutation transition time (s)")+","+expected.get("VAR21")+"\n");
                }
            }
            report.put("protection_flag_mismatches",flagMismatches);report.put("maximum_raml_marker_error_s",markerError);report.put("maximum_commutation_clock_error_s",transitionError);
            int mismatches=flagMismatches;double ramlError=markerError,clockError=transitionError;
            checks.add(()->assertEquals(0,mismatches,"Protection regime and observed-transition flags"));
            checks.add(()->assertTrue(ramlError<h*1.01,"RAML marker error "+ramlError));
            checks.add(()->assertTrue(clockError<h*1.01,"Commutation clock error "+clockError));
        }
        if(stopped!=null){report.put("stopped_time_s",a.getSimuTime());report.put("failure",stopped.toString());}
        try(var w=BenchmarkOutputGuard.newWriter(output.resolve("comparison.json"),20000)){JSON.writerWithDefaultPrettyPrinter().writeValue(w,report);}
        if(stopped!=null)throw stopped;
        assertEquals(reference.size(),recorder.rows.size());assertEquals(2,duplicates);
        for(int k=0;k<26;k++){int channel=k;checks.add(()->assertTrue(maximum[channel]<LIMITS[channel],"Channel "+channel+" error "+maximum[channel]+" at "+times[channel]));}
        assertAll(tag+" / "+staggered,checks);
    }
}
