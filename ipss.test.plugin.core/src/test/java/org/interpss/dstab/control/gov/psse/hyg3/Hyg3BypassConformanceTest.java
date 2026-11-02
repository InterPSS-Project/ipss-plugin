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
}
