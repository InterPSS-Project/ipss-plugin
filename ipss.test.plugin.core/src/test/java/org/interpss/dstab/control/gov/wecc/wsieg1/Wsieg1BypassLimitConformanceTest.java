package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent bypass observations and active-limit trajectories; failed gates remain visible. */
public class Wsieg1BypassLimitConformanceTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1BypassLimitConformanceTest");
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}

    @Test void coldCapturesPreserveParametersAndExerciseBypassesAndLimits(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);var pairs=new LinkedHashMap<String,Map<String,String>>();
        for(var entry:fixture.matrix()) {
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);
            var manifest=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
            byte[] bytes=Files.readAllBytes(root.resolve("native.csv"));var rows=Wsieg1StateConformanceTest.rows(bytes);
            pairs.computeIfAbsent(tag.substring(0,tag.lastIndexOf('-')),key->new HashMap<>()).put(entry.path("repeat").asText(),Wsieg1ReferenceFixture.sha(bytes));
            var c=manifest.path("initialized_constants");String profile=entry.path("profile").asText();
            assertTrue(rows.size()>100);assertTrue(rows.stream().allMatch(r->r.get("VAR2")==0.));
            if(profile.equals("speed-bypass")) {
                double excursion=0.;
                for(var row:rows) {
                    assertEquals(c.get(0).asDouble()*row.get("VAR3"),row.get("STATE0"),5e-10,tag);
                    excursion=Math.max(excursion,Math.abs(row.get("STATE0")));
                }
                assertTrue(excursion>.009,"Discriminate zero-filled observations");
            }
            for(int i=0;i<4;i++)if(c.get(8+3*i).asDouble()==0.)for(var row:rows)
                assertEquals(row.get(i==0?"VAR7":"STATE"+(i+1)),row.get("STATE"+(i+2)),0.,tag);
            if(profile.equals("valve-position")) {
                assertEquals(c.get(7).asDouble(),rows.stream().mapToDouble(r->r.get("STATE1")).min().orElseThrow(),1e-12);
                assertEquals(c.get(6).asDouble(),rows.stream().mapToDouble(r->r.get("STATE1")).max().orElseThrow(),1e-12);
            }
            if(profile.equals("valve-rates")) {
                double h=entry.path("step_s").asDouble();int rising=0,falling=0;
                for(int i=1;i<rows.size();i++) {
                    var a=rows.get(i-1);var b=rows.get(i);double time=a.get("time_s");
                    if(b.get("time_s")<=time||time<.06||time>.58)continue;
                    double rate=time<.28?c.get(4).asDouble():time>.32?c.get(5).asDouble():Double.NaN;
                    if(!Double.isFinite(rate))continue;
                    // The independent recorder exposes binary32 accumulated states.
                    assertEquals((double)(float)(a.get("STATE1")+h*rate),b.get("STATE1"),1e-12,tag);
                    if(rate>0)rising++;else falling++;
                }
                assertTrue(rising>20&&falling>20,"Both rate limits are active");
            }
        }
        assertEquals(10,pairs.size());for(var pair:pairs.values()){assertEquals(Set.of("a","b"),pair.keySet());assertEquals(pair.get("a"),pair.get("b"));}
    }

    @Test void recordedBypassInputPublishesWithoutMutatingIntegratorState(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);
        for(var entry:fixture.matrix()) {
            if(!entry.path("profile").asText().equals("speed-bypass")||!entry.path("repeat").asText().equals("a"))continue;
            Path root=fixture.stage(entry.path("tag").asText());var context=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(entry.path("step_s").asDouble());algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
            var g=(Wsieg1Governor)context.getDStabilityNet().getMachine("Bus1-mach1").getGovernor();double[] published=new double[6];
            var states=g.getIntegrationStates();var histories=g.getIntegrationHistories();
            for(var row:Wsieg1StateConformanceTest.rows(Files.readAllBytes(root.resolve("native.csv")))) {
                g.getMachine().setSpeed(1.+row.get("VAR3"));g.readPublishedState(published,0);
                assertEquals(row.get("STATE0"),published[0],5e-10);
                assertEquals(states,g.getIntegrationStates());assertEquals(histories,g.getIntegrationHistories());
            }
        }
    }

    @Test void independentValveRateRampAccumulatesInBinary32(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);int checked=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("profile").asText().equals("valve-position")
                    ||!entry.path("repeat").asText().equals("a")
                    ||entry.path("step_s").asDouble()>=.001)continue;
            Path root=fixture.stage(entry.path("tag").asText());
            var manifest=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            double h=entry.path("step_s").asDouble();
            double rate=manifest.path("initialized_constants").get(4).asDouble();
            var rows=Wsieg1StateConformanceTest.rows(Files.readAllBytes(root.resolve("native.csv")));
            double first=Double.NaN,last=Double.NaN;int increments=0;
            for(int i=1;i<rows.size();i++) {
                var before=rows.get(i-1);var after=rows.get(i);
                double time=before.get("time_s");
                if(time<.051||after.get("time_s")>.0601)continue;
                double previous=before.get("STATE1"),actual=after.get("STATE1");
                assertEquals((double)(float)(previous+h*rate),actual,1e-12,
                        "Independent valve state retains each binary32 rate-limited increment");
                if(increments==0)first=previous;
                last=actual;increments++;
            }
            assertTrue(increments>=15,"A sustained unclipped ramp must be present");
            assertTrue(last-(first+increments*h*rate)>2e-7,
                    "Repeated binary32 rounding must be distinguishable from double accumulation");
            checked++;
        }
        assertEquals(1,checked);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void completeCoupledTrajectoriesRetainStrictBoundaryGates(boolean staggered,@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);
        compareCoupledTrajectories(fixture,staggered,"wsieg1-bypass-",50);
    }

    static void compareCoupledTrajectories(Wsieg1ReferenceFixture fixture,boolean staggered,String prefix,int gateCount)throws Exception {
        compareCoupledTrajectories(fixture,staggered,prefix,gateCount,0);
    }
    static void compareCoupledTrajectories(Wsieg1ReferenceFixture fixture,boolean staggered,String prefix,int gateCount,int startupSteps)throws Exception {
        Path output=Files.createTempDirectory(Path.of("target"),prefix+(staggered?"staggered-":"euler-"));
        System.out.println("WSIEG1 bypass/limit comparison "+output.getFileName());var gates=new ArrayList<Executable>();
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            if(entry.has("status")&&!entry.path("status").asText().equals("complete"))continue;
            String tag=entry.path("tag").asText();Path input=fixture.stage(tag);double step=entry.path("step_s").asDouble();
            var manifest=new ObjectMapper().readTree(input.resolve("manifest.json").toFile());
            var expected=Wsieg1StateConformanceTest.rows(Files.readAllBytes(input.resolve("native.csv")));
            var events=new ArrayList<Wsieg1SharedSmibTest.ReferenceEvent>();
            for(var point:manifest.path("reference_events"))events.add(new Wsieg1SharedSmibTest.ReferenceEvent(point.get(0).asDouble(),point.get(1).asDouble()));
            double duration=Math.round(expected.getLast().get("time_s")/step)*step;
            var run=Wsieg1SharedSmibTest.simulate(input.resolve("network.raw"),input.resolve("dynamic.dyr"),staggered,entry.path("event").asText(),step,duration,events,algorithm->{},startupSteps);
            var maximum=new LinkedHashMap<String,Double>();var squares=new LinkedHashMap<String,Double>();var times=new LinkedHashMap<String,Double>();
            for(String key:expected.getFirst().keySet())if(!key.equals("time_s")&&!key.equals("VAR2")){maximum.put(key,0.);squares.put(key,0.);times.put(key,0.);}
            int frame=0,startups=0,unchangedRunBoundaries=0;float clock=0f;long tick=0;
            Map<String,Double> previousReference=null;
            Wsieg1SharedSmibTest.Frame previousFrame=null;
            try(var stream=new GZIPOutputStream(BenchmarkOutputGuard.newOutputStream(output.resolve(tag+".csv.gz"),16L*1024*1024));var writer=new BufferedWriter(new OutputStreamWriter(stream,StandardCharsets.UTF_8))) {
                writer.write("reference_time_s,actual_time_s,sample_kind,"+String.join(",",run.initial.values().keySet()));
                for(String key:expected.getFirst().keySet())writer.write(",REFERENCE."+key);writer.write("\n");
                for(var ref:expected) {
                    double time=ref.get("time_s");boolean held=time<0&&startupSteps==0;
                    // A flat reference capture still stops/restarts RUN at the diagnostic
                    // boundaries. Retain both identical observations of the same state;
                    // restarting recording must not advance the numerical solution.
                    boolean repeatedFlatBoundary=!held&&entry.path("event").asText().equals("flat")
                            &&previousReference!=null&&time==previousReference.get("time_s");
                    if(repeatedFlatBoundary){assertEquals(previousReference,ref,"Flat RUN boundary changes no measurements");unchangedRunBoundaries++;}
                    var row=held?run.initial:repeatedFlatBoundary?previousFrame:run.frames.get(frame++);if(held)startups++;
                    else if(time<0) {
                        long negativeTick=Math.round(row.time()/step);
                        assertTrue(negativeTick<0&&negativeTick>=-startupSteps);
                        assertEquals((double)(float)(negativeTick*step),time,1e-10,"Explicit startup reference clock");
                    } else {
                        long next=Math.round(row.time()/step);assertTrue(next>=tick&&next-tick<=1);assertEquals(next*step,row.time(),1e-10);
                        while(tick<next){clock+=(float)step;tick++;}assertEquals((double)clock,time,1e-10,"Explicit binary32 reference clock");
                    }
                    writer.write(time+","+row.time()+","+(held?"STARTUP_HELD_INITIAL":row.kind()));for(double v:row.values().values())writer.write(","+v);for(double v:ref.values())writer.write(","+v);writer.write("\n");
                    for(String key:maximum.keySet()) {
                        String observed=key.startsWith("STATE")?"PUBLISHED"+key.substring(5):key;double error=row.values().get(observed)-ref.get(key);assertTrue(Double.isFinite(error));
                        if(Math.abs(error)>maximum.get(key)){maximum.put(key,Math.abs(error));times.put(key,time);}
                        squares.put(key,squares.get(key)+error*error);
                    }
                    previousReference=ref;previousFrame=row;
                }
            }
            assertEquals(expected.size(),run.frames.size()+startups+unchangedRunBoundaries);var normalized=new LinkedHashMap<String,Double>();var ranges=new LinkedHashMap<String,Double>();
            for(String key:expected.getFirst().keySet())if(key.matches("V[0-9]+")||key.startsWith("P_PU")||key.startsWith("Q_PU")) {
                double low=expected.stream().mapToDouble(r->r.get(key)).min().orElseThrow(),high=expected.stream().mapToDouble(r->r.get(key)).max().orElseThrow();
                double rmse=Math.sqrt(squares.get(key)/expected.size());ranges.put(key,high-low);
                if(high>low) {
                    double nrmse=rmse/(high-low);normalized.put(key,nrmse);
                    if(entry.path("event").asText().equals("flat"))
                        gates.add(()->assertTrue(maximum.get(key)<1e-6,tag+" "+key+" flat-run absolute error "+maximum.get(key)));
                    else gates.add(()->assertTrue(nrmse<.001,tag+" "+key+" range NRMSE "+nrmse));
                } else {
                    normalized.put(key,null);
                    gates.add(()->assertTrue(maximum.get(key)<1e-6,tag+" "+key+" constant-reference absolute error "+maximum.get(key)));
                }
            }
            var rotorByChannel=new LinkedHashMap<String,Double>();
            for(String key:expected.getFirst().keySet())if(key.startsWith("ANGLE_DEG")) {
                double rmse=Math.sqrt(squares.get(key)/expected.size());rotorByChannel.put(key,rmse);
                gates.add(()->assertTrue(rmse<.01,tag+" "+key+" rotor RMSE "+rmse));
            }
            double rotor=rotorByChannel.get("ANGLE_DEG");
            var result=new LinkedHashMap<String,Object>();result.put("tag",tag);result.put("staggered",staggered);result.put("samples",expected.size());
            result.put("maximum_absolute_error",maximum);result.put("time_of_maximum_error",times);result.put("range_nrmse",normalized);result.put("reference_ranges",ranges);result.put("rotor_rmse_deg",rotor);
            result.put("unmapped_reference_diagnostics",List.of("VAR2"));
            result.put("integrated_startup_steps",startupSteps);
            result.put("constant_reference_maximum_absolute_error_limit",1e-6);
            result.put("rotor_rmse_by_channel",rotorByChannel);
            result.put("flat_run_uses_absolute_error",entry.path("event").asText().equals("flat"));
            result.put("retained_unchanged_run_boundary_duplicates",unchangedRunBoundaries);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve(tag+".json").toFile(),result);
        }
        assertEquals(gateCount,gates.size());assertAll("Independent boundary accuracy, including small-response ranges",gates);
    }
}
