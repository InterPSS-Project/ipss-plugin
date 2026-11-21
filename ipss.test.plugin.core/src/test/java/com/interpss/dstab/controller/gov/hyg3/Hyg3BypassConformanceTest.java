package com.interpss.dstab.controller.gov.hyg3;

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
import com.interpss.dstab.controller.gov.hyg3.PsseHyg3Governor;

/** Independent zero-time filters and servo observations with both integration methods. */
public class Hyg3BypassConformanceTest {
    private static final TestConfig CONFIG = TestConfig.load(
            "/test-config/ipss.test.plugin.core.json", "Hyg3BypassConformanceTest");

    @Test void coldRepeatsAndRecordedAlgebraicCoordinatesDiscriminateDormantStorage(@TempDir Path staged) throws Exception {
        var fixture = new Hyg3ReferenceFixture(staged, CONFIG);
        int compared = 0;
        for (var entry : fixture.matrix()) {
            if (!entry.path("repeat").asText().equals("a")) continue;
            String tag = entry.path("tag").asText();
            var root = fixture.stage(tag); var repeat = fixture.stage(tag.substring(0, tag.length()-1)+"b");
            assertArrayEquals(Files.readAllBytes(root.resolve("native.csv")), Files.readAllBytes(repeat.resolve("native.csv")));
            var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            var c = manifest.path("initialized_constants");
            assertEquals(manifest.path("entered_constants"), c, "No hidden time-constant preparation in these captures");
            String profile = entry.path("profile").asText();
            int coordinate = switch (profile) {
                case "input-filter-bypass" -> 0;
                case "servo-bypass" -> 3;
                case "power-filter-bypass" -> 5;
                default -> throw new AssertionError(profile);
            };
            int constant = coordinate == 0 ? 3 : coordinate == 3 ? 9 : 2;
            assertEquals(0., c.get(constant).asDouble(), 0.);
            var rows = Hyg3StateConformanceTest.rows(root.resolve("native.csv"));
            double initial = rows.getFirst().get("STATE"+coordinate), motion = 0., maximum = 0.;
            for (var row : rows) {
                double value = row.get("STATE"+coordinate), expected;
                if (coordinate == 5) expected = row.get("P_PU");
                else if (coordinate == 3) expected = Math.max(c.get(11).asDouble(), Math.min(c.get(10).asDouble(),
                        c.get(8).asDouble()*(row.get("VAR0")-row.get("VAR3"))));
                else if (entry.path("mode").asInt() == 0) expected = row.get("VAR1");
                else expected = (manifest.path("initial_reference_from_equilibrium").asDouble()-row.get("VAR1")
                            -c.get(1).asDouble()*row.get("STATE5")-c.get(0).asDouble()*(row.get("STATE1")-row.get("STATE2")))
                        /(1.+c.get(0).asDouble()*(c.get(4).asDouble()+c.get(6).asDouble()/c.get(7).asDouble()));
                maximum = Math.max(maximum, Math.abs(value-expected)); motion = Math.max(motion, Math.abs(value-initial));
            }
            // The PID algebraic feedback has a finite-stage residual; keep it
            // distinct from the direct bypass identities and complete-state gates.
            double tolerance = coordinate == 5 ? 4e-8 : coordinate == 3 ? 1e-10
                    : entry.path("mode").asInt() == 0 ? 1e-15 : 3e-6;
            assertTrue(maximum < tolerance, tag+" algebraic residual="+maximum);
            assertTrue(motion > 1e-4, "A frozen allocated coordinate must fail this discriminator");
            System.out.println("HYG3 bypass identity "+tag+" residual="+maximum+" dormant_difference="+motion);
            compared++;
        }
        assertEquals(12, compared);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void completeBypassTrajectoriesRetainStateAndBoundaryGates(boolean staggered, @TempDir Path staged) throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        var fixture = new Hyg3ReferenceFixture(staged, CONFIG);
        var output = Files.createTempDirectory(Path.of("target"), staggered ? "hyg3-bypass-staggered-" : "hyg3-bypass-euler-");
        Hyg3StateConformanceTest.compare(fixture, staggered, output, manifest -> {
            boolean fine = manifest.path("settings").path("step").asDouble() < .001;
            double[] states = fine
                    ? staggered ? new double[]{4e-7,3e-6,1.3e-6,1.1e-5,2e-6,3.3e-6,2.6e-6,1.6e-7,7e-7}
                                : new double[]{8e-6,3e-6,1.3e-6,1.1e-5,2e-6,1.4e-4,2.6e-6,3e-7,3.4e-6}
                    : staggered ? new double[]{9e-6,8e-6,1.1e-5,6.6e-5,6.8e-6,1.5e-4,2.4e-6,4.6e-6,4e-5}
                                : new double[]{5.5e-5,4.6e-5,2.2e-5,1.7e-4,6.5e-6,.0019,2.4e-6,2.1e-5,2.3e-4};
            // Same channel order as the shared comparison: P,Q,Pm,speed,A1,A2,
            // command, deadband, backlash history, gate output and turbine head.
            double[] boundaries = fine
                    ? staggered ? new double[]{6.5e-6,1.3e-5,7e-6,4e-8,5.2e-5,1.8e-5,7.5e-6,4e-8,1e-7,2e-6,8.5e-6}
                                : new double[]{.00015,.0021,7e-6,1.7e-6,.0028,.00075,2.4e-5,1.7e-6,1e-7,2e-6,8.5e-6}
                    : staggered ? new double[]{.00015,.0009,9.5e-6,2e-6,.0015,.00045,5.2e-5,2e-6,1e-7,6.8e-6,2.1e-5}
                                : new double[]{.0019,.0134,9.5e-6,1.5e-5,.027,.0078,.00016,1.5e-5,1e-7,6.5e-6,2.1e-5};
            return new Hyg3StateConformanceTest.ComparisonLimits(states, boundaries);
        });
    }

