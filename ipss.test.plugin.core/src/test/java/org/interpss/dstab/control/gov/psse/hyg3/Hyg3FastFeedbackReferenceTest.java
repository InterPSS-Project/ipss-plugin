package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reference quality and combined-bypass controls are distinct from trajectory acceptance. */
public class Hyg3FastFeedbackReferenceTest {
    private static final TestConfig CONFIG=TestConfig.load(
            "/test-config/ipss.test.plugin.core.json","Hyg3FastFeedbackReferenceTest");
    @Test void coldRepeatsPreserveFiniteCapturesAndExplicitNonfiniteRejections(@TempDir Path staged)throws Exception{
        var fixture=new Hyg3ReferenceFixture(staged,CONFIG);int finite=0,rejected=0;
        for(var entry:fixture.matrix()){
            if(!entry.path("repeat").asText().equals("a"))continue;
            String tag=entry.path("tag").asText(),other=tag.substring(0,tag.length()-1)+"b";
            var a=fixture.captureManifest(tag);var b=fixture.captureManifest(other);
            assertEquals(a.path("status"),b.path("status"));
            assertEquals(a.path("entered_constants"),a.path("initialized_constants"));
            assertEquals(a.path("entered_constants"),b.path("entered_constants"));
            if(a.path("status").asText().equals("complete")){
                assertTrue(a.path("finite_measurements").asBoolean());
                var p=fixture.stage(tag);var q=fixture.stage(other);
                assertArrayEquals(Files.readAllBytes(p.resolve("native.csv")),Files.readAllBytes(q.resolve("native.csv")));
                finite++;
            }else{
                assertEquals("rejected-nonfinite",a.path("status").asText());
                assertFalse(a.path("finite_measurements").asBoolean());assertFalse(b.path("finite_measurements").asBoolean());
                assertFalse(a.path("files").has("native.csv"));assertFalse(b.path("files").has("native.csv"));
                assertTrue(a.path("files").has("native.out"));assertTrue(b.path("files").has("native.out"));
                rejected++;
            }
        }
        assertEquals(12,finite);assertEquals(8,rejected);
    }
    @Test void combinedInputBypassRetainsFourIntervalPreparationWhereReferenceRunsRemainFinite(@TempDir Path staged)throws Exception{
        var fixture=new Hyg3ReferenceFixture(staged,CONFIG);int pairs=0;
        for(var entry:fixture.matrix()){
            if(!entry.path("repeat").asText().equals("a")||!entry.path("profile").asText().equals("input-derivative-bypass"))continue;
            String tag=entry.path("tag").asText();
            if(!fixture.captureManifest(tag).path("status").asText().equals("complete"))continue;
            var a=Hyg3StateConformanceTest.rows(fixture.stage(tag).resolve("native.csv"));
            var b=Hyg3StateConformanceTest.rows(fixture.stage(tag.replace("input-derivative-bypass","input-derivative-explicit")).resolve("native.csv"));
            assertEquals(a.size(),b.size());
            for(int i=0;i<a.size();i++)for(String key:a.get(i).keySet())
                assertEquals(a.get(i).get(key),b.get(i).get(key),key.equals("time_s")?0.:3e-7,tag+" "+key+" row "+i);
            pairs++;
        }
        assertEquals(3,pairs);
    }
    @Test void finiteTerminalOutputsDoNotQualifyAnExplosiveInternalPidState(@TempDir Path staged)throws Exception{
        var fixture=new Hyg3ReferenceFixture(staged,CONFIG);
        String tag="m1-derivative-filter-one-step-h0p00416666666667-t0p5-a";
        var rows=Hyg3StateConformanceTest.rows(fixture.stage(tag).resolve("native.csv"));
        double largest=0.;
        for(var row:rows){
            for(double value:row.values())assertTrue(Double.isFinite(value));
            largest=Math.max(largest,Math.abs(row.get("STATE2")));
            assertTrue(Math.abs(row.get("PMECH"))<1.);
        }
        assertTrue(largest>1e6,"A finite-only check must not label this internal trajectory accepted");
        System.out.println("HYG3 finite but unqualified short-time reference compensation peak="+largest);
    }
}
