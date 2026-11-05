package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent active actuator limits and recorded-input backlash replay retain all reference samples. */
public class Hyg3LimitConformanceTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json","Hyg3LimitConformanceTest");
    @BeforeAll static void setup() { IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu"); }

    @Test void independentReferenceReversalsDistinguishTheLeftEndpointFromTwoStepHistory(@TempDir Path staged) throws Exception {
        var fixture = new Hyg3ReferenceFixture(staged,CONFIG);
        for (var entry : fixture.matrix()) {
            if (!entry.path("repeat").asText().equals("a")
                    || !entry.path("tag").asText().startsWith("m1-backlash-large-")) continue;
            String tag=entry.path("tag").asText(); Path root=fixture.stage(tag);
            var manifest=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            var constants=manifest.path("initialized_constants"); double h=entry.path("step_s").asDouble();
            var grid=new TreeMap<Long,Map<String,Double>>();
            for (var row : Hyg3StateConformanceTest.rows(root.resolve("native.csv")))
                if (row.get("time_s")>=0.) grid.put(Math.round(row.get("time_s")/h),row);
            double pref=manifest.path("initial_reference_from_equilibrium").asDouble(); int eventIndex=0;
            for (var event : manifest.path("reference_events")) {
                long tick=Math.round(event.get(0).asDouble()/h); double increment=event.get(1).asDouble();
                var before=grid.get(tick); var previous=grid.get(tick-1); var after=grid.get(tick+1);
                double left=inputFilterRate(before,pref,constants), prior=inputFilterRate(previous,pref,constants);
                double right=left+increment/constants.get(3).asDouble();
                double fresh=before.get("STATE0")+h*(.75*(right+left)-.5*prior);
                double stale=before.get("STATE0")+h*(.75*right+.25*prior);
                if (eventIndex>0) {
                    assertEquals(fresh,after.get("STATE0"),3e-8,tag+" independent current-left event recurrence");
                    assertTrue(Math.abs(stale-after.get("STATE0"))>8e-8,tag+" the preceding rate is a distinct discriminator");
                }
                pref+=increment; eventIndex++;
            }
        }
    }

    private static double inputFilterRate(Map<String,Double> row,double pref,com.fasterxml.jackson.databind.JsonNode constants) {
        return (pref-row.get("SPEED")-constants.get(0).asDouble()*row.get("VAR0")
                -constants.get(1).asDouble()*row.get("STATE5")-row.get("STATE0"))/constants.get(3).asDouble();
    }

    @Test void independentValvePlateausExposeAccumulatedBinary32GateRounding(@TempDir Path staged) throws Exception {
        var fixture=new Hyg3ReferenceFixture(staged,CONFIG);
        for (var entry : fixture.matrix()) {
            if (!entry.path("repeat").asText().equals("a") || !entry.path("profile").asText().equals("valve-limits")) continue;
            String tag=entry.path("tag").asText(); Path root=fixture.stage(tag);
            var constants=new ObjectMapper().readTree(root.resolve("manifest.json").toFile()).path("initialized_constants");
            double h=entry.path("step_s").asDouble(); var grid=new TreeMap<Long,Map<String,Double>>();
            for (var row : Hyg3StateConformanceTest.rows(root.resolve("native.csv")))
                if (row.get("time_s")>=0.) grid.put(Math.round(row.get("time_s")/h),row);
            double maxDefect=0.,accumulatedRounding=0.; int plateaus=0;
            for (var point : grid.entrySet()) {
                var previous=grid.get(point.getKey()-1); var after=grid.get(point.getKey()+1); var row=point.getValue();
                if (previous==null || after==null) continue;
                double valve=row.get("STATE3");
                if (Math.min(Math.abs(valve-constants.get(10).asDouble()),Math.abs(valve-constants.get(11).asDouble()))>1e-10
                        || Math.abs(previous.get("STATE3")-valve)>1e-10) continue;
                // On a constant-rate plateau the two-step formula reduces to x+h*v.
                // Reproduce storage precision only in this independent diagnostic.
                double rounded=(float)(row.get("STATE4")+(float)((float)h*valve));
                maxDefect=Math.max(maxDefect,Math.abs(rounded-after.get("STATE4")));
                accumulatedRounding+=after.get("STATE4")-row.get("STATE4")-h*valve; plateaus++;
            }
            assertTrue(plateaus>150,tag+" active plateau coverage"); assertTrue(maxDefect<1e-12,tag+" binary32 recurrence "+maxDefect);
            if (h<.001) assertTrue(Math.abs(accumulatedRounding)>1.9e-5,tag+" cumulative gate-storage rounding");
            System.out.println("HYG3 gate-storage diagnostic "+tag+" plateau steps="+plateaus
                    +" recurrence defect="+maxDefect+" accumulated rounding="+accumulatedRounding);
        }
    }

    @Test void nativeLimitsAndRecordedGateReplayExposeTheActualStateAndHistoryContracts(@TempDir Path staged) throws Exception {
        var fixture = new Hyg3ReferenceFixture(staged,CONFIG);
        for (var entry : fixture.matrix()) {
            if (!entry.path("repeat").asText().equals("a")) continue;
            String tag = entry.path("tag").asText(); Path root = fixture.stage(tag);
            var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            var constants = manifest.path("initialized_constants"); double step = entry.path("step_s").asDouble();
            var rows = Hyg3StateConformanceTest.rows(root.resolve("native.csv"));
            var loader = new PSSEMultiFileLoader(); var context = loader.loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
            var algorithm = context.getDynSimuAlgorithm(); assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(step); algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algorithm.initialization());
            var governor = (PsseHyg3Governor) context.getDStabilityNet().getMachine("Bus1-mach1").getGovernor();
            assertEquals(PsseHyg3GovernorData.BacklashConvention.PER_UNIT_GATE,governor.getData().getBacklashConvention());
            assertEquals(constants.get(14).asDouble(),governor.getEffectiveBacklashWidth(),1e-7);
            double[] trial = new double[9], rates = new double[9]; governor.readStaggeredState(trial,0);
            double lastAccepted = 0., maxOutputError = 0., maxHistoryError = 0.;
            for (var row : rows) {
                assertTrue(row.get("STATE3") >= constants.get(11).asDouble()-1e-7);
                assertTrue(row.get("STATE3") <= constants.get(10).asDouble()+1e-7);
                assertTrue(row.get("STATE4") >= constants.get(13).asDouble()-1e-7);
                assertTrue(row.get("STATE4") <= constants.get(12).asDouble()+1e-7);
                var history = governor.getIntegrationHistories(); trial[4] = row.get("STATE4");
                governor.applyStaggeredState(trial,0,step); governor.evaluateStaggered(step,rates,0);
                assertEquals(history,governor.getIntegrationHistories(),"Trial/equation evaluation cannot commit backlash memory");
                maxOutputError = Math.max(maxOutputError,Math.abs(governor.getGateOutput()-row.get("VAR3")));
                double time = row.get("time_s");
                if (time > lastAccepted) { governor.completeStaggeredStep(step); lastAccepted = time; }
                maxHistoryError = Math.max(maxHistoryError,Math.abs(governor.getBacklashInputHistory()-row.get("VAR2")));
            }
            assertTrue(maxOutputError < 2e-7,tag+" recorded-gate output "+maxOutputError);
            assertTrue(maxHistoryError < 2e-7,tag+" accepted input history "+maxHistoryError);
            String profile = entry.path("profile").asText();
            if (profile.equals("gate-limits")) {
                assertTrue(maximum(rows,"STATE1") > constants.get(12).asDouble()+.01);
                assertTrue(minimum(rows,"STATE1") < constants.get(13).asDouble()-.005);
                assertTrue(maximum(rows,"VAR0") > constants.get(12).asDouble()+.01);
                assertTrue(minimum(rows,"VAR0") < constants.get(13).asDouble()-.005);
                assertEquals(constants.get(12).asDouble(),maximum(rows,"STATE4"),1e-7);
                assertEquals(constants.get(13).asDouble(),minimum(rows,"STATE4"),1e-7);
            }
            if (profile.equals("valve-limits")) {
                assertEquals(constants.get(10).asDouble(),maximum(rows,"STATE3"),1e-7);
                assertEquals(constants.get(11).asDouble(),minimum(rows,"STATE3"),1e-7);
            }
            System.out.println("HYG3 limit/replay "+tag+" output="+maxOutputError+" history="+maxHistoryError);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void coupledReferenceReversalsRetainCompleteStateAndBoundaryDiagnostics(boolean staggered,@TempDir Path staged) throws Exception {
        var fixture = new Hyg3ReferenceFixture(staged,CONFIG);
        Path output = Files.createTempDirectory(Path.of("target"),staggered ? "hyg3-limit-staggered-" : "hyg3-limit-euler-");
        compare(fixture,staggered,output,entry -> true);
    }

    static void compare(Hyg3ReferenceFixture fixture,boolean staggered,Path output,
            java.util.function.Predicate<com.fasterxml.jackson.databind.JsonNode> include) throws Exception {
        compare(fixture,staggered,output,include,false);
    }

    static void compare(Hyg3ReferenceFixture fixture,boolean staggered,Path output,
            java.util.function.Predicate<com.fasterxml.jackson.databind.JsonNode> include,
            boolean prefaultRelativeStates) throws Exception {
        System.out.println("HYG3 active-limit comparison "+output.getFileName());
        var diagnostics = new LinkedHashMap<String,Object>();
        for (var entry : fixture.matrix()) {
            if (!entry.path("repeat").asText().equals("a") || !include.test(entry)) continue;
            String tag = entry.path("tag").asText(); Path root = fixture.stage(tag);
            var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            var events = new ArrayList<Hyg3SharedSmibTest.ReferenceEvent>();
            for (var event : manifest.path("reference_events")) events.add(new Hyg3SharedSmibTest.ReferenceEvent(event.get(0).asDouble(),event.get(1).asDouble()));
            assertEquals(3,events.size()); double step = entry.path("step_s").asDouble(), duration = entry.path("duration_s").asDouble();
            var reference = Hyg3StateConformanceTest.rows(root.resolve("native.csv"));
            var actual = Hyg3SharedSmibTest.simulate(root.resolve("network.raw"),root.resolve("dynamic.dyr"),staggered,false,step,
                    Hyg3StateConformanceTest.comparisonDuration(reference,step),events);
            assertEquals(manifest.path("initial_reference_from_equilibrium").asDouble(),actual.initial.values().get("REFERENCE_INPUT"),2e-8);
            assertEquals(events.size(),actual.nextEvent,"Every declared input boundary executes once");
            var maximum = new LinkedHashMap<String,Double>(); var at = new LinkedHashMap<String,Double>();
            var squared = new LinkedHashMap<String,Double>(); var low = new LinkedHashMap<String,Double>(); var high = new LinkedHashMap<String,Double>();
            for (String key : reference.getFirst().keySet()) if (!key.equals("time_s")) {
                maximum.put(key,0.); at.put(key,0.); squared.put(key,0.); low.put(key,Double.POSITIVE_INFINITY); high.put(key,Double.NEGATIVE_INFINITY);
            }
            int frame = 0,startups = 0; long clockTick = 0; float referenceClock = 0.f;
            try (var raw = BenchmarkOutputGuard.newOutputStream(output.resolve(tag+".csv.gz"),
                    BenchmarkOutputGuard.estimateCsvBytes(reference.size(),actual.initial.values().size()+reference.getFirst().size()));
                    var gzip = new GZIPOutputStream(raw); var writer = new BufferedWriter(new OutputStreamWriter(gzip,StandardCharsets.UTF_8))) {
                writer.write("reference_time_s,actual_time_s,sample_kind,"+String.join(",",actual.initial.values().keySet()));
                for (String key : reference.getFirst().keySet()) writer.write(",REFERENCE."+key); writer.write("\n");
                for (var expected : reference) {
                    double time = expected.get("time_s"); var row = time < 0. ? actual.initial : actual.frames.get(frame++);
                    if (time < 0.) startups++;
                    else {
                        long tick = Math.round(row.time()/step); assertEquals(tick*step,row.time(),1e-10);
                        assertTrue(tick >= clockTick && tick-clockTick <= 1);
                        while (clockTick < tick) { referenceClock += (float) step; clockTick++; }
                        assertEquals((double) referenceClock,time,1e-10,"Explicit binary32 reference clock");
                    }
                    writer.write(time+","+row.time()+","+(time < 0. ? "STARTUP_HELD_INITIAL" : row.kind()));
                    for (double value : row.values().values()) { assertTrue(Double.isFinite(value)); writer.write(","+value); }
                    for (double value : expected.values()) writer.write(","+value); writer.write("\n");
                    for (String key : maximum.keySet()) {
                        String observed = key.startsWith("STATE") ? "PUBLISHED"+key.substring(5) : key;
                        assertTrue(row.values().containsKey(observed),key); double value = expected.get(key), error = row.values().get(observed)-value;
                        if (Math.abs(error) > maximum.get(key)) { maximum.put(key,Math.abs(error)); at.put(key,time); }
                        squared.put(key,squared.get(key)+error*error); low.put(key,Math.min(low.get(key),value)); high.put(key,Math.max(high.get(key),value));
                    }
                }
            }
            assertEquals(reference.size(),actual.frames.size()+startups);
            var norms = new LinkedHashMap<String,Object>();
            for (String key : List.of("V1","V2","P_PU","Q_PU")) {
                double range = high.get(key)-low.get(key), rmse = Math.sqrt(squared.get(key)/reference.size());
                var norm = new LinkedHashMap<String,Object>(); norm.put("rmse",rmse); norm.put("reference_range",range);
                norm.put("range_nrmse",range == 0. ? null : rmse/range); norms.put(key,norm);
            }
            var result = new LinkedHashMap<String,Object>(); result.put("maximum_absolute_error",maximum); result.put("time_of_maximum",at);
            result.put("norms",norms); result.put("rotor_rmse_deg",Math.sqrt(squared.get("ANGLE_DEG")/reference.size()));
            result.put("profile",entry.path("profile").asText()); result.put("step_s",step);
            result.put("prefault_relative_state_gate",prefaultRelativeStates);
            var governor = (PsseHyg3Governor) actual.algorithm.getNetwork().getMachine("Bus1-mach1").getGovernor();
            result.put("backlash_events",compareBacklashEvents(reference,actual,step,governor.getEffectiveBacklashWidth(),maximum.get("STATE4")));
            result.put("qualification",prefaultRelativeStates
                    ?"Paired nonzero-prefault state gates, absolute zero-state/boundary gates and discrete-event checks"
                    :"Per-channel absolute/state and discrete-event regression gates; strict range-normalized acceptance remains diagnostic");
            diagnostics.put(tag,result);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(),diagnostics);
            for (var row : actual.frames) {
                assertTrue(row.values().get("STATE3") <= governor.getData().getVelopen()+1e-12);
                assertTrue(row.values().get("STATE3") >= governor.getData().getVelclose()-1e-12);
                assertTrue(row.values().get("STATE4") <= governor.getEffectivePmax()+1e-12);
                assertTrue(row.values().get("STATE4") >= governor.getEffectivePmin()-1e-12);
            }
            assertTrue((double) result.get("rotor_rmse_deg") < .0033,tag+" rotor RMSE "+result.get("rotor_rmse_deg"));
            checkAbsoluteEnvelope(tag,maximum,squared,reference,staggered,step,prefaultRelativeStates);
            if (staggered) assertTrue(maximum.get("STATE0") < 5e-7,
                    tag+" input-filter event history error "+maximum.get("STATE0"));
        }
    }

    private record BacklashJump(long tick,double before,double after) { }

    private static List<BacklashJump> jumps(SortedMap<Long,Double> values,double width) {
        var result=new ArrayList<BacklashJump>(); double previous=values.get(values.firstKey());
        for (var point : values.entrySet()) {
            if (Math.abs(point.getValue()-previous)>width*.5) result.add(new BacklashJump(point.getKey(),previous,point.getValue()));
            previous=point.getValue();
        }
        return result;
    }

    private static Map<String,Object> compareBacklashEvents(List<Map<String,Double>> reference,Hyg3SharedSmibTest.Recording actual,
            double h,double width,double gateError) {
        var expected=new TreeMap<Long,Double>(); var observed=new TreeMap<Long,Double>();
        for (var row : reference) if (row.get("time_s")>=0.) expected.put(Math.round(row.get("time_s")/h),row.get("VAR2"));
        for (var row : actual.frames) observed.put(Math.round(row.time()/h),row.values().get("VAR2"));
        if (width==0.) {
            for (var point : expected.entrySet()) assertEquals(point.getValue(),observed.get(point.getKey()),1e-7,"Inactive input memory");
            return Map.of("events",List.of(),"opposite_event_side_rows",0);
        }
        var a=jumps(expected,width); var b=jumps(observed,width); assertEquals(a.size(),b.size(),"Backlash releases/reversals");
        var events=new ArrayList<Map<String,Object>>(); double allowance=gateError+.2*h+1e-6;
        for (int i=0;i<a.size();i++) {
            var left=a.get(i); var right=b.get(i); assertTrue(Math.abs(left.tick-right.tick)<=1,"Backlash event displacement exceeds one physical step");
            assertEquals(Math.signum(left.after-left.before),Math.signum(right.after-right.before),0.,"Backlash event direction");
            assertEquals(left.before,right.before,allowance,"Pre-release input reference");
            assertEquals(left.after,right.after,allowance,"Accepted release input reference");
            events.add(Map.of("reference_time_s",left.tick*h,"actual_time_s",right.tick*h,
                    "reference_before",left.before,"actual_before",right.before,"reference_after",left.after,"actual_after",right.after));
        }
        int opposite=0;
        for (var point : expected.entrySet()) {
            boolean differentSide=false;
            for (int i=0;i<a.size();i++) if ((point.getKey()>=a.get(i).tick)!=(point.getKey()>=b.get(i).tick)) differentSide=true;
            if (differentSide) opposite++;
            else assertEquals(point.getValue(),observed.get(point.getKey()),allowance,"Input history on the same accepted backlash branch");
        }
        return Map.of("events",events,"opposite_event_side_rows",opposite);
    }

    private static void checkAbsoluteEnvelope(String tag,Map<String,Double> maximum,Map<String,Double> squared,
            List<Map<String,Double>> reference,boolean staggered,double h,boolean prefaultRelativeStates) {
        boolean fine=h<.001;
        // Existing absolute envelopes still govern zero-prefault states and every physical boundary.
        double[] states=staggered ? (fine
                ? new double[]{3e-7,2.2e-6,6e-7,4e-5,2.5e-5,1.8e-5,2e-5,7e-7,4e-6}
                : new double[]{3e-7,2.6e-6,5e-7,7e-6,2.6e-6,2e-5,2.2e-6,2.2e-6,1.3e-5}) : (fine
                ? new double[]{1.3e-3,1.3e-4,4.5e-5,1.4e-3,2.6e-5,2.2e-5,2.1e-5,8e-7,4e-6}
                : new double[]{9e-3,1.1e-3,2.7e-3,.01,1.9e-4,2.2e-4,1.2e-4,3.6e-6,3e-5});
        for (int i=0;i<states.length;i++) {
            String key="STATE"+i;double prefault=Math.abs(reference.getFirst().get(key));
            if(prefaultRelativeStates&&Double.isFinite(prefault)&&prefault>0) {
                double rmse=Math.sqrt(squared.get(key)/reference.size());
                assertTrue(maximum.get(key)<.005*prefault&&rmse<.0001*prefault,
                        tag+" "+key+" peak "+maximum.get(key)+" / "+(.005*prefault)
                                +" and RMSE "+rmse+" / "+(.0001*prefault));
            } else assertTrue(maximum.get(key)<states[i],tag+" "+key+" "+maximum.get(key));
        }
        double[] variables=staggered ? (fine ? new double[]{2.2e-6,2e-7,2.5e-5,3.5e-5} : new double[]{2.6e-6,3.7e-7,2.6e-6,4.5e-6})
                : (fine ? new double[]{.0038,2.5e-7,2.7e-5,7.3e-5} : new double[]{.025,3.7e-6,1.8e-4,6e-4});
        int[] keys={0,1,3,4}; for (int i=0;i<keys.length;i++) assertTrue(maximum.get("VAR"+keys[i])<variables[i],tag+" VAR"+keys[i]);
        for (String key : List.of("V1","V2")) assertTrue(maximum.get(key)<6e-6,tag+" "+key);
        assertTrue(maximum.get("P_PU")<4.3e-4,tag+" active power"); assertTrue(maximum.get("Q_PU")<4.5e-5,tag+" reactive power");
    }

    private static double maximum(List<Map<String,Double>> rows,String key) { return rows.stream().mapToDouble(row->row.get(key)).max().orElseThrow(); }
    private static double minimum(List<Map<String,Double>> rows,String key) { return rows.stream().mapToDouble(row->row.get(key)).min().orElseThrow(); }
}
