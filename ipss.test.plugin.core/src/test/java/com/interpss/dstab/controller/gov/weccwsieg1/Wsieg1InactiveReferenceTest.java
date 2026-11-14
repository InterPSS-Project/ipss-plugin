package com.interpss.dstab.controller.gov.weccwsieg1;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
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

/** An initially inactive companion contributes no shaft power and owns no integration states. */
public class Wsieg1InactiveReferenceTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1InactiveReferenceTest");
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}

    @Test void coldControlsKeepTheSecondaryInactiveAndTheEnteredCoefficientsUnchanged(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);var pairs=new LinkedHashMap<String,Map<String,String>>();
        for(var entry:fixture.matrix()) {
            String tag=entry.path("tag").asText();Path input=fixture.stage(tag);
            var manifest=new ObjectMapper().readTree(input.resolve("manifest.json").toFile());
            assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"));
            byte[] data=Files.readAllBytes(input.resolve("native.csv"));
            pairs.computeIfAbsent(tag.substring(0,tag.lastIndexOf('-')),k->new HashMap<>()).put(entry.path("repeat").asText(),Wsieg1ReferenceFixture.sha(data));
            for(var row:Wsieg1StateConformanceTest.rows(data)) {
                assertEquals(0.,row.get("VAR1"),0.);
                for(String key:List.of("PMECH_LP","P_PU_LP","Q_PU_LP","ANGLE_DEG_LP","SPEED_LP"))assertEquals(0.,row.get(key),0.,tag+" "+key);
            }
        }
        assertEquals(4,pairs.size());for(var pair:pairs.values()){assertEquals(Set.of("a","b"),pair.keySet());assertEquals(pair.get("a"),pair.get("b"));}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void initializationDoesNotWaitForTheInactiveCompanion(boolean staggered,@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);int checked=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a"))continue;
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);
            var expected=Wsieg1StateConformanceTest.rows(Files.readAllBytes(root.resolve("native.csv"))).getFirst();
            var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
            var a=context.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(entry.path("step_s").asDouble());a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            a.setSolver(staggered?new org.interpss.dstab.algo.StaggeredEulerSolver(a):new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(a));assertTrue(a.initialization(),tag);
            var primary=context.getDStabilityNet().getMachine("Bus1-mach1");var secondary=context.getDStabilityNet().getMachine("Bus2-mach1");var g=(Wsieg1Governor)primary.getGovernor();
            assertFalse(secondary.getParentGen().isActive());assertEquals(0.,g.getSecondaryBaseScale(),0.);
            assertEquals(expected.get("PMECH"),g.getOutput(primary),1e-7,tag);
            assertEquals(0.,secondary.getGovernor().getOutput(secondary),0.);
            double[] state=new double[6],rates=new double[6];g.readPublishedState(state,0);g.evaluateStaggered(a.getSimuStepSec(),rates,0);
            for(int i=0;i<state.length;i++){assertEquals(expected.get("STATE"+i),state[i],1e-7,tag);assertEquals(0.,rates[i],1e-12,tag);}
            assertTrue(a.solveDEqnStep(true),tag+" active-owner first step");checked++;
        }
        assertEquals(4,checked);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothMethodsRetainCompleteInactiveShaftFlatAndFaultComparisons(boolean staggered,@TempDir Path staged)throws Exception {
        Wsieg1BypassLimitConformanceTest.compareCoupledTrajectories(new Wsieg1ReferenceFixture(staged,CONFIG),staggered,"wsieg1-inactive-",36);
    }

    @Test void nominalZeroPowerCapturesDoNotProveAnExactlyZeroDenominator(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);var pairs=new LinkedHashMap<String,Map<String,String>>();
        try(var zip=new ZipFile(fixture.archive().toFile())) {
            var matrix=new ObjectMapper().readTree(zip.getInputStream(zip.getEntry("zero-power-diagnostic-matrix.json")));
            for(var entry:matrix) {
                String tag=entry.path("tag").asText();
                var manifest=new ObjectMapper().readTree(zip.getInputStream(zip.getEntry(tag+"/manifest.json")));
                byte[] body=zip.getInputStream(zip.getEntry(tag+"/native.csv")).readAllBytes();
                assertEquals(manifest.path("files").path("native.csv").asText(),Wsieg1ReferenceFixture.sha(body));
                pairs.computeIfAbsent(tag.substring(0,tag.lastIndexOf('-')),k->new HashMap<>()).put(entry.path("repeat").asText(),Wsieg1ReferenceFixture.sha(body));
                var first=Wsieg1StateConformanceTest.rows(body).getFirst();
                assertTrue(first.get("PMECH")>0.&&first.get("PMECH")<1e-6,"The recorded initialized power includes a nonzero residual");
                double expected=first.get("PMECH_LP")/(.5*first.get("STATE2"));
                assertEquals(expected,first.get("VAR1"),Math.max(1.,Math.abs(expected))*1e-6);
            }
        }
        assertEquals(4,pairs.size());for(var pair:pairs.values()){assertEquals(Set.of("a","b"),pair.keySet());assertEquals(pair.get("a"),pair.get("b"));}
    }
}
