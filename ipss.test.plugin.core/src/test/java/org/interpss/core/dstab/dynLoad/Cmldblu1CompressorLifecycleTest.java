package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.google.gson.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Accepted compressor histories use their own timers through repeated stalls and restarts. */
public class Cmldblu1CompressorLifecycleTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "Cmldblu1CompressorLifecycleTest");
    private static final Path ARCHIVE = Path.of(CONFIG.string("reference.archive",
            "testData/private/verification-artifacts/cmldblu1-compressor-lifecycle-20261031/captures.zip"));
    private static final String ARCHIVE_SHA = "8f3cae2dfd96e9db24b68dea4b2e32460702280f80e292ae7375aefd67908001";
    private static final Map<Double,List<Map<String,Double>>> REFERENCES = new HashMap<>();

    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE), "Retain or regenerate the private repeated-fault references");
        assertEquals(ARCHIVE_SHA,sha(Files.readAllBytes(ARCHIVE)));
        try (var zip = new ZipFile(ARCHIVE.toFile())) {
            var manifest = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("references.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for (var profile : manifest.getAsJsonObject("profiles").entrySet()) {
                double h = Double.parseDouble(profile.getKey());
                List<Map<String,Double>> combined = null;
                assertTrue(profile.getValue().getAsJsonObject().get("common_channels_equal").getAsBoolean());
                for (var partValue : profile.getValue().getAsJsonObject().getAsJsonArray("parts")) {
                    var part = partValue.getAsJsonObject();
                    assertTrue(part.get("cold_pair_equal").getAsBoolean());
                    byte[] raw = zip.getInputStream(zip.getEntry(part.get("entry").getAsString())).readAllBytes();
                    assertEquals(part.get("sha256").getAsString(),sha(raw));
                    assertArrayEquals(raw,zip.getInputStream(zip.getEntry(part.get("entry").getAsString().replace("-a-part","-b-part"))).readAllBytes());
                    var rows = csv(raw); assertEquals(part.get("rows").getAsInt(),rows.size());
                    if (combined == null) combined = rows;
                    else for (int i=0;i<rows.size();i++) for (var entry : rows.get(i).entrySet()) {
                        var old = combined.get(i).putIfAbsent(entry.getKey(),entry.getValue());
                        if (old != null) assertEquals(old,entry.getValue());
                    }
                }
                assertNotNull(combined); assertEquals(186,combined.getFirst().size());
                int duplicates=0; double last=Double.NEGATIVE_INFINITY;
                for (var row : combined) { double t=row.get("time_s"); assertTrue(t>=last); if(t==last)duplicates++;last=t; }
                assertEquals(4,duplicates); assertTrue(combined.getFirst().get("time_s")<0);
                double[] clock=new double[(int)Math.round(.5/h)+1];float accumulated=0;
                for(int i=1;i<clock.length;i++){accumulated+=(float)h;clock[i]=accumulated;}
                for(var row:combined)if(row.get("time_s")>=0) {
                    int grid=(int)Math.round(row.get("time_s")/h);
                    assertEquals(clock[grid],row.get("time_s"),6e-13,"Declared accumulated binary32 source clock");
                }
                REFERENCES.put(h,combined.stream().filter(row->row.get("time_s")>=0).toList());
            }
        }
    }
    private static String sha(byte[] raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
    private static List<Map<String,Double>> csv(byte[] raw) {
        String[] lines = new String(raw,StandardCharsets.UTF_8).strip().split("\\R"), names=lines[0].split(",");
        var rows = new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.length;i++) {
            String[] fields=lines[i].split(",");assertEquals(names.length,fields.length);var row=new LinkedHashMap<String,Double>();
            for(int j=0;j<names.length;j++){double value=Double.parseDouble(fields[j]);assertTrue(Double.isFinite(value));row.put(names[j],value);}rows.add(row);
        }
        return rows;
    }
    static Stream<Arguments> methods() { return Stream.of(.0005,.00025).flatMap(h->Stream.of(false,true).map(s->Arguments.of(h,s))); }
    private static void input(org.interpss.dstab.dynLoad.impl.Cmldblu1Model model, Map<String,Double> row) {
        // Published compressor boundary inputs, retained independently of its
        // distribution diagnostic and sensed-voltage differential state.
        model.getLoadBus().setVoltage(new Complex(row.get("VAR117"),0));
        // This published frequency is already absolute per unit and clipped.
        // It is not the upstream phase-derived input of the frequency filter.
        model.getDStabBus().setFreq(row.get("VAR118"));
    }
    @ParameterizedTest @ValueSource(doubles={.0005,.00025})
    void publishedFrequencyUsesAbsoluteClippedCoordinates(double h) throws Exception {
        var source=REFERENCES.get(h);var model=Cmldblu1SharedStateTest.model("cmldblu1-restart");
        assertEquals(1,source.getFirst().get("VAR118"),1e-7);
        boolean clipped=false;
        for(var row:source) {
            double absolute=1+row.get("STATE19");
            assertEquals(Math.max(.95,Math.min(1.05,absolute)),row.get("VAR118"),1e-7);
            clipped|=absolute<.95 || absolute>1.05;
        }
        assertTrue(clipped,"The independent profile must activate the observation limiter");
        input(model,source.getFirst());assertEquals(1,model.getDStabBus().getFreq(),1e-7);
    }
    private record Transition(int grid,double value) { }
    private static List<Transition> transitions(List<Map<String,Double>> rows,String key,double h,boolean sourceClock) {
        var result=new ArrayList<Transition>(); Double old=null;
        for(var row:rows) {
            double value=row.get(key);int grid=sourceClock?(int)Math.round(row.get("time_s")/h):row.get("grid").intValue();
            if(old==null || value!=old){result.add(new Transition(grid,value));old=value;}
        }
        return result;
    }
    private static void compareTransitions(List<Transition> expected,List<Transition> actual) {
        assertEquals(expected.size(),actual.size(),"Every stall and restart must occur");
        for(int i=0;i<expected.size();i++) {
            assertEquals(expected.get(i).value,actual.get(i).value);
            assertTrue(Math.abs(expected.get(i).grid-actual.get(i).grid)<=1,"Accepted action differs by more than one grid interval");
        }
    }
    private static Map<String,Object> error(List<Map<String,Double>> source,List<Map<String,Double>> actual,String expected,String observed,double offset) {
        double maximum=0,sum=0;int maxRow=0;
        for(int i=0;i<source.size();i++) {
            Double measured=actual.get(i).get(observed);if(measured==null)return Map.of("status","unimplemented-coordinate");
            double delta=measured+offset-source.get(i).get(expected);sum+=delta*delta;
            if(Math.abs(delta)>maximum){maximum=Math.abs(delta);maxRow=i;}
        }
        return Map.of("maximum_absolute",maximum,"rmse",Math.sqrt(sum/source.size()),"source_time_of_maximum",source.get(maxRow).get("time_s"));
    }
    private static void write(Path directory,List<Map<String,Double>> actual,Map<String,Object> report) throws Exception {
        Files.createDirectories(directory);
        try(var writer=BenchmarkOutputGuard.newWriter(directory.resolve("comparison.json"),100000)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(report,writer);
        }
        var names=actual.getFirst().keySet();
        try(var writer=BenchmarkOutputGuard.newWriter(directory.resolve("actual.csv"),BenchmarkOutputGuard.estimateCsvBytes(actual.size(),names.size()))) {
            writer.write(String.join(",",names)+"\n");
            for(var row:actual){boolean first=true;for(String key:names){if(!first)writer.write(",");first=false;Double value=row.get(key);if(value!=null)writer.write(value.toString());}writer.write("\n");}
        }
    }
    private static Map<String,Object> replay(double h,boolean staggered,Path output,boolean enforce) throws Exception {
        var source=REFERENCES.get(h);var model=Cmldblu1SharedStateTest.model("cmldblu1-restart");
        var motor=(LD1PACImpl)model.get1PhaseACMotor();var actual=new ArrayList<Map<String,Double>>();
        double[] accepted=new double[motor.staggeredStateCount()],predicted=new double[motor.staggeredStateCount()],k1=new double[motor.staggeredStateCount()],k2=new double[motor.staggeredStateCount()];
        int previousGrid=0;Map<String,Double> previous=source.getFirst();
        for(var row:source) {
            int grid=(int)Math.round(row.get("time_s")/h);
            if(grid>previousGrid) {
                assertEquals(previousGrid+1,grid);input(model,previous);var history=motor.getIntegrationHistories();
                motor.evaluateStaggered(h,k1,0);motor.evaluateStaggered(h,k1,0);
                assertEquals(history,motor.getIntegrationHistories(),"Trial evaluations cannot commit protection");
                if(staggered) {
                    motor.readStaggeredState(accepted,0);motor.evaluateStaggered(h,k1,0);
                    for(int j=0;j<accepted.length;j++)predicted[j]=accepted[j]+h*k1[j];motor.applyStaggeredState(predicted,0,h);
                    input(model,row);motor.evaluateStaggered(h,k2,0);
                    for(int j=0;j<accepted.length;j++)predicted[j]=accepted[j]+.5*h*(k1[j]+k2[j]);
                    motor.applyStaggeredState(predicted,0,h);motor.completeStaggeredStep(h);
                } else {
                    assertTrue(motor.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0));input(model,row);
                    assertTrue(motor.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1));
                }
                assertTrue(motor.afterStep(h));previousGrid=grid;
            }
            var values=new LinkedHashMap<String,Double>();values.put("grid",(double)grid);values.put("source_time_s",row.get("time_s"));
            values.putAll(motor.getNamedStates());var history=motor.getIntegrationHistories();
            values.put("RunA",history.get("RunA"));values.put("RunB",history.get("RunB"));values.putIfAbsent("StallTimerB",history.get("StallTimerB"));
            values.put("AcceptedTime",history.get("AcceptedTime"));actual.add(values);previous=row;
        }
        var errors=new LinkedHashMap<String,Object>();
        String[] observed={"Vmeas","Fmeas","TemperatureA","TemperatureB","UvTimer1","UvTimer2","StallTimer","StallTimerB","RestartTimer"};
        for(int j=0;j<observed.length;j++)errors.put("STATE"+(18+j),error(source,actual,"STATE"+(18+j),observed[j],j==1?-1:0));
        var a=transitions(actual,"RunA",h,false);var b=transitions(actual,"RunB",h,false);
        var report=new LinkedHashMap<String,Object>();report.put("status","recorded-boundary-history-replay-continuous-state-and-network-parity-incomplete");
        report.put("input_coordinates",Map.of("voltage","published VAR117; distribution VAR29 is retained separately",
                "frequency","published VAR118 in absolute pu; clipped filter output, upstream phase-derived input unavailable"));
        report.put("continuous_state_error_status","diagnostic only; published observations are not qualified upstream filter inputs");
        report.put("method",staggered?"staggered":"modified-euler");report.put("step_sec",h);report.put("unmasked_errors",errors);report.put("run_A_transitions",a);report.put("run_B_transitions",b);
        write(output,actual,report);
        if(enforce) {
            compareTransitions(transitions(source,"VAR133",h,true),a);compareTransitions(transitions(source,"VAR134",h,true),b);
            for(int i=0;i<actual.size();i++) {
                var measured=actual.get(i);var expected=source.get(i);assertNotNull(measured.get("StallTimerB"));
                assertEquals(measured.get("grid")*h,measured.get("AcceptedTime"),1e-12,"One history commit per interval");
                if(measured.get("RunA")==0)assertEquals(0,measured.get("StallTimer"));
                if(measured.get("RunB")==0)assertEquals(0,measured.get("StallTimerB"));else assertEquals(0,measured.get("RestartTimer"));
                if(measured.get("RunA").equals(expected.get("VAR133")))assertEquals(expected.get("STATE24"),measured.get("StallTimer"),h+1e-8);
                if(measured.get("RunB").equals(expected.get("VAR134"))) {
                    assertEquals(expected.get("STATE25"),measured.get("StallTimerB"),h+1e-8);
                    assertEquals(expected.get("STATE26"),measured.get("RestartTimer"),h+1e-8);
                }
            }
        }
        return report;
    }
    @ParameterizedTest @MethodSource("methods")
    void recordedInputsQualifyRepeatedStallAndRestartHistories(double h,boolean staggered) throws Exception {
        replay(h,staggered,Files.createTempDirectory(Path.of("target"),"cmldblu1-compressor-replay-"),true);
    }
    @ParameterizedTest @MethodSource("methods")
    void coupledRepeatedFaultRetainsCompleteUnqualifiedReferenceErrors(double h,boolean staggered) throws Exception {
        var recording=Cmldblu1SharedRecording.simulate("cmldblu1-restart",staggered,false,h);
        var source=REFERENCES.get(h);assertEquals(source.size(),recording.frames.size());var rows=new ArrayList<Map<String,Double>>();
        for(var frame:recording.frames){var row=new LinkedHashMap<String,Double>(frame.values());row.put("grid",Math.rint(frame.time()/h));rows.add(row);}
        var output=Files.createTempDirectory(Path.of("target"),"cmldblu1-compressor-coupled-");recording.write(output.resolve("actual.csv.gz"));
        var errors=new LinkedHashMap<String,Object>();
        for(String key:List.of("V1","V2","V3","P_PU","Q_PU","ANGLE_DEG","SPEED"))errors.put(key,error(source,rows,key,key,0));
        var report=new LinkedHashMap<String,Object>();report.put("status","coupled-lifecycle-diagnostic-reference-parity-incomplete");report.put("step_sec",h);report.put("method",staggered?"staggered":"modified-euler");report.put("unmasked_boundary_errors",errors);
        try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("comparison.json"),100000)){new GsonBuilder().setPrettyPrinting().create().toJson(report,writer);}
        assertEquals(2,transitions(rows,"MotorD.RunA",h,false).size());assertEquals(5,transitions(rows,"MotorD.RunB",h,false).size());
        assertEquals(0,rows.getLast().get("MotorD.RunA"));assertEquals(1,rows.getLast().get("MotorD.RunB"));
    }
    @Test void reinitializationClearsAllAcceptedProtectionHistory() throws Exception {
        var model=Cmldblu1SharedStateTest.model("cmldblu1-restart");var motor=(LD1PACImpl)model.get1PhaseACMotor();
        Complex initial=model.getLoadBus().getVoltage();var initialCurrent=motor.getOutputObject();
        var history=motor.getIntegrationHistories();
        for(double invalid:new double[]{-.001,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->motor.afterStep(invalid));
            assertEquals(history,motor.getIntegrationHistories());
        }
        model.getLoadBus().setVoltage(new Complex(.1,0));for(int i=0;i<40;i++){assertTrue(motor.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(motor.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));assertTrue(motor.afterStep(.0005));}
        assertEquals(0,motor.getIntegrationHistories().get("RunA"));assertEquals(0,motor.getIntegrationHistories().get("RunB"));
        model.getLoadBus().setVoltage(Complex.ONE);for(int i=0;i<50;i++){assertTrue(motor.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(motor.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,1));assertTrue(motor.afterStep(.0005));}
        assertEquals(1,motor.getIntegrationHistories().get("RunB"));
        model.getLoadBus().setVoltage(initial);assertTrue(motor.initStates());
        for(String name:List.of("StallTimer","StallTimerB","RestartTimer","UvTimer1","UvTimer2","AcceptedTime"))assertEquals(0,motor.getIntegrationHistories().get(name));
        assertEquals(1,motor.getIntegrationHistories().get("RunA"));assertEquals(1,motor.getIntegrationHistories().get("RunB"));
        assertEquals(0,((Complex)initialCurrent).subtract((Complex)motor.getOutputObject()).abs(),1e-14);
    }
    /** A frozen pre-change compressor can be replayed without substituting its equations. */
    public static void main(String[] args) throws Exception {
        setup();Path directory=Path.of(args[0]);
        for(double h:new double[]{.0005,.00025})for(boolean shared:new boolean[]{false,true})replay(h,shared,directory.resolve((shared?"staggered":"modified-euler")+"-h"+h),false);
    }
}
