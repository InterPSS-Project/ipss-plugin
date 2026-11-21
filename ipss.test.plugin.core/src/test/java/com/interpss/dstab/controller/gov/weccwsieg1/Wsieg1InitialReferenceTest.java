package com.interpss.dstab.controller.gov.weccwsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.controller.gov.weccwsieg1.Wsieg1Governor;

/** Nonlinear initial bounds are checked independently; suspect starts are not trajectory acceptance. */
public class Wsieg1InitialReferenceTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1InitialReferenceTest");
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}

    @Test void coldQualityAndInitialCoordinatesReproduceWithoutChangingConstants(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);var pairs=new LinkedHashMap<String,Map<String,String>>();int rejected=0;
        for(var entry:fixture.matrix()) {
            String tag=entry.path("tag").asText(),profile=entry.path("profile").asText();
            String status=profile.endsWith("expansion")||profile.equals("initial-lower-block-conflict")?"rejected-quality":"complete";
            assertEquals(status,entry.path("status").asText());Path root=fixture.stage(tag,status);
            var m=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
            byte[] bytes=Files.readAllBytes(root.resolve("native.csv"));var rows=Wsieg1StateConformanceTest.rows(bytes);assertTrue(rows.size()>200);
            pairs.computeIfAbsent(tag.substring(0,tag.lastIndexOf('-')),key->new HashMap<>()).put(entry.path("repeat").asText(),Wsieg1ReferenceFixture.sha(bytes));
            var initial=rows.getFirst();double lower=initial.get("VAR5"),upper=initial.get("VAR4"),reference=initial.get("VAR0");
            assertEquals(Math.min(upper,Math.max(lower,reference)),initial.get("STATE1"),0.);
            assertEquals(initial.get("STATE1"),initial.get("VAR6"),0.);
            if(profile.contains("block"))assertTrue(Math.abs(reference-initial.get("STATE2"))>.03,"Valve position differs from turbine power");
            if(profile.endsWith("bypass")) {
                for(int i=2;i<6;i++)assertEquals(initial.get("PMECH"),initial.get("STATE"+i),0.);
                assertTrue(Math.abs(initial.get("PMECH")-initial.get("P_PU"))>.2,"An algebraic power jump can coexist with zero controller rates");
            }
            if(profile.equals("initial-upper-block-conflict")) {
                assertTrue(lower>upper);
                var reversal=rows.stream().filter(r->r.get("time_s")>.301&&r.get("time_s")<.36).toList();
                assertTrue(reversal.size()>6);
                for(int i=1;i<reversal.size();i++) {
                    double previous=reversal.get(i-1).get("STATE1"),current=reversal.get(i).get("STATE1");
                    assertTrue(current==lower||current==upper);
                    assertNotEquals(previous,current,"Conflicting effective limits alternate after the command reversal");
                }
                // The last command changes the valve derivative but no turbine
                // derivative. Their recurrence retains its history across it.
                int boundary=-1;
                for(int i=2;i<rows.size()-1;i++)if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))
                        &&Math.abs(rows.get(i).get("time_s")-.6)<entry.path("step_s").asDouble()/2.)boundary=i;
                assertTrue(boundary>1);var previous=rows.get(boundary-2);var current=rows.get(boundary);var next=rows.get(boundary+1);
                var c=m.path("initialized_constants");double slope=(c.get(28).asDouble()-c.get(26).asDouble())/(c.get(27).asDouble()-c.get(25).asDouble());
                java.util.function.ToDoubleFunction<Map<String,Double>> rate=r->
                        (c.get(26).asDouble()+slope*(r.get("VAR7")-c.get(25).asDouble())-r.get("STATE2"))/c.get(8).asDouble();
                double h=entry.path("step_s").asDouble(),now=rate.applyAsDouble(current),before=rate.applyAsDouble(previous);
                double retained=current.get("STATE2")+h*(1.5*now-.5*before);
                double smoothed=current.get("STATE2")+h*(.75*now+.25*before);
                assertEquals(retained,next.get("STATE2"),2e-7,"Unchanged derivative input preserves turbine integration history");
                assertTrue(Math.abs(smoothed-next.get("STATE2"))>1e-4,"Discriminate an unconditional event reset");
            }
            if(status.equals("rejected-quality")){assertTrue(Math.abs(reference-initial.get("STATE1"))>.1);rejected++;}
        }
        assertEquals(18,pairs.size());assertEquals(12,rejected);
        for(var pair:pairs.values()){assertEquals(Set.of("a","b"),pair.keySet());assertEquals(pair.get("a"),pair.get("b"));}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothInitializersPreservePublishedBoundsAndExposeNonEquilibrium(boolean staggered,@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag,entry.path("status").asText());
            var expected=Wsieg1StateConformanceTest.rows(Files.readAllBytes(root.resolve("native.csv"))).getFirst();
            var context=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
            var a=context.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(entry.path("step_s").asDouble());a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            var g=(Wsieg1Governor)context.getDStabilityNet().getMachine("Bus1-mach1").getGovernor();double[] entered=g.getConstants();
            a.setSolver(staggered?new org.interpss.dstab.algo.StaggeredEulerSolver(a):new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(a));
            assertTrue(a.initialization(),tag);double[] published=new double[6];g.readPublishedState(published,0);
            for(int i=0;i<6;i++)assertEquals(expected.get("STATE"+i),published[i],8e-8,tag+" initial STATE"+i);
            assertEquals(expected.get("VAR0"),g.getInitialReference(),8e-8,tag);assertEquals(expected.get("VAR4"),g.getEffectiveMaximum(),8e-8,tag);
            assertEquals(expected.get("VAR5"),g.getEffectiveMinimum(),8e-8,tag);assertEquals(expected.get("VAR6"),g.getBacklashInputHistory(),8e-8,tag);assertEquals(expected.get("VAR7"),g.getValveOutput(),8e-8,tag);
            assertEquals(expected.get("VAR0")-expected.get("STATE1"),g.getInitialValveLimitMismatch(),8e-8,tag);
            assertEquals(expected.get("PMECH"),g.getOutput(g.getMachine()),8e-8,tag);assertArrayEquals(entered,g.getConstants(),0.);
            if(entry.path("status").asText().equals("rejected-quality"))assertTrue(Math.abs(g.getStateDerivativeSnapshot().rates().get("Turbine 1"))>2.,tag);
            if(entry.path("profile").asText().endsWith("bypass")) {
                for(double rate:g.getStateDerivativeSnapshot().rates().values())assertEquals(0.,rate,0.);
                assertTrue(Math.abs(g.getInitialValveLimitMismatch())>.1);
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void acceptedReferenceRunsRetainCompleteCoupledDiagnostics(boolean staggered,@TempDir Path staged)throws Exception {
        Wsieg1BypassLimitConformanceTest.compareCoupledTrajectories(new Wsieg1ReferenceFixture(staged,CONFIG),staggered,"wsieg1-initial-startup-",60,2);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void conflictingLimitValveAndBacklashFollowEveryIndependentSample(boolean staggered,@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);int profiles=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a")||!entry.path("profile").asText().equals("initial-upper-block-conflict"))continue;
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);double h=entry.path("step_s").asDouble();
            var reference=Wsieg1StateConformanceTest.rows(Files.readAllBytes(root.resolve("native.csv")));
            var m=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            var events=new ArrayList<Wsieg1SharedSmibTest.ReferenceEvent>();
            for(var event:m.path("reference_events"))events.add(new Wsieg1SharedSmibTest.ReferenceEvent(event.get(0).asDouble(),event.get(1).asDouble()));
            var actual=Wsieg1SharedSmibTest.simulate(root.resolve("network.raw"),root.resolve("dynamic.dyr"),staggered,"reference-reversals",h,1.,events,a->{},2);
            assertEquals(reference.size(),actual.frames.size());
            if(staggered)assertEquals(3,((Wsieg1Governor)actual.algorithm.getNetwork().getMachine("Bus1-mach1").getGovernor()).staggeredInputDiscontinuityVersion());
            for(int i=0;i<reference.size();i++) {
                var expected=reference.get(i);var row=actual.frames.get(i);
                assertEquals(expected.get("STATE1"),row.values().get("PUBLISHED1"),8e-8,tag+" valve at "+i);
                assertEquals(expected.get("VAR6"),row.values().get("VAR6"),8e-8,tag+" backlash input at "+i);
                assertEquals(expected.get("VAR7"),row.values().get("VAR7"),8e-8,tag+" backlash output at "+i);
            }
            profiles++;
        }
        assertEquals(2,profiles);
    }
}
