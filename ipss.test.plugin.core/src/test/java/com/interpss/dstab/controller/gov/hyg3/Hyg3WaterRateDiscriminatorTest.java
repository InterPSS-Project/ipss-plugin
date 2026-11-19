package com.interpss.dstab.controller.gov.hyg3;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Moving-flow reference command separates hydraulic two-step updates from one-step Euler. */
public class Hyg3WaterRateDiscriminatorTest {
    private static final Path ARCHIVE=Path.of(
            "testData/private/verification-artifacts/hyg3-water-rate-20261105/captures.zip");
    private static final String SHA="ec0cac9bb198c70207b012d9ecd6d67bb6b0f18f77f3255efdf594eac6f59856";
    private static Hyg3ReferenceFixture fixture(Path staged) {
        return new Hyg3ReferenceFixture(staged,ARCHIVE,SHA);
    }

    @Test void coldPairedMovingFlowRejectsOneStepEuler(@TempDir Path staged) throws Exception {
        var source=fixture(staged);
        int profiles=0;
        for(var entry:source.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            String tag=entry.path("tag").asText();
            var first=source.stage(tag);
            var second=source.stage(tag.substring(0,tag.length()-1)+"b");
            assertArrayEquals(Files.readAllBytes(first.resolve("native.csv")),
                    Files.readAllBytes(second.resolve("native.csv")));
            var manifest=source.captureManifest(tag);
            var constants=manifest.path("initialized_constants");
            double h=manifest.path("settings").path("step").asDouble();
            double h0=constants.get(27).asDouble(),tw=constants.get(29).asDouble();
            assertEquals(.0005,h,0.);assertEquals(.02,tw,1e-8);
            var rows=Hyg3StateConformanceTest.rows(first.resolve("native.csv"));
            double euler=0,twoStep=0;int count=0,moving=0;
            for(int i=1;i+1<rows.size();i++) {
                var previous=rows.get(i-1);var left=rows.get(i);var right=rows.get(i+1);
                double time=left.get("time_s"),increment=right.get("time_s")-time;
                if(time<=.05||time>=1.||increment<=0.||Math.abs(increment-h)>1e-4*h
                        ||previous.get("time_s")>=time)continue;
                double flow=left.get("STATE6"),next=right.get("STATE6"),old=previous.get("STATE6");
                double gate=left.get("VAR3"),oldGate=previous.get("VAR3");
                double rate=(h0-Math.pow(flow/gate,2))/tw;
                double oldRate=(h0-Math.pow(old/oldGate,2))/tw;
                double observed=(float)(double)next;
                euler+=Math.abs((float)(flow+h*rate)-observed);
                twoStep+=Math.abs((float)(flow+h*(1.5*rate-.5*oldRate))-observed);
                if(next!=flow)moving++;
                count++;
            }
            assertEquals(1897,count);assertTrue(moving>1000);
            assertTrue(twoStep<.25*euler,tag+" two-step error "+twoStep+" vs Euler "+euler);
            profiles++;
        }
        assertEquals(2,profiles);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void completeReversalRetainsExistingBothMethodBoundaries(boolean staggered,@TempDir Path staged)
            throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path output=staged.resolve("comparison");Files.createDirectories(output);
        Hyg3LimitConformanceTest.compare(fixture(staged),staggered,output,entry->true);
        var diagnostics=new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(output.resolve("diagnostic.json").toFile());
        assertEquals(2,diagnostics.size());
        for(var iterator=diagnostics.fields();iterator.hasNext();) {
            var entry=iterator.next();
            var result=entry.getValue();
            for(String channel:new String[]{"V1","V2","P_PU","Q_PU"})
                assertTrue(result.path("norms").path(channel).path("range_nrmse").asDouble()<.001,
                        entry.getKey()+" "+channel);
            assertTrue(result.path("rotor_rmse_deg").asDouble()<.01,entry.getKey()+" rotor angle");
            assertTrue(result.path("maximum_absolute_error").path("STATE6").asDouble()<1e-5,
                    entry.getKey()+" hydraulic flow");
        }
    }
}
