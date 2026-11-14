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
import org.interpss.core.dstab.reference.RelativeRmse;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import com.interpss.dstab.hvdc.model.*;
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
    private static final double BOUNDARY_NRMSE_LIMIT=.02, STATE_NRMSE_LIMIT=.05;
    private static final double SMALL_RANGE_LIMIT_MULTIPLIER=10.;
    private static final double RAML_ANGLE_RMSE_LIMIT_DEG=.1;
    private static final double LOW_MAGNITUDE_STATE_REFERENCE_RMS_LIMIT=1e-2;
    private static final double LOW_MAGNITUDE_STATE_ERROR_RMSE_LIMIT=1e-4;
    @TempDir Path folder;
    @BeforeAll static void integrity()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent coupled reference captures");
        assertEquals("75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
    }
    static Stream<Arguments> profiles(){return Stream.of("0p0005","0p00025").flatMap(h->Stream.of(false,true).map(s->Arguments.of(h,s)));}
    private static final Path LINE_ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-line-input-20261103/fixed-tap-captures.zip");
    private static String lineTag(String interval){return "chvdc2-fixed-tap-line-control-20261103/stiff-legacy-inverter-fault-flags001-h"
            +interval+"-fixed-angle-line-gamma30-netiter50-fixed-dc-taps-a";}
    private static void lineIntegrity()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(LINE_ARCHIVE),"Retain independent fixed-angle line captures");
        assertEquals("2ef07e630f94d9b53409b5e743af3a619cd7e87e67dbcf9991068ac1cf3dd0af",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(LINE_ARCHIVE))));
    }
    @Test void fixedAngleCapturesIsolateExcitedLineWithoutProtection()throws Exception {
        lineIntegrity();
        try(var z=new ZipFile(LINE_ARCHIVE.toFile())) {
            for(String interval:List.of("0p0005","0p00025")) {
                String tag=lineTag(interval);
                var manifest=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                assertEquals("complete",manifest.path("status").asText());
                assertTrue(manifest.path("initialization_and_network_valid").asBoolean());
                assertEquals("disabled",manifest.path("settings").path("dc_tap_adjustment").asText());
                assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
                for(int k:new int[]{9,12,18,19,30})assertEquals(0,manifest.path("entered_constants").get(k).asDouble());
                byte[] csv=z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes();
                assertArrayEquals(csv,z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                var rows=Tiocr1ResetReferenceTest.csv(csv);assertEquals(interval.equals("0p0005")?1005:2005,rows.size());
                int duplicates=0;double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
                for(int k=0;k<rows.size();k++) {
                    var row=rows.get(k);
                    assertEquals(rows.getFirst().get("STATE6"),row.get("STATE6"));
                    assertEquals(rows.getFirst().get("STATE7"),row.get("STATE7"));
                    assertEquals(1.,row.get("VAR22"));
                    if(k>0&&row.get("time_s").equals(rows.get(k-1).get("time_s")))duplicates++;
                    min=Math.min(min,row.get("VAR5"));max=Math.max(max,row.get("VAR5"));
                }
                assertEquals(2,duplicates);assertTrue(max-min>30,"The line must be independently excited");
            }
        }
    }
    @ParameterizedTest @MethodSource("profiles")
    void fixedAngleLineNetworkComparedAgainstIndependentCaptures(String interval,boolean staggered)throws Exception {
        lineIntegrity();compare(LINE_ARCHIVE,lineTag(interval),staggered,false);
    }
    @ParameterizedTest @ValueSource(strings={"0p0005","0p00025"})
    void fixedAngleLineWithLocalIntegration(String interval)throws Exception {
        lineIntegrity();compare(LINE_ARCHIVE,lineTag(interval),true,false,Chvdc2TerminalPorts.ProtectionClock.BINARY32,10);
    }
    static Stream<Arguments> coarseLineProfiles(){return Stream.of("0p00416666666667","0p00208333333333")
            .flatMap(h->Stream.of(false,true).map(s->Arguments.of(h,s)));}
    @ParameterizedTest @MethodSource("coarseLineProfiles")
    void coarseFixedAngleLineNetworkComparedAgainstIndependentCaptures(String interval,boolean staggered)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/chvdc2-coarse-line-event-20261104/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain coarse fixed-angle controls");
        assertEquals("ae3e8619df97ad2fb85b6d4704959faf60c057c87a6a8b2ef21d23e0efdd839c",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        compare(archive,"stiff-legacy-inverter-fault-flags001-h"+interval+"-fixed-angle-line-gamma30-netiter50-fixed-dc-taps-a",
                staggered,false,Chvdc2TerminalPorts.ProtectionClock.BINARY32,staggered?10:1);
    }
    @ParameterizedTest @ValueSource(strings={"0p00416666666667","0p00208333333333"})
    void coarseDefaultLocalModifiedEulerNetworkComparedAgainstIndependentCaptures(String interval)throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/chvdc2-coarse-line-event-20261104/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain coarse fixed-angle controls");
        assertEquals("ae3e8619df97ad2fb85b6d4704959faf60c057c87a6a8b2ef21d23e0efdd839c",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        compare(archive,"stiff-legacy-inverter-fault-flags001-h"+interval+"-fixed-angle-line-gamma30-netiter50-fixed-dc-taps-a",
                false,false,Chvdc2TerminalPorts.ProtectionClock.BINARY32,Chvdc2TerminalPorts.DEFAULT_LOCAL_STEPS);
    }
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
        final List<double[]> rows=new ArrayList<>();boolean enabled;int networkSolves;
        final List<double[]> dcBoundaries=new ArrayList<>();
        final List<Map<String,Double>> histories=new ArrayList<>();
        boolean faultEnabled=true;
        final double[] states,boundary=new double[10];
        Recorder(com.interpss.dstab.algo.DynamicSimuAlgorithm a,Chvdc2NetworkPreparation.Binding b,Chvdc2TerminalPorts p){algorithm=a;binding=b;ports=p;states=new double[b.runtime().staggeredStateCount()];}
        void before(double time){if(enabled&&(Math.abs(time-.05)<1e-9||Math.abs(time-.1)<1e-9))capture(time);}
        void capture(double time){
            var r=ports.rectifierPort().getDStabBus().getVoltage();var i=ports.inverterPort().getDStabBus().getVoltage();
            // Compare the actual published terminal injection, not a fresh electrical-history evaluation.
            var rp=r.multiply(((Complex)ports.rectifierPort().getOutputObject()).conjugate()).negate();
            var ip=i.multiply(((Complex)ports.inverterPort().getOutputObject()).conjugate()).negate();
            binding.runtime().readPublishedStates(states,0);binding.runtime().readBoundary(boundary,0);
            double[] row=new double[11+states.length];row[0]=time;row[1]=r.abs();row[2]=i.abs();row[3]=boundary[2];row[4]=boundary[3];
            row[5]=rp.getReal();row[6]=rp.getImaginary();row[7]=ip.getReal();row[8]=ip.getImaginary();row[9]=states[6];row[10]=states[7];
            System.arraycopy(states,0,row,11,states.length);rows.add(row);histories.add(binding.runtime().getIntegrationHistories());
            dcBoundaries.add(new double[]{boundary[0],boundary[1]});
        }
    }
    private static final class Ordinary extends DefaultDynamicSimulationSolver {
        final Recorder r;Ordinary(Recorder r){super(r.algorithm);this.r=r;converge_tol=1e-10;setMaxIterationTimes(50);}
        @Override public void beforeStep(double time)throws DStabSimuException{r.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{r.networkSolves++;boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage))r.capture(dstabAlgo.getSimuTime());return ok;}
    }
    private static final class Shared extends StaggeredEulerSolver {
        final Recorder r;Shared(Recorder r){super(r.algorithm);this.r=r;converge_tol=1e-10;setMaxIterationTimes(50);setReuseNetworkEndpoint(false);}
        @Override public void beforeStep(double time)throws DStabSimuException{r.before(time);super.beforeStep(time);}
        @Override public boolean networkSolutionStep()throws DStabSimuException{r.networkSolves++;boolean ok=super.networkSolutionStep();if(ok&&r.enabled&&"INITIAL".equals(networkSolutionStage))r.capture(dstabAlgo.getSimuTime());return ok;}
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
    @ParameterizedTest @MethodSource("protectedProfiles")
    void completeProtectedNetworkWithAccumulatedBinary32Clock(String archive,String digest,String tag,boolean staggered)throws Exception {
        Path source=Path.of("testData/private/verification-artifacts").resolve(archive);
        Assumptions.assumeTrue(Files.isRegularFile(source),"Retain independent protected network captures");
        assertEquals(digest,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        compare(source,tag,staggered,true,Chvdc2TerminalPorts.ProtectionClock.BINARY32);
    }
    private void compare(Path archive,String tag,boolean staggered,boolean protectedProfile)throws Exception {
        compare(archive,tag,staggered,protectedProfile,Chvdc2TerminalPorts.ProtectionClock.BINARY32);
    }
    private void compare(Path archive,String tag,boolean staggered,boolean protectedProfile,Chvdc2TerminalPorts.ProtectionClock clock)throws Exception {
        compare(archive,tag,staggered,protectedProfile,clock,1);
    }
    static Stream<Arguments> localProfiles() {
        return Stream.of(
                new String[]{"chvdc2-controller-20261102/captures.zip","75d8b6a733eb20426dbc8c8fe7c4efdab854c8e11a82da99ee98f6ce76377511","stiff-legacy-inverter-fault-flags001-h0p0005-a"},
                new String[]{"chvdc2-protection-20261102/captures.zip","32079591003689230139fa893b5c3e07a315dc4d411442372838e10da3b658ea","chvdc2-protection-short-hold-20261102/stiff-legacy-inverter-fault-flags001-h0p0005-gamma12-faultx0p02-netiter50-cfhold0p005-a"},
                new String[]{"chvdc2-measurements-20261102/captures.zip","d9d055e905044a4de552955beb536267699184eae70fe0498746f1b3409ac707","stiff-legacy-rectifier-fault-flags000-h0p0005-netiter50-meas0p001-a"})
                .flatMap(p->Stream.of(1,2,4,10).map(n->Arguments.of(p[0],p[1],p[2],n)));
    }
    @ParameterizedTest @MethodSource("localProfiles")
    void localStaggeredNetworkRefinement(String archive,String digest,String tag,int localSteps)throws Exception {
        Path source=Path.of("testData/private/verification-artifacts").resolve(archive);
        Assumptions.assumeTrue(Files.isRegularFile(source),"Retain independent local-integration controls");
        assertEquals(digest,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        compare(source,tag,true,tag.contains("cfhold"),Chvdc2TerminalPorts.ProtectionClock.BINARY32,localSteps);
    }
    @ParameterizedTest @MethodSource("localProfiles")
    void localModifiedEulerNetworkRefinement(String archive,String digest,String tag,int localSteps)throws Exception {
        Path source=Path.of("testData/private/verification-artifacts").resolve(archive);
        Assumptions.assumeTrue(Files.isRegularFile(source),"Retain independent local-integration controls");
        assertEquals(digest,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        compare(source,tag,false,tag.contains("cfhold"),Chvdc2TerminalPorts.ProtectionClock.BINARY32,localSteps);
    }
    @Test void fastAlgebraicLineModifiedEulerNetworkControl()throws Exception {
        Path source=Path.of("testData/private/verification-artifacts/chvdc2-measurements-20261102/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(source),"Retain independent fast-line control");
        assertEquals("d9d055e905044a4de552955beb536267699184eae70fe0498746f1b3409ac707",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        compare(source,"stiff-legacy-rectifier-fault-flags000-h0p0005-netiter50-meas0p001-a",false,false,
                Chvdc2TerminalPorts.ProtectionClock.BINARY32,1);
    }
    void compare(Path archive,String tag,boolean staggered,boolean protectedProfile,Chvdc2TerminalPorts.ProtectionClock clock,int localSteps)throws Exception {
        List<Map<String,Double>> reference;com.fasterxml.jackson.databind.JsonNode manifest,equipment;
        Path raw=folder.resolve("network.raw"),dyr=folder.resolve("machines.dyr");
        try(var z=new ZipFile(archive.toFile())) {
            manifest=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
            equipment=JSON.readTree(z.getInputStream(z.getEntry(tag+"/solved-equipment.json")));
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
        var flow=a.getAclfAlgorithm();flow.setTolerance(1e-10);flow.setMaxIterations(80);
        // Match the independent preparation's explicit DC tap policy and solve
        // the equipment. Do not import recorded operating points or states.
        boolean dcTaps=!"disabled".equals(manifest.path("settings").path("dc_tap_adjustment").asText("enabled"));
        var adjustment=flow.getLfAdjAlgo();adjustment.setApplyAdjustAlgo(dcTaps);
        adjustment.getVoltAdjConfig().setXfrTapControl(false);
        adjustment.getVoltAdjConfig().setSwitchedShuntAdjust(false);
        adjustment.getVoltAdjConfig().setHvdcTapControl(dcTaps);
        assertTrue(flow.loadflow());
        double h=manifest.path("settings").path("step").asDouble();
        var p=new Chvdc2Parameters("1",Chvdc2Parameters.Dialect.CHVDC2U1,
                JSON.convertValue(manifest.path("settings").path("flags"),int[].class),JSON.convertValue(manifest.path("entered_constants"),double[].class));
        var binding=Chvdc2NetworkPreparation.prepare(n,p,h,-2*h);var ports=new Chvdc2TerminalPorts(binding,clock,localSteps);ports.attach();
        for(var entry:Map.of("7",binding.owner().getRectifier(),"9",binding.owner().getInverter()).entrySet()) {
            var expected=equipment.path(entry.getKey());var converter=entry.getValue();
            assertEquals(expected.path("TAP").asDouble(),converter.getXformerTapSetting(),1e-7,"Solved converter tap");
            assertEquals(expected.path("KVDC").asDouble()*1000,converter.getDcVoltage(),.5,"Solved converter voltage");
            assertEquals(expected.path("DCCUR").asDouble(),converter.getIdc(),.002,"Solved converter current");
        }
        var recorder=new Recorder(a,binding,ports);a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(h);a.setTotalSimuTimeSec(.5);
        a.setSimuOutputHandler(new StateMonitor());a.setSolver(staggered?new Shared(recorder):new Ordinary(recorder));
        String faultBus=manifest.path("settings").path("profile").asText().equals("rectifier-fault")?"Bus7":"Bus9";
        double reactance=manifest.path("settings").path("fault_reactance").asDouble(.2);
        recorder.faultEnabled=!manifest.path("settings").path("profile").asText().equals("flat");
        if(recorder.faultEnabled)n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(faultBus,n,SimpleFaultCode.GROUND_3P,new Complex(0,reactance),null,.05,.05),"TerminalFault");
        assertTrue(a.initialization());a.setSimuTime(-2*h);recorder.enabled=true;
        Exception stopped=null;
        try{while(a.getSimuTime()<.5-h/2)if(!a.solveDEqnStep(true))throw new IllegalStateException("Attached network step did not converge");recorder.capture(a.getSimuTime());}
        catch(Exception failure){stopped=failure;}
        Path output=Files.createTempDirectory(Files.createDirectories(Path.of(localSteps>1?"../target/chvdc2-terminal-local-reference":"../target/chvdc2-terminal-reference")),staggered?"staggered-":"euler-");
        int channels=10+recorder.states.length;
        double[] maximum=new double[channels],times=new double[channels];int duplicates=0,step=0;
        double[] referenceMin=new double[channels],referenceMax=new double[channels];
        Arrays.fill(referenceMin,Double.POSITIVE_INFINITY);Arrays.fill(referenceMax,Double.NEGATIVE_INFINITY);
        double[][] periodSquaredError=new double[2][channels],periodSquaredReference=new double[2][channels];int[] periodSamples=new int[2];
        int[] periodByRow=new int[recorder.rows.size()];
        boolean faultOn=false;
        float referenceClock=(float)(-2*h);
        var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
        try(var w=BenchmarkOutputGuard.newWriter(output.resolve("comparison.csv"),2*1024*1024)) {
            w.write("time_s,reference_time_s");for(int k=0;k<channels;k++)w.write(",actual_"+k+",reference_"+k+",error_"+k);w.write("\n");
            for(int row=0;row<recorder.rows.size();row++) {
                var actual=recorder.rows.get(row);var expected=reference.get(row);
                boolean duplicate=row>0&&actual[0]==recorder.rows.get(row-1)[0];
                if(duplicate)duplicates++;
                else if(row>0){step++;referenceClock+=(float)h;}
                if(recorder.faultEnabled&&duplicate) {
                    if(Math.abs(actual[0]-.05)<1e-9)faultOn=true;
                    else if(Math.abs(actual[0]-.1)<1e-9)faultOn=false;
                }
                int period=faultOn?1:0;periodByRow[row]=period;periodSamples[period]++;
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
                for(int k=0;k<channels;k++){double target=k<10?boundary[k]:expected.get("STATE"+(k-10)),error=actual[k+1]-target;
                    int channel=k;
                    checks.add(()->assertTrue(Double.isFinite(actual[channel+1])&&Double.isFinite(target),"Finite sample/channel "+index+"/"+channel));
                    referenceMin[k]=Math.min(referenceMin[k],target);referenceMax[k]=Math.max(referenceMax[k],target);
                    periodSquaredError[period][k]+=error*error;
                    periodSquaredReference[period][k]+=target*target;
                    if(Math.abs(error)>maximum[k]){maximum[k]=Math.abs(error);times[k]=actual[0];}w.write(","+actual[k+1]+","+target+","+error);}
                w.write("\n");
            }
        }
        try(var w=BenchmarkOutputGuard.newWriter(output.resolve("dc-interface.csv"),2*1024*1024)) {
            w.write("time_s,reference_time_s,actual_rectifier_dc_kv,reference_rectifier_dc_kv,actual_inverter_dc_kv,reference_inverter_dc_kv,actual_state2,reference_state2,actual_state3,reference_state3\n");
            for(int row=0;row<recorder.rows.size();row++){
                var actual=recorder.rows.get(row);var expected=reference.get(row);var dc=recorder.dcBoundaries.get(row);
                w.write(actual[0]+","+expected.get("time_s")+","+dc[0]*.001+","+expected.get("VAR6")
                        +","+dc[1]*.001+","+expected.get("VAR8")
                        +","+actual[13]+","+expected.get("STATE2")
                        +","+actual[14]+","+expected.get("STATE3")+"\n");
            }
        }
        var report=new LinkedHashMap<String,Object>();report.put("profile",tag);report.put("staggered",staggered);report.put("completed_samples",recorder.rows.size());
        report.put("local_steps",localSteps);report.put("network_interval_s",h);report.put("local_interval_s",h/localSteps);
        report.put("network_solution_calls",recorder.networkSolves);
        report.put("reference_samples",reference.size());report.put("event_duplicates",duplicates);report.put("trajectory_complete",stopped==null);
        report.put("maximum_absolute_errors",maximum);report.put("maximum_error_times",times);
        report.put("previous_maximum_absolute_limits",Arrays.copyOf(LIMITS,channels));
        Double[][] periodNrmse=new Double[2][channels],periodRmse=new Double[2][channels],periodRelativeRmse=new Double[2][channels],periodReferenceRms=new Double[2][channels];
        boolean[][] lowMagnitudeState=new boolean[2][channels];
        double[] referenceRange=new double[channels];boolean[] useRmse=new boolean[channels];
        for(int period=0;period<2;period++)if(periodSamples[period]>0)for(int k=0;k<channels;k++) {
            double range=referenceMax[k]-referenceMin[k];
            periodRmse[period][k]=Math.sqrt(periodSquaredError[period][k]/periodSamples[period]);
            periodReferenceRms[period][k]=Math.sqrt(periodSquaredReference[period][k]/periodSamples[period]);
            // Channels 8-9 duplicate published states; channels 10+ are the numbered states.
            lowMagnitudeState[period][k]=k>=8&&periodReferenceRms[period][k]<LOW_MAGNITUDE_STATE_REFERENCE_RMS_LIMIT;
            if(range>0)periodNrmse[period][k]=periodRmse[period][k]/range;
            periodRelativeRmse[period][k]=periodSquaredReference[period][k]==0.
                    ? periodSquaredError[period][k]==0.?0.:Double.POSITIVE_INFINITY
                    : Math.sqrt(periodSquaredError[period][k]/periodSquaredReference[period][k]);
        }
        for(int k=0;k<channels;k++){
            referenceRange[k]=referenceMax[k]-referenceMin[k];
            useRmse[k]=(tag.contains("-raml-")&&(k==8||k==16))
                    || referenceRange[k]<=SMALL_RANGE_LIMIT_MULTIPLIER*LIMITS[k];
        }
        report.put("acceptance_metric","period relative RMSE below 1%, or absolute RMSE below 1e-4 for states with reference RMS below 1e-2; event and protection checks remain independent");
        report.put("low_magnitude_state_reference_rms_limit",LOW_MAGNITUDE_STATE_REFERENCE_RMS_LIMIT);
        report.put("low_magnitude_state_error_rmse_limit",LOW_MAGNITUDE_STATE_ERROR_RMSE_LIMIT);
        report.put("raml_angle_rmse_limit_deg",RAML_ANGLE_RMSE_LIMIT_DEG);
        report.put("period_names",List.of("nonfault","fault_on"));report.put("period_samples",periodSamples);
        report.put("period_range_nrmse",periodNrmse);report.put("period_rmse",periodRmse);report.put("period_relative_rmse",periodRelativeRmse);
        report.put("period_reference_rms",periodReferenceRms);report.put("period_low_magnitude_state",lowMagnitudeState);
        report.put("reference_range",referenceRange);report.put("uses_rmse",useRmse);
        report.put("small_range_limit_multiplier",SMALL_RANGE_LIMIT_MULTIPLIER);
        report.put("boundary_nrmse_limit",BOUNDARY_NRMSE_LIMIT);report.put("state_nrmse_limit",STATE_NRMSE_LIMIT);
        report.put("numerical_state_count",recorder.states.length);
        report.put("automatic_import",false);report.put("full_network_terminal_attachment",true);report.put("recorded_state_or_input_forcing",false);
        report.put("protected_profile",protectedProfile);report.put("fault_bus",faultBus);report.put("fault_reactance_pu",reactance);
        report.put("protection_clock",clock.name());
        if(protectedProfile){
            int flagMismatches=0;double markerError=0,transitionError=0;
            double[] markerSquaredError=new double[2],markerSquaredReference=new double[2];int[] markerSamples=new int[2];
            double[] actualMarkers=new double[recorder.rows.size()],referenceMarkers=new double[recorder.rows.size()];
            try(var w=BenchmarkOutputGuard.newWriter(output.resolve("protection.csv"),2*1024*1024)){
                w.write("time_s,reference_time_s,raml_active,raml_marker_s,reference_marker_s,shorted,reference_shorted,transition_observed,transition_s,reference_transition_s,accepted_protection_time_s\n");
                for(int k=0;k<recorder.rows.size();k++){
                    var history=recorder.histories.get(k);var expected=reference.get(k);
                    double shorted=history.get("Inverter shorted"),sourceShorted=expected.get("VAR22")<0?1:0;
                    if(shorted!=sourceShorted)flagMismatches++;
                    double markerDifference=history.get("RAML timer marker (s)")-expected.get("VAR18");
                    actualMarkers[k]=history.get("RAML timer marker (s)");referenceMarkers[k]=expected.get("VAR18");
                    markerError=Math.max(markerError,Math.abs(markerDifference));
                    int markerPeriod=periodByRow[k];markerSquaredError[markerPeriod]+=markerDifference*markerDifference;
                    markerSquaredReference[markerPeriod]+=expected.get("VAR18")*expected.get("VAR18");
                    markerSamples[markerPeriod]++;
                    boolean observed=history.get("Commutation transition observed")!=0,sourceObserved=expected.get("VAR21")!=999;
                    if(clock==Chvdc2TerminalPorts.ProtectionClock.BINARY32)
                        checks.add(()->assertEquals(expected.get("time_s"),history.get("Accepted time (s)"),1e-12,"Independently accumulated protection clock"));
                    if(observed!=sourceObserved)flagMismatches++;
                    if(observed&&sourceObserved)transitionError=Math.max(transitionError,Math.abs(history.get("Commutation transition time (s)")-expected.get("VAR21")));
                    w.write(recorder.rows.get(k)[0]+","+expected.get("time_s")+","+history.get("RAML active")+","+history.get("RAML timer marker (s)")+","+expected.get("VAR18")+","+shorted+","+sourceShorted+","+history.get("Commutation transition observed")+","+history.get("Commutation transition time (s)")+","+expected.get("VAR21")+","+history.get("Accepted time (s)")+"\n");
                }
            }
            double[] markerRmse=new double[2],markerRelativeRmse=new double[2];
            for(int period=0;period<2;period++)if(markerSamples[period]>0){
                markerRmse[period]=Math.sqrt(markerSquaredError[period]/markerSamples[period]);
                markerRelativeRmse[period]=markerSquaredReference[period]==0.
                        ? markerSquaredError[period]==0.?0.:Double.POSITIVE_INFINITY
                        :Math.sqrt(markerSquaredError[period]/markerSquaredReference[period]);
            }
            var actualMarkerTransitions=new ArrayList<Integer>();var referenceMarkerTransitions=new ArrayList<Integer>();
            for(int k=1;k<actualMarkers.length;k++){
                if(Math.abs(actualMarkers[k]-actualMarkers[k-1])>2*h)actualMarkerTransitions.add(k);
                if(Math.abs(referenceMarkers[k]-referenceMarkers[k-1])>2*h)referenceMarkerTransitions.add(k);
            }
            boolean markerTransitionsWithinOneStep=actualMarkerTransitions.size()==referenceMarkerTransitions.size()
                    &&(!tag.contains("-raml-")||referenceMarkerTransitions.size()==2);
            boolean[] markerTransitionRows=new boolean[actualMarkers.length];
            if(markerTransitionsWithinOneStep)for(int k=0;k<actualMarkerTransitions.size();k++){
                int actualRow=actualMarkerTransitions.get(k),referenceRow=referenceMarkerTransitions.get(k);
                if(Math.abs(actualRow-referenceRow)>1
                        ||Math.abs(recorder.rows.get(actualRow)[0]-recorder.rows.get(referenceRow)[0])>h*1.01
                        ||Math.abs(actualMarkers[actualRow]-referenceMarkers[referenceRow])>h*1.01)
                    markerTransitionsWithinOneStep=false;
                if(actualRow!=referenceRow)markerTransitionRows[Math.min(actualRow,referenceRow)]=true;
            }
            double[] alignedMarkerSquaredError=new double[2],alignedMarkerSquaredReference=new double[2];
            int[] alignedMarkerSamples=new int[2];int excludedMarkerTransitionRows=0;
            for(int k=0;k<actualMarkers.length;k++){
                if(markerTransitionRows[k]){excludedMarkerTransitionRows++;continue;}
                int period=periodByRow[k];double difference=actualMarkers[k]-referenceMarkers[k];
                alignedMarkerSquaredError[period]+=difference*difference;
                alignedMarkerSquaredReference[period]+=referenceMarkers[k]*referenceMarkers[k];
                alignedMarkerSamples[period]++;
            }
            double[] alignedMarkerRelativeRmse=new double[2];
            for(int period=0;period<2;period++)if(alignedMarkerSamples[period]>0)
                alignedMarkerRelativeRmse[period]=alignedMarkerSquaredReference[period]==0.
                        ?alignedMarkerSquaredError[period]==0.?0.:Double.POSITIVE_INFINITY
                        :Math.sqrt(alignedMarkerSquaredError[period]/alignedMarkerSquaredReference[period]);
            report.put("protection_flag_mismatches",flagMismatches);report.put("maximum_raml_marker_error_s",markerError);
            report.put("period_raml_marker_rmse_s",markerRmse);report.put("period_raml_marker_relative_rmse",markerRelativeRmse);
            report.put("raml_actual_transition_rows",actualMarkerTransitions);
            report.put("raml_reference_transition_rows",referenceMarkerTransitions);
            report.put("raml_transitions_within_one_step",markerTransitionsWithinOneStep);
            report.put("raml_excluded_transition_rows",excludedMarkerTransitionRows);
            report.put("period_raml_aligned_marker_relative_rmse",alignedMarkerRelativeRmse);
            report.put("maximum_commutation_clock_error_s",transitionError);
            int mismatches=flagMismatches;double clockError=transitionError;
            checks.add(()->assertEquals(0,mismatches,"Protection regime and observed-transition flags"));
            boolean transitionsWithinOneStep=markerTransitionsWithinOneStep;
            checks.add(()->assertTrue(transitionsWithinOneStep,"RAML marker transitions must match within one recorded step"));
            for(int period=0;period<2;period++)if(markerSamples[period]>0){
                int selected=period;
                checks.add(()->assertTrue(alignedMarkerRelativeRmse[selected]<RelativeRmse.ONE_PERCENT,
                        "RAML marker "+(selected==1?"fault-on":"nonfault")+" aligned relative RMSE="+alignedMarkerRelativeRmse[selected]
                        +"; raw="+markerRelativeRmse[selected]
                        +"; limit="+RelativeRmse.ONE_PERCENT));
            }
            checks.add(()->assertTrue(clockError<h*1.01,"Commutation clock error "+clockError));
        }
        if(stopped!=null){report.put("stopped_time_s",a.getSimuTime());report.put("failure",stopped.toString());}
        try(var w=BenchmarkOutputGuard.newWriter(output.resolve("comparison.json"),20000)){JSON.writerWithDefaultPrettyPrinter().writeValue(w,report);}
        if(stopped!=null)throw stopped;
        assertEquals(reference.size(),recorder.rows.size());assertEquals(2,duplicates);
        if(recorder.faultEnabled)assertEquals((int)Math.round(.05/h)+1,periodSamples[1],
                "Fault-on count includes application-right and clearing-left samples");
        else assertEquals(0,periodSamples[1]);
        for(int k=0;k<channels;k++){
            int channel=k;
            for(int period=0;period<2;period++)if(periodSamples[period]>0){
                int selected=period;
                checks.add(()->assertTrue(periodRelativeRmse[selected][channel]<RelativeRmse.ONE_PERCENT
                        ||lowMagnitudeState[selected][channel]
                                &&periodRmse[selected][channel]<LOW_MAGNITUDE_STATE_ERROR_RMSE_LIMIT,
                        "Channel "+channel+" "+(selected==1?"fault-on":"nonfault")+
                        " relative RMSE="+periodRelativeRmse[selected][channel]
                        +"; absolute RMSE="+periodRmse[selected][channel]
                        +"; reference RMS="+periodReferenceRms[selected][channel]
                        +"; relative limit="+RelativeRmse.ONE_PERCENT
                        +"; low-state absolute limit="+LOW_MAGNITUDE_STATE_ERROR_RMSE_LIMIT));
            }
        }
        assertAll(tag+" / "+staggered,checks);
    }
}
