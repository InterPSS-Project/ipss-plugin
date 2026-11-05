package org.interpss.dstab.control.gov.wecc.wsieg1;

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

/** Independent shaft-loading, rating and network-base controls retain both machine boundaries. */
public class Wsieg1BaseConformanceTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1BaseConformanceTest");
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}

    @Test void coldPairsPreserveCoefficientsAndDiscriminatePowerNormalization(@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);var pairs=new LinkedHashMap<String,Map<String,String>>();
        for(var entry:fixture.matrix()) {
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);var m=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
            byte[] data=Files.readAllBytes(root.resolve("native.csv"));pairs.computeIfAbsent(tag.substring(0,tag.lastIndexOf('-')),k->new HashMap<>()).put(entry.path("repeat").asText(),Wsieg1ReferenceFixture.sha(data));
            var rows=Wsieg1StateConformanceTest.rows(data);var initial=rows.getFirst();var c=m.path("initialized_constants");
            double hp=0.,lp=0.;for(int i=0;i<4;i++){hp+=c.get(9+3*i).asDouble();lp+=c.get(10+3*i).asDouble();}
            assertEquals(initial.get("PMECH")/hp,initial.get("STATE2"),1e-7);
            assertEquals(initial.get("PMECH_LP")/(initial.get("STATE2")*lp),initial.get("VAR1"),5e-7);
            if(entry.path("profile").asText().equals("independent-secondary"))assertEquals(3.,initial.get("VAR1"),5e-7,"Ratings alone give two, which is wrong for this loading");
            if(entry.path("profile").asText().equals("zero-secondary"))assertEquals(0.,initial.get("VAR1"),5e-7);
            for(var row:rows) {
                double primary=0.,secondary=0.;
                for(int i=0;i<4;i++){primary+=c.get(9+3*i).asDouble()*row.get("STATE"+(i+2));secondary+=c.get(10+3*i).asDouble()*row.get("STATE"+(i+2));}
                assertEquals(primary,row.get("PMECH"),2e-7,tag);
                assertEquals(initial.get("VAR1")*secondary,row.get("PMECH_LP"),2e-7,tag);
            }
        }
        assertEquals(24,pairs.size());for(var pair:pairs.values()){assertEquals(Set.of("a","b"),pair.keySet());assertEquals(pair.get("a"),pair.get("b"));}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothInitializersMatchEachSolvedShaftPowerAndState(boolean staggered,@TempDir Path staged)throws Exception {
        var fixture=new Wsieg1ReferenceFixture(staged,CONFIG);int profiles=0;
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a")||!entry.path("event").asText().equals("fault"))continue;
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);
            var expected=Wsieg1StateConformanceTest.rows(Files.readAllBytes(root.resolve("native.csv"))).getFirst();
            var context=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
            var a=context.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuStepSec(entry.path("step_s").asDouble());a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            a.setSolver(staggered?new org.interpss.dstab.algo.StaggeredEulerSolver(a):new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(a));assertTrue(a.initialization(),tag);
            var primary=context.getDStabilityNet().getMachine("Bus1-mach1");var secondary=context.getDStabilityNet().getMachine("Bus2-mach1");var g=(Wsieg1Governor)primary.getGovernor();
            assertEquals(expected.get("PMECH"),g.getOutput(primary),1e-7,tag);assertEquals(expected.get("PMECH_LP"),secondary.getGovernor().getOutput(secondary),1e-7,tag);
            assertEquals(expected.get("VAR1"),g.getSecondaryBaseScale(),5e-7,tag);
            double[] states=new double[6];g.readPublishedState(states,0);for(int i=0;i<6;i++)assertEquals(expected.get("STATE"+i),states[i],1e-7,tag);
            for(double rate:g.getStateDerivativeSnapshot().rates().values())assertEquals(0.,rate,1e-12,tag);
            profiles++;
        }
        assertEquals(12,profiles);
    }

    @Test void systemBaseConversionPreservesTheIndependentPhysicalFault(@TempDir Path staged)throws Exception {
        var ordinary=new Wsieg1ReferenceFixture(staged.resolve("ordinary"));
        var doubled=new Wsieg1ReferenceFixture(staged.resolve("doubled"),CONFIG);int checked=0;
        for(var entry:doubled.matrix()) {
            String tag=entry.path("tag").asText();
            if(!tag.startsWith("system-double-")||!entry.path("event").asText().equals("fault")
                    ||!entry.path("repeat").asText().equals("a"))continue;
            var before=Wsieg1StateConformanceTest.rows(Files.readAllBytes(ordinary.stage(tag.substring("system-double-".length())).resolve("native.csv")));
            var after=Wsieg1StateConformanceTest.rows(Files.readAllBytes(doubled.stage(tag).resolve("native.csv")));
            assertEquals(before.size(),after.size());
            for(int i=0;i<before.size();i++)for(String key:before.get(i).keySet()) {
                // Electrical P/Q are on system base; mechanical power and all
                // controller states remain on their respective machine bases.
                double scale=key.startsWith("P_PU")||key.startsWith("Q_PU")?.5:1.;
                assertEquals(scale*before.get(i).get(key),after.get(i).get(key),1e-10,tag+" "+key);
            }
            checked++;
        }
        assertEquals(2,checked);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothMethodsPreserveAllDualShaftFlatAndFaultBoundaries(boolean staggered,@TempDir Path staged)throws Exception {
        Wsieg1BypassLimitConformanceTest.compareCoupledTrajectories(new Wsieg1ReferenceFixture(staged,CONFIG),staggered,"wsieg1-base-",216);
    }
}
