package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.*;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent clock discriminators, not acceptance of either Java integration method. */
public class Chvdc2RunBoundaryReferenceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ARCHIVE = Path.of("testData/private/verification-artifacts/chvdc2-boundary-clock-20261102/captures.zip");
    private static final Map<String, JsonNode> PROFILES = new LinkedHashMap<>();

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @BeforeAll static void verifyColdArtifacts() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE), "Retain independent run-boundary captures");
        assertEquals("3637371edc1a70378883617fac6f2cab04a4e62eb0f456fb71578745732b84f1", sha(Files.readAllBytes(ARCHIVE)));
        PROFILES.clear();
        try (var z = new ZipFile(ARCHIVE.toFile())) {
            var matrix = JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for (var file : matrix.path("files").properties())
                assertEquals(file.getValue().asText(), sha(z.getInputStream(z.getEntry(file.getKey())).readAllBytes()));
            for (var profile : matrix.path("profiles")) {
                String tag = profile.path("tag").asText();
                var m = JSON.readTree(z.getInputStream(z.getEntry(tag + "/manifest.json")));
                assertEquals("complete", m.path("status").asText());
                assertTrue(m.path("initialization_and_network_valid").asBoolean());
                assertTrue(m.path("finite_measurements").asBoolean());
                assertEquals(m.path("entered_constants"), m.path("initialized_constants"));
                if (!profile.path("repeat").asText().equals("a")) continue;
                String repeat = tag.substring(0, tag.length() - 1) + "b";
                assertArrayEquals(bytes(z, tag, "native.csv"), bytes(z, repeat, "native.csv"));
                if (m.path("settings").hasNonNull("boundary_probe"))
                    assertArrayEquals(bytes(z, tag, "boundary-snapshots.json"), bytes(z, repeat, "boundary-snapshots.json"));
                PROFILES.put(tag, m);
            }
        }
        assertEquals(6, PROFILES.size());
    }

    private static byte[] bytes(ZipFile z, String tag, String file) throws Exception {
        return z.getInputStream(z.getEntry(tag + "/" + file)).readAllBytes();
    }

    private static String profile(double step, String mode) {
        return PROFILES.entrySet().stream().filter(e -> {
            var settings = e.getValue().path("settings");
            return settings.path("step").asDouble() == step
                    && (mode == null ? !settings.hasNonNull("boundary_probe")
                                     : settings.path("boundary_probe").asText().equals(mode));
        }).map(Map.Entry::getKey).findFirst().orElseThrow();
    }

    private static JsonNode snapshot(JsonNode snapshots, String label) {
        for (var s : snapshots) if (s.path("label").asText().equals(label)) return s;
        throw new AssertionError("Missing public snapshot " + label);
    }

    private static double serialized(double value) {
        // The capture script explicitly writes twelve significant digits to its CSV.
        return Double.parseDouble(String.format(Locale.ROOT, "%.12g", value));
    }

    @ParameterizedTest @ValueSource(doubles = {.0005, .00025})
    void observationsArePureButPauseResumeChangesControllerHistory(double step) throws Exception {
        try (var z = new ZipFile(ARCHIVE.toFile())) {
            String plain = profile(step, null), observed = profile(step, "observations"), split = profile(step, "split");
            assertArrayEquals(bytes(z, plain, "native.csv"), bytes(z, observed, "native.csv"),
                    "Public snapshot reads must not perturb any channel or event side");
            var baseRows = Tiocr1ResetReferenceTest.csv(bytes(z, observed, "native.csv"));
            var splitRows = Tiocr1ResetReferenceTest.csv(bytes(z, split, "native.csv"));
            assertEquals(baseRows.size() + 2, splitRows.size(), "Retain both extra run-boundary observations");

            for (String tag : List.of(observed, split)) {
                var snapshots = JSON.readTree(bytes(z, tag, "boundary-snapshots.json"));
                var rows = tag.equals(observed) ? baseRows : splitRows;
                for (var s : snapshots) {
                    assertFalse(s.path("derivatives_available").asBoolean());
                    double time = serialized(s.path("time_s").asDouble());
                    var matching = rows.stream().filter(r -> r.get("time_s") == time).toList();
                    assertFalse(matching.isEmpty());
                    for (var row : matching) for (int k = 0; k < 18; k++)
                        assertEquals(serialized(s.path("states").get(k).asDouble()), row.get("STATE" + k),
                                "Public states and recorded states must agree at the same physical boundary");
                }
                for (var labels : List.of(List.of("before event", "event API returned"),
                                          List.of("before clearing", "clearing API returned"))) {
                    var before = snapshot(snapshots, labels.get(0));
                    var after = snapshot(snapshots, labels.get(1));
                    for (String key : List.of("time_s", "states", "variables", "ac_voltage"))
                        assertEquals(before.path(key), after.path(key), "Event API alone does not run the dynamic equations");
                }
            }

            var splitSnapshots = JSON.readTree(bytes(z, split, "boundary-snapshots.json"));
            double pauseTime = serialized(snapshot(splitSnapshots, "first event step").path("time_s").asDouble());
            var byTime = new LinkedHashMap<Double, List<Map<String, Double>>>();
            for (var row : splitRows) byTime.computeIfAbsent(row.get("time_s"), t -> new ArrayList<>()).add(row);
            var occurrences = new HashMap<Double, Integer>();
            var maximum = new LinkedHashMap<String, Double>();
            var atTime = new LinkedHashMap<String, Double>();
            double firstStateDifference = Double.NaN;
            for (var row : baseRows) {
                double time = row.get("time_s");
                int occurrence = occurrences.merge(time, 1, Integer::sum) - 1;
                var candidate = byTime.get(time).get(occurrence);
                for (String channel : row.keySet()) {
                    double error = Math.abs(row.get(channel) - candidate.get(channel));
                    if (error > maximum.getOrDefault(channel, 0.0)) {
                        maximum.put(channel, error); atTime.put(channel, time);
                    }
                    if (channel.startsWith("STATE")) {
                        if (time <= pauseTime) assertEquals(0, error, "Pausing cannot change already recorded states");
                        else if (error != 0 && Double.isNaN(firstStateDifference)) firstStateDifference = time;
                    }
                }
            }
            assertTrue(firstStateDifference > pauseTime, "The resumed trajectory must independently expose hidden history");
            assertTrue(maximum.getOrDefault("STATE10", 0.0) > 1e-6, "Distinguish history from CSV rounding");
            var result = new LinkedHashMap<String, Object>();
            result.put("network_interval_s", step);
            result.put("first_state_difference_s", firstStateDifference);
            result.put("maximum_absolute_differences", maximum);
            result.put("maximum_difference_time_s", atTime);
            result.put("baseline_samples", baseRows.size()); result.put("split_samples", splitRows.size());
            result.put("extra_pause_observations_retained_in_archive", 2);
            result.put("trajectory_acceptance", false);
            Path output = Files.createTempDirectory(Path.of("target"), "chvdc2-run-boundary-")
                    .resolve("comparison-" + step + ".json");
            try (var writer = BenchmarkOutputGuard.newWriter(output, 30000)) {
                JSON.writerWithDefaultPrettyPrinter().writeValue(writer, result);
            }
        }
    }
}