    @Test void recordedSpeedAndPowerLocalizeTheRemainingBypassStateErrors(@TempDir Path staged) throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var fixture=new Hyg3ReferenceFixture(staged,CONFIG);
        Path output=Files.createTempDirectory(Path.of("target"),"hyg3-input-replay-");
        System.out.println("HYG3 prescribed-input comparison "+output.getFileName());int profiles=0;
        var diagnostics=new java.util.LinkedHashMap<String,Object>();
        for(var entry:fixture.matrix()) {
            if(!entry.path("repeat").asText().equals("a")||!entry.path("profile").asText().equals("power-filter-bypass"))continue;
            String tag=entry.path("tag").asText();Path root=fixture.stage(tag);
            var manifest=new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            double h=manifest.path("settings").path("step").asDouble();boolean doubleDerivative=entry.path("mode").asInt()==0;
            var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("dynamic.dyr").toString());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(h);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
            var machine=context.getDStabilityNet().getMachine("Bus1-mach1");var governor=(PsseHyg3Governor)machine.getGovernor();
            assertEquals(0.,governor.getData().getTt());assertEquals(0.,governor.getData().getDb2());
            assertEquals(0.,machine.getRa());assertEquals(100.,governor.getGovernorBaseMva());
            double[] state=new double[9],rates=new double[9],history=new double[9],left=new double[9],published=new double[9],maximum=new double[9];
            governor.evaluateStaggered(h,history,0);
            var reference=Hyg3StateConformanceTest.rows(root.resolve("native.csv"));int written=0,events=0;
            try(var raw=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newOutputStream(output.resolve(tag+".csv.gz"),
                    org.interpss.core.dstab.reference.BenchmarkOutputGuard.estimateCsvBytes(reference.size(),22));
                    var writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.util.zip.GZIPOutputStream(raw),java.nio.charset.StandardCharsets.UTF_8))) {
                writer.write("time_s,sample_kind,speed_input,power_input");for(int k=0;k<9;k++)writer.write(",STATE"+k+",REFERENCE.STATE"+k);writer.write("\n");
                for(int j=0;j<reference.size();j++) {
                    var row=reference.get(j);double time=row.get("time_s");
                    machine.setSpeed(1.+row.get("SPEED"));machine.setPe(row.get("P_PU"));
                    var before=governor.getIntegrationStates();var committed=governor.getIntegrationHistories();
                    governor.evaluateStaggered(h,rates,0);governor.readPublishedState(published,0);
                    assertEquals(before,governor.getIntegrationStates());assertEquals(committed,governor.getIntegrationHistories());
                    assertEquals(row.get("P_PU"),published[5],1e-15,"Prescribed terminal power reaches the bypass coordinate directly");
                    boolean event=time>0.&&j>0&&row.get("time_s").equals(reference.get(j-1).get("time_s"));
                    writer.write(time+","+(time<0.?"STARTUP_HELD_INITIAL":event?"EVENT_POST":"GRID")+","+row.get("SPEED")+","+row.get("P_PU"));
                    for(int k=0;k<9;k++){maximum[k]=Math.max(maximum[k],Math.abs(published[k]-row.get("STATE"+k)));writer.write(","+published[k]+","+row.get("STATE"+k));}writer.write("\n");written++;
                    if(time<0.||j+1==reference.size())continue;
                    if(row.get("time_s").equals(reference.get(j+1).get("time_s"))){System.arraycopy(rates,0,left,0,9);continue;}
                    if(event)events++;governor.readStaggeredState(state,0);
                    for(int k=0;k<9;k++){double effective=event?.5*(rates[k]+left[k]):rates[k];state[k]+=h*(1.5*effective-.5*history[k]);history[k]=effective;}
                    governor.applyStaggeredState(state,0,h);governor.completeStaggeredStep(h);
                }
            }
            assertEquals(reference.size(),written);assertEquals(2,events);
            diagnostics.put(tag,java.util.Map.of("maximum_state_error",maximum,"samples",written,
                    "scope","Prescribed speed and power diagnostic; complete coupled gates remain separate"));
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(),diagnostics);
            double[] bounds=doubleDerivative?new double[]{1.5e-10,1.1e-6,2.5e-10,6e-6,1.3e-6,3.1e-8,1.1e-6,1e-9,5e-9}
                    :new double[]{5e-8,1.1e-6,8e-8,6e-6,1.3e-6,3.1e-8,1.1e-6,1e-15,1e-15};
            for(int k=0;k<9;k++)assertTrue(maximum[k]<bounds[k],tag+" prescribed-input STATE"+k+" "+maximum[k]);profiles++;
        }
        assertEquals(4,profiles);
    }
}
