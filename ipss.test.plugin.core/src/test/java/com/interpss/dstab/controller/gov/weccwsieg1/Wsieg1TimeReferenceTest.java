package com.interpss.dstab.controller.gov.weccwsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.IpssCorePlugin;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent two-interval preparation boundary, cold controls and complete coupled trajectories. */
public class Wsieg1TimeReferenceTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1TimeReferenceTest");
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}

    @Test void allColdPairsAreFiniteReproducibleAndKeepEnteredConstants(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);var pairs=new LinkedHashMap<String,Map<String,String>>();
        for(var entry:fixture.matrix()) {
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);var m=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            assertEquals(m.path("entered_constants"),m.path("initialized_constants"));byte[] bytes=Files.readAllBytes(root.resolve("native.csv"));
            pairs.computeIfAbsent(tag.substring(0,tag.lastIndexOf('-')),key->new HashMap<>()).put(entry.path("repeat").asText(),Wsieg1ReferenceFixture.sha(bytes));
            var rows=Wsieg1StateConformanceTest.rows(bytes);assertTrue(rows.size()>100);
            for(var row:rows)for(int i=0;i<6;i++)assertTrue(Math.abs(row.get("STATE"+i))<2.,tag+" finite internal response");
        }
        assertEquals(32,pairs.size());for(var pair:pairs.values()){assertEquals(Set.of("a","b"),pair.keySet());assertEquals(pair.get("a"),pair.get("b"));}
    }

    @Test void nativePreparationBoundaryMatchesExplicitBypassControlsAcrossEveryChannel(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged.resolve("fast"),CONFIG);
        var bypass=new Wsieg1ReferenceFixture(staged.resolve("bypass"),TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1BypassLimitConformanceTest"));
        int collapsed=0,retained=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            String profile=entry.path("profile").asText();if(profile.startsWith("valve"))continue;
            Path root=fixture.stage(entry.path("tag").asText());var m=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            double h=entry.path("step_s").asDouble();boolean speed=profile.startsWith("speed");
            double entered=m.path("entered_constants").get(speed?1:8).asDouble();boolean preparedBypass=entered<=2.*(float)h;
            String control=speed?"speed-bypass":profile.startsWith("all-turbines")?"all-turbines-bypass":"first-turbine-bypass";
            var controlEntry=bypass.matrix().stream().filter(e->e.path("profile").asText().equals(control)&&e.path("repeat").asText().equals("a")&&e.path("step_s").asDouble()==h).findFirst().orElseThrow();
            var actual=Wsieg1StateConformanceTest.rows(Files.readAllBytes(root.resolve("native.csv")));
            var expected=Wsieg1StateConformanceTest.rows(Files.readAllBytes(bypass.stage(controlEntry.path("tag").asText()).resolve("native.csv")));
            assertEquals(expected.size(),actual.size());double maximum=0.;
            for(int i=0;i<actual.size();i++)for(String key:actual.get(i).keySet())maximum=Math.max(maximum,Math.abs(actual.get(i).get(key)-expected.get(i).get(key)));
            if(preparedBypass){assertEquals(0.,maximum,0.,profile+" all-channel bypass identity");collapsed++;}
            else {assertTrue(maximum>1e-6,profile+" distinguish retained positive lag");retained++;}
        }
        assertEquals(20,collapsed);assertEquals(8,retained);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void completeFastTimeTrajectoriesUseTheSameStrictBoundaryGates(boolean staggered,@TempDir Path staged)throws Exception {
        Wsieg1BypassLimitConformanceTest.compareCoupledTrajectories(new Wsieg1ReferenceFixture(staged,CONFIG),staggered,"wsieg1-fast-times-",160);
    }
}
