package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
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

/** State-fed constitutive-power and heating checks; complete coupled qualification remains separate. */
public class CompressorCurrentConformanceTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","CompressorCurrentConformanceTest");
    private static final Path ARCHIVE=Path.of(CONFIG.string("reference.archive",
            "testData/private/verification-artifacts/cmldblu1-current-refresh-20261031/captures.zip"));
    private static final String ARCHIVE_SHA="8c11c1e85484c4be5d00a16fc9ea8b2220a52158336fd08374b1e743b2bd5157";
    private static final Map<String,Profile> PROFILES=new TreeMap<>();
    private record Profile(double h,double restart,List<Map<String,Double>> rows) { }
    private static String sha(byte[] raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    }
    private static List<Map<String,Double>> csv(byte[] raw) {
        String[] lines=new String(raw,StandardCharsets.UTF_8).strip().split("\\R"),names=lines[0].split(",");
        assertEquals(51,names.length);var rows=new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.length;i++) {
            String[] fields=lines[i].split(",");assertEquals(names.length,fields.length);var row=new LinkedHashMap<String,Double>();
            for(int j=0;j<names.length;j++){double value=Double.parseDouble(fields[j]);assertTrue(Double.isFinite(value));row.put(names[j],value);}rows.add(row);
        }
        return rows;
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain or regenerate the private standalone current-law controls");
        assertEquals(ARCHIVE_SHA,sha(Files.readAllBytes(ARCHIVE)));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            var manifest=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("references.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:manifest.getAsJsonObject("files").entrySet())
                assertEquals(entry.getValue().getAsString(),sha(zip.getInputStream(zip.getEntry(entry.getKey())).readAllBytes()));
            for(var entry:manifest.getAsJsonObject("profiles").entrySet()) {
                var p=entry.getValue().getAsJsonObject();String name=p.get("entry").getAsString();
                byte[] raw=zip.getInputStream(zip.getEntry(name)).readAllBytes();
                assertArrayEquals(raw,zip.getInputStream(zip.getEntry(name.replace("-a-part","-b-part"))).readAllBytes());
                var rows=csv(raw);assertEquals(p.get("rows").getAsInt(),rows.size());
                double h=p.get("step_sec").getAsDouble();float clock=0;double[] grid=new double[(int)Math.round(.5/h)+1];
                for(int i=1;i<grid.length;i++){clock+=(float)h;grid[i]=clock;}
                int duplicates=0;double previous=Double.NEGATIVE_INFINITY;
                for(var row:rows) {
                    double t=row.get("time_s");assertTrue(t>=previous);if(t==previous)duplicates++;previous=t;
                    if(t>=0)assertEquals(grid[(int)Math.round(t/h)],t,6e-13);
                    assertEquals(row.get("V2"),row.get("VAR0"),0,"The isolated voltage observation must be the external bus magnitude");
                    for(String gain:List.of("VAR10","VAR11","VAR14","VAR18"))assertEquals(1,row.get(gain),0,"Protection gains must stay inactive in this control");
                }
                assertEquals(4,duplicates);assertTrue(rows.getFirst().get("time_s")<0);
                PROFILES.put(entry.getKey(),new Profile(h,p.get("restart_voltage").getAsDouble(),rows.stream().filter(r->r.get("time_s")>=0).toList()));
            }
        }
    }
    static Stream<Arguments> profiles() { return PROFILES.keySet().stream().flatMap(p->Stream.of(false,true).map(s->Arguments.of(p,s))); }
    private static Complex voltage(Map<String,Double> row) {
        double angle=Math.toRadians(row.get("A2"));return new Complex(row.get("V2")*Math.cos(angle),row.get("V2")*Math.sin(angle));
    }
    private static boolean matches(LD1PACImpl motor,Map<String,Double> source) {
        var history=motor.getIntegrationHistories();return history.get("RunA").equals(source.get("VAR16"))&&history.get("RunB").equals(source.get("VAR17"));
    }
    private record Action(int grid,double value) { }
    private static List<Action> actions(List<Map<String,Double>> rows,String key,double h,boolean source) {
        var result=new ArrayList<Action>();Double previous=null;
        for(var row:rows){double value=row.get(key);int grid=source?(int)Math.round(row.get("time_s")/h):row.get("grid").intValue();
            if(previous==null || value!=previous){result.add(new Action(grid,value));previous=value;}}
        return result;
    }
    private static void checkActions(List<Action> expected,List<Action> actual) {
        assertEquals(expected.size(),actual.size(),"No stall or restart may be omitted");
        for(int i=0;i<expected.size();i++){
            assertEquals(expected.get(i).value,actual.get(i).value);
            assertTrue(Math.abs(expected.get(i).grid-actual.get(i).grid)<=1,"Action differs by more than one accepted interval");
        }
    }
    private static double[] rawPower(LD1PACImpl motor,Complex voltage) {
        motor.getDStabBus().setVoltage(voltage);
        Complex totalY=motor.getPosSeqEquivY();Complex injection=(Complex)motor.getOutputObject();
        Complex rawCurrent=voltage.multiply(totalY.subtract(motor.getCompensateShuntY())).subtract(injection);
        Complex power=voltage.multiply(rawCurrent.conjugate()).multiply(motor.getDStabBus().getNetwork().getBaseMva()/motor.getMvaBase());
        return new double[]{power.getReal(),power.getImaginary()};
    }
    private static double[] expectedPower(Map<String,Double> source) {
        double v=source.get("V2");return new double[]{source.get("VAR2")/source.get("VAR26"),source.get("VAR3")/source.get("VAR26")-source.get("VAR15")*v*v};
    }
    private static final class Error {
        double maximum,sum,time;int count;
        void add(double difference,double sourceTime){sum+=difference*difference;count++;if(Math.abs(difference)>maximum){maximum=Math.abs(difference);time=sourceTime;}}
        Map<String,Object> report(){return Map.of("maximum_absolute",maximum,"rmse",Math.sqrt(sum/Math.max(1,count)),"source_time_of_maximum",time,"samples",count);}
    }
    private static void write(Path directory,List<Map<String,Double>> rows,Map<String,Object> report) throws Exception {
        Files.createDirectories(directory);
        try(var writer=BenchmarkOutputGuard.newWriter(directory.resolve("comparison.json"),100000)){new GsonBuilder().setPrettyPrinting().create().toJson(report,writer);}
        var keys=rows.getFirst().keySet();
        try(var writer=BenchmarkOutputGuard.newWriter(directory.resolve("actual.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),keys.size()))) {
            writer.write(String.join(",",keys)+"\n");
            for(var row:rows){boolean first=true;for(String key:keys){if(!first)writer.write(",");first=false;writer.write(row.get(key).toString());}writer.write("\n");}
        }
    }
    private static void replay(String name,boolean shared,Path output,boolean enforce) throws Exception {
        var profile=PROFILES.get(name);var model=Cmldblu1SharedStateTest.model("cmldblu1-restart");
        var motor=(LD1PACImpl)model.get1PhaseACMotor();motor.setCmpKpf(0);motor.setCmpKqf(0);motor.setVrst(profile.restart);
        var rows=new ArrayList<Map<String,Double>>();String[] names={"raw_P","raw_Q","thermal_rate_A","thermal_rate_B","trial_P","trial_Q"};
        var full=new LinkedHashMap<String,Error>();var qualified=new LinkedHashMap<String,Error>();
        for(String key:names){full.put(key,new Error());qualified.put(key,new Error());}
        double[] rates=new double[motor.staggeredStateCount()];int previousGrid=0;var timers=new CompressorTimerReplay(motor,shared);
        for(int i=0;i<profile.rows.size();i++) {
            var source=profile.rows.get(i);double time=source.get("time_s");int grid=(int)Math.round(time/profile.h);
            boolean advance=grid>previousGrid,event=!advance&&i>0;if(event)timers.beforeEvent(profile.h);
            double[] state=advance?timers.advance(profile.h):timers.current();
            Complex v=voltage(source);model.getLoadBus().setVoltage(v);
            state[0]=source.get("STATE0");state[1]=1+source.get("STATE1");state[2]=source.get("STATE2");state[3]=source.get("STATE3");motor.applyStaggeredState(state,0,0);
            if(advance){assertEquals(previousGrid+1,grid);motor.completeStaggeredStep(profile.h);assertTrue(motor.afterStep(profile.h));previousGrid=grid;}else if(event)timers.acceptEvent(time);
            double[] actual=rawPower(motor,v),expected=expectedPower(source);boolean same=matches(motor,source);
            var accepted=motor.getIntegrationStates();var history=motor.getIntegrationHistories();
            if(shared)motor.evaluateStaggered(profile.h,rates,0);
            else {assertTrue(motor.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));var snapshot=motor.getStateDerivativeSnapshot();rates[2]=snapshot.rates().get("TemperatureA");rates[3]=snapshot.rates().get("TemperatureB");}
            double heatA=(.11*Math.pow(source.get("VAR19"),2)-source.get("STATE2"))/13;
            double heatB=(.11*Math.pow(source.get("VAR20"),2)-source.get("STATE3"))/13;
            var next=profile.rows.get(Math.min(i+1,profile.rows.size()-1));double[] trial=rawPower(motor,voltage(next)),expectedTrial=expectedPower(next);boolean trialSame=matches(motor,next);
            double[] restored=rawPower(motor,v);assertArrayEquals(actual,restored,1e-12);
            assertEquals(accepted,motor.getIntegrationStates());assertEquals(history,motor.getIntegrationHistories(),"Trial currents and rates cannot commit physical history");
            double[] differences={actual[0]-expected[0],actual[1]-expected[1],rates[2]-heatA,rates[3]-heatB,trial[0]-expectedTrial[0],trial[1]-expectedTrial[1]};
            for(int k=0;k<names.length;k++){full.get(names[k]).add(differences[k],time);if(k<4?same:trialSame)qualified.get(names[k]).add(differences[k],time);}
            var row=new LinkedHashMap<String,Double>();row.put("source_time_s",time);row.put("grid",(double)grid);row.put("matching_discrete_state",same?1.:0.);row.put("matching_trial_state",trialSame?1.:0.);
            row.putAll(motor.getNamedStates());row.put("RunA",history.get("RunA"));row.put("RunB",history.get("RunB"));
            for(int k=0;k<names.length;k++)row.put(names[k]+"_error",differences[k]);rows.add(row);
        }
        var report=new LinkedHashMap<String,Object>();report.put("status","recorded-state-current-and-heating-replay-only-coupled-qualification-incomplete");report.put("profile",name);report.put("rhs_method",shared?"staggered":"modified-euler");report.put("step_sec",profile.h);
        var unmasked=new LinkedHashMap<String,Object>();var matching=new LinkedHashMap<String,Object>();for(String key:names){unmasked.put(key,full.get(key).report());matching.put(key,qualified.get(key).report());}
        report.put("unmasked_errors",unmasked);report.put("errors_at_matching_discrete_states",matching);write(output,rows,report);
        if(enforce){
            checkActions(actions(profile.rows,"VAR16",profile.h,true),actions(rows,"RunA",profile.h,false));
            checkActions(actions(profile.rows,"VAR17",profile.h,true),actions(rows,"RunB",profile.h,false));
        }
        if(enforce)for(String key:names){
            assertTrue(qualified.get(key).count>=rows.size()-4,"More than four action-edge samples have different discrete states");
            double limit=key.startsWith("thermal")?2e-7:key.endsWith("P")?1.2e-6:2e-6;
            assertTrue(qualified.get(key).maximum<=limit,key+" error: "+qualified.get(key).report());
        }
    }
    @ParameterizedTest @MethodSource("profiles")
    void recordedStatesQualifyCurrentAndHeatingAcrossAcceptedAndTrialVoltages(String name,boolean shared) throws Exception {
        replay(name,shared,Files.createTempDirectory(Path.of("target"),"compressor-current-conformance-"),true);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void zeroVoltageHasFiniteCurrentAndThermalRates(boolean shared) throws Exception {
        var model=Cmldblu1SharedStateTest.model("cmldblu1-restart");var motor=(LD1PACImpl)model.get1PhaseACMotor();model.getLoadBus().setVoltage(Complex.ZERO);
        assertEquals(Complex.ZERO,motor.getOutputObject());
        if(shared){double[] rates=new double[motor.staggeredStateCount()];motor.evaluateStaggered(.0005,rates,0);for(double rate:rates)assertTrue(Double.isFinite(rate));}
        else {assertTrue(motor.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,0));assertTrue(motor.getStateDerivativeSnapshot().rates().values().stream().allMatch(Double::isFinite));}
    }
    @Test void warmedCurrentPublicationUsesBoundedScratchStorage() throws Exception {
        var generic=ManagementFactory.getThreadMXBean();Assumptions.assumeTrue(generic instanceof com.sun.management.ThreadMXBean);
        var bean=(com.sun.management.ThreadMXBean)generic;Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        var model=Cmldblu1SharedStateTest.model("cmldblu1-restart");var motor=(LD1PACImpl)model.get1PhaseACMotor();
        for(int i=0;i<50000;i++)motor.getOutputObject();long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
        for(int i=0;i<20000;i++)motor.getOutputObject();long bytes=bean.getThreadAllocatedBytes(thread)-before;
        System.out.println("Compressor current allocated bytes per callback: "+bytes/20000.);
        assertTrue(bytes<=104L*20000,"Scratch complex arithmetic must not add allocations beyond the published quantities");
    }
    /** Retains causal before/after diagnostics using the same state-fed harness. */
    public static void main(String[] args) throws Exception {
        setup();Path out=Path.of(args[0]);for(String name:PROFILES.keySet())for(boolean shared:new boolean[]{false,true})replay(name,shared,out.resolve(name+"-"+(shared?"staggered":"modified-euler")),false);
    }
}
