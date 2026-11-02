package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.IpssCorePlugin;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent zero-time controls distinguish four outer intervals from bypass and fixed time. */
public class Hyg3DerivativePreparationConformanceTest {
    private static final TestConfig CONFIG = TestConfig.load(
            "/test-config/ipss.test.plugin.core.json", "Hyg3DerivativePreparationConformanceTest");

    @Test void coldRepeatsIdentifyZeroTimePreparationAcrossModesAndNetworkSteps(@TempDir Path staged) throws Exception {
        var fixture = new Hyg3ReferenceFixture(staged, CONFIG);
        int pairs = 0, zeroControls = 0;
        for (var entry : fixture.matrix()) {
            if (!entry.path("repeat").asText().equals("a")) continue;
            String tag = entry.path("tag").asText(); var root = fixture.stage(tag);
            var repeat = fixture.stage(tag.substring(0,tag.length()-1)+"b");
            assertArrayEquals(Files.readAllBytes(root.resolve("native.csv")),Files.readAllBytes(repeat.resolve("native.csv")));
            var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
            pairs++;
            if (!entry.path("profile").asText().equals("derivative-filter-zero")) continue;
            var rows = Hyg3StateConformanceTest.rows(root.resolve("native.csv"));
            var control = Hyg3StateConformanceTest.rows(fixture.stage(tag.replace("filter-zero","filter-four-steps")).resolve("native.csv"));
            assertEquals(rows.size(),control.size()); double maximum = 0.;
            for (int i=0;i<rows.size();i++) for (String key:rows.get(i).keySet()) {
                double error=Math.abs(rows.get(i).get(key)-control.get(i).get(key));
                maximum=Math.max(maximum,error);
                assertEquals(control.get(i).get(key),rows.get(i).get(key),key.startsWith("STATE")?1e-7:0.,tag+" "+key+" row "+i);
            }
            double step=entry.path("step").asDouble();
            String alternative=step<.0005?"fixed-small":"two-steps";
            var wrong=Hyg3StateConformanceTest.rows(fixture.stage(tag.replace("filter-zero","filter-"+alternative)).resolve("native.csv"));
            double separation=0.;
            for(int i=0;i<rows.size();i++) separation=Math.max(separation,Math.abs(rows.get(i).get("STATE2")-wrong.get(i).get("STATE2")));
            assertTrue(separation>1e-4,"Control must distinguish fixed-time/two-interval alternatives");
            System.out.println("HYG3 zero-time control "+tag+" four-interval maximum="+maximum+" alternative separation="+separation);
            zeroControls++;
        }
        assertEquals(18,pairs); assertEquals(6,zeroControls);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothMethodsRetainCompleteZeroTimeStateAndBoundaryGates(boolean staggered,@TempDir Path staged) throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        var fixture=new Hyg3ReferenceFixture(staged,CONFIG);
        var output=Files.createTempDirectory(Path.of("target"),staggered?"hyg3-derivative-staggered-":"hyg3-derivative-euler-");
        Hyg3StateConformanceTest.compare(fixture,staggered,output,manifest->{
            boolean fine=manifest.path("settings").path("step").asDouble()<.001;
            double[] states=fine
                    ? staggered?new double[]{2.6e-7,6e-6,5.2e-5,1.9e-5,2.6e-6,7e-6,5.1e-6,1.5e-5,.00033}
                               :new double[]{2.6e-7,2.3e-5,5.2e-5,1.9e-5,2.6e-6,.00011,5.1e-6,2.8e-5,.0056}
                    : staggered?new double[]{7e-7,4.3e-5,8e-6,6.7e-5,4.2e-6,.00014,9.4e-7,4e-5,.0013}
                               :new double[]{4.5e-6,.00024,3.2e-5,.00024,9.5e-6,.00096,3.4e-6,.00016,.007};
            double[] boundaries=fine
                    ? staggered?new double[]{1.3e-5,1.3e-5,1.9e-5,8.3e-8,9.1e-5,2.6e-5,5.5e-6,8.3e-8,1e-7,2.6e-6,2.6e-5}
                               :new double[]{.00015,.0022,1.9e-5,1.8e-6,.0028,.00075,2.3e-5,1.8e-6,1e-7,2.6e-6,2.6e-5}
                    : staggered?new double[]{.00016,.00092,7.2e-6,2e-6,.0016,.00047,4.3e-5,2e-6,1e-7,4.3e-6,1.5e-5}
                               :new double[]{.002,.014,1.8e-5,1.6e-5,.028,.008,.00024,1.6e-5,1e-7,9.6e-6,3.5e-5};
            return new Hyg3StateConformanceTest.ComparisonLimits(states,boundaries);
        },entry->entry.path("profile").asText().equals("derivative-filter-zero"));
    }
}
