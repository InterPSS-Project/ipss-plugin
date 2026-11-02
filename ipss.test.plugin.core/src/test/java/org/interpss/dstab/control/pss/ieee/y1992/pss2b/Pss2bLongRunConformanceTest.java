package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Complete production-interval and half-interval independent SMIB trajectories. */
public class Pss2bLongRunConformanceTest {
    @TempDir Path staged;
    private Pss2bReferenceFixture references() {
        return Pss2bReferenceFixture.longRun(staged);
    }

    @Test void publishedLongRunStatesReconstructEveryOutput() throws Exception {
        IpssCorePlugin.init();
        var references = references();
        for (var entry : references.matrix()) {
            if (!entry.get("repeat").asText().equals("a")) continue;
            var root = references.stage("pss2b-native-" + entry.get("tag").asText());
            Pss2bReferenceReplayTest.reconstructPublishedOutput(root);
        }
    }

    @Test void derivativeInputReplayConvergesWhenTheProductionStepIsHalved() throws Exception {
        var references = references();
        double[] coarse = Pss2bReferenceReplayTest.replay(references.stage("pss2b-native-long-quarter-derivative-a-20261102"));
        double[] fine = Pss2bReferenceReplayTest.replay(references.stage("pss2b-native-long-half-quarter-derivative-a-20261102"));
        for (int channel = 0; channel < fine.length; channel++)
            assertTrue(fine[channel] <= .75*coarse[channel]+1e-8,
                    "Recorded-boundary state/output convergence, channel="+channel);
    }

    @Test void independentFirstFaultUpdateDistinguishesTenExciterStepsFromOne() throws Exception {
        var references = references();
        for (var entry : references.matrix()) {
            if (!entry.get("repeat").asText().equals("a")) continue;
            var root = references.stage("pss2b-native-" + entry.get("tag").asText());
            var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            var rows = Pss2bReferenceReplayTest.rows(root.resolve("native.csv"));
            double step = manifest.get("step_s").asDouble();
            double tau = manifest.get("components").get("EXC").get("actual_constants").get(0).asDouble();
            int after = -1;
            for (int i = 1; i < rows.size()-1; i++)
                if (Math.abs(rows.get(i).get("time_s")-.05) < step/10
                        && rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s"))) after = i;
            assertTrue(after > 0, "Retain both fault application sides");
            double initial = rows.get(after).get("EXC_VMEAS");
            assertEquals(initial, rows.get(after-1).get("EXC_VMEAS"), 0.);
            double voltage = rows.get(after).get("V_BUS1");
            double expected = rows.get(after+1).get("EXC_VMEAS");
            double state = initial, previousRate = 0.;
            for (int local = 0; local < 10; local++) {
                double rate = (voltage-state)/tau;
                if (local == 0) rate *= .5;
                state += step/10 * (1.5*rate-.5*previousRate);
                previousRate = rate;
            }
            assertEquals(expected, state, 8e-8, "Independent first-fault transducer recurrence with ten local steps");
            double single = initial + .75*step*(voltage-initial)/tau;
            assertTrue(Math.abs(single-expected) > .002, "Single-step event recurrence must remain distinguishable");
        }
    }
    static java.util.stream.Stream<Arguments> profiles(){
        return java.util.stream.Stream.of("quarter","half-quarter").flatMap(grid->
                java.util.stream.Stream.of("speed","derivative","voltage-limits","remote-frequency").flatMap(name->
                        java.util.stream.Stream.of(false,true).map(shared->Arguments.of(grid,name,shared))));
    }
    @ParameterizedTest(name="{0}, {1}, staggered={2}") @MethodSource("profiles")
    void tenSecondFaultPreservesCompleteBoundaryAndControllerObservations(String grid,String name,boolean staggered)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var references=references();
        String tag="long-"+grid+"-"+name+"-a-20261102";
        com.fasterxml.jackson.databind.JsonNode selected=null;
        for(var entry:references.matrix())if(entry.get("tag").asText().equals(tag))selected=entry;
        assertNotNull(selected);assertEquals(10,selected.get("profile").get("duration").asInt());
        references.requireConstantPowerLoads("pss2b-native-"+tag);
        var root=references.stage("pss2b-native-"+tag);
        var output=Files.createTempDirectory(Files.createDirectories(Path.of("target/pss2b-long")),
                grid+"-"+name+"-"+(staggered?"staggered":"euler")+"-");
        Pss2bCoupledBoundaryConformanceTest.run(root,selected.get("profile"),output,staggered,10);
    }
}
