package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.test.TestConfig;

/** Independent boundary regressions and explicit, still incomplete component-state diagnostics. */
public class Cmldblu1StateConformanceTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "Cmldblu1StateConformanceTest");
    private static final Path ROOT = Path.of(CONFIG.string("reference.root",
            "testData/private/verification-artifacts/cmldblu1-shared-state-20261031/references"));
    private static JsonObject manifest;
    @BeforeAll static void setup() throws Exception {
        org.interpss.IpssCorePlugin.init(); org.interpss.IpssCorePlugin.configureSparseSolver("java-klu");
        Assumptions.assumeTrue(Files.isRegularFile(ROOT.resolve("manifest.json")), "Retain or regenerate the private independent composite captures");
        byte[] raw = Files.readAllBytes(ROOT.resolve("manifest.json"));
        assertEquals("40c09bc5b54f203c678f41613543a78ec6e49f52e025541f2aaa9e12221a675d", sha(raw));
        manifest = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    static Stream<Arguments> profiles() {
        return Stream.of("cmldblu1", "cmldblu1-mixed", "cmldblu1-electronic")
                .flatMap(f -> Stream.of(.0005, .00025).flatMap(h -> Stream.of(false, true).map(s -> Arguments.of(f, h, s))));
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static List<Map<String, Double>> reference(String fixture, double h) throws Exception {
        var profile = manifest.getAsJsonObject("profiles").getAsJsonObject(fixture + "-h" + (h == .0005 ? "0.0005" : "0.00025"));
        assertTrue(profile.get("common_channels_equal").getAsBoolean());
        List<Map<String, Double>> combined = null;
        for (var partValue : profile.getAsJsonArray("parts")) {
            var part = partValue.getAsJsonObject(); assertTrue(part.get("cold_pair_equal").getAsBoolean());
            byte[] archive = Files.readAllBytes(ROOT.resolve(part.get("file").getAsString()));
            assertEquals(part.get("archive_sha256").getAsString(), sha(archive));
            byte[] raw;
            try (var stream = new GZIPInputStream(new ByteArrayInputStream(archive))) { raw = stream.readAllBytes(); }
            assertEquals(part.get("raw_sha256").getAsString(), sha(raw));
            String[] lines = new String(raw, StandardCharsets.UTF_8).strip().split("\\R");
            String[] names = lines[0].split(","); var rows = new ArrayList<Map<String, Double>>();
            for (int row = 1; row < lines.length; row++) {
                String[] fields = lines[row].split(","); assertEquals(names.length, fields.length);
                var values = new LinkedHashMap<String, Double>();
                for (int col = 0; col < names.length; col++) {
                    double value = Double.parseDouble(fields[col]); assertTrue(Double.isFinite(value)); values.put(names[col], value);
                }
                rows.add(values);
            }
            assertEquals(part.get("rows").getAsInt(), rows.size());
            if (combined == null) combined = rows;
            else {
                assertEquals(combined.size(), rows.size());
                for (int row = 0; row < rows.size(); row++) for (var entry : rows.get(row).entrySet()) {
                    var old = combined.get(row).putIfAbsent(entry.getKey(), entry.getValue());
                    if (old != null) assertEquals(old, entry.getValue());
                }
            }
        }
        assertNotNull(combined); assertEquals(27 + 146 + 13, combined.getFirst().size());
        assertTrue(combined.getFirst().get("time_s") < 0, "Startup remains in the retained reference");
        int duplicates = 0; double last = Double.NEGATIVE_INFINITY;
        for (var row : combined) { double t = row.get("time_s"); assertTrue(t >= last); if (t == last) duplicates++; last = t; }
        assertEquals(2, duplicates, "Both fault-side duplicate pairs must remain");
        return combined.stream().filter(r -> r.get("time_s") >= 0).toList();
    }
    record Error(double maximumAbsolute, double rmse, Double rangeNormalizedRmse, double timeOfMaximum) { }
    private static Error error(List<Map<String, Double>> reference, List<Cmldblu1SharedRecording.Frame> actual,
            String source, String observed, double observationOffset) {
        double maximum = -1, sum = 0, time = 0, low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < reference.size(); i++) {
            double expected = reference.get(i).get(source), value = actual.get(i).values().get(observed) + observationOffset;
            double difference = value - expected; sum += difference * difference;
            low = Math.min(low, expected); high = Math.max(high, expected);
            if (Math.abs(difference) > maximum) { maximum = Math.abs(difference); time = reference.get(i).get("time_s"); }
        }
        double rmse = Math.sqrt(sum / reference.size());
        return new Error(maximum, rmse, high > low ? rmse / (high - low) : null, time);
    }
    @ParameterizedTest @MethodSource("profiles")
    void completeBoundaryComparisonRetainsUnqualifiedStateErrors(String fixture, double h, boolean staggered) throws Exception {
        var reference = reference(fixture, h);
        var recording = Cmldblu1SharedRecording.simulate(fixture, staggered, false, h);
        assertEquals(reference.size(), recording.frames.size());
        double[] sourceClock = new double[(int) Math.round(.5 / h) + 1];
        float clock = 0;
        for (int i = 1; i < sourceClock.length; i++) { clock += (float) h; sourceClock[i] = clock; }
        for (int i = 0; i < reference.size(); i++) {
            int grid = (int) Math.round(recording.frames.get(i).time() / h);
            assertEquals(sourceClock[grid], reference.get(i).get("time_s"), 6e-13, "Declared accumulated binary32 source clock");
        }
        String[] names = {"V1", "V2", "V3", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED"};
        double[] bounds = fixture.endsWith("electronic")
                ? staggered ? new double[] {3e-6, 1.3e-6, 2.3e-7, 4.6e-6, 2e-5, 7e-5, 3.4e-8}
                        : new double[] {.00033, .00013, .000022, .0001, .00295, .0001, 2.95e-6}
                : fixture.endsWith("mixed") ? new double[] {.00013, .000165, .000027, .00014, .00086, .00032, 1.25e-6}
                : new double[] {.00012, .00027, .000027, .004, .0011, .00043, 1.4e-6};
        if (fixture.endsWith("electronic") && !staggered) {
            // The ordinary method's one-stage fault-edge error halves with h.
            // Keep this distinction explicit rather than weakening both methods.
            for (int i = 0; i < bounds.length; i++) bounds[i] *= h / .0005;
        } else if (!fixture.endsWith("electronic")) {
            bounds[6] = (staggered ? fixture.endsWith("mixed") ? 3.3e-7 : 2.1e-7 : bounds[6]) * h / .0005;
        }
        var boundaries = new LinkedHashMap<String, Error>();
        var failures = new ArrayList<String>();
        for (int i = 0; i < names.length; i++) {
            var value = error(reference, recording.frames, names[i], names[i], 0); boundaries.put(names[i], value);
            if (value.maximumAbsolute > bounds[i]) failures.add(names[i] + " regression: " + value);
            if (names[i].startsWith("V") && (value.rangeNormalizedRmse == null || value.rangeNormalizedRmse >= .001))
                failures.add(names[i] + " voltage NRMSE must be below 0.001: " + value);
            if (names[i].equals("ANGLE_DEG") && value.rmse >= .01) failures.add("Rotor RMSE must be below 0.01 degrees: " + value);
        }
        var stateErrors = new LinkedHashMap<String, Error>();
        for (int motor = 0; motor < 3; motor++) {
            String prefix = "Motor" + (char) ('A' + motor) + ".";
            String[] states = {"EPrimeD", "EPrimeQ", "EDoublePrimeD", "EDoublePrimeQ", "SpeedDeviation"};
            for (int state = 0; state < states.length; state++) if (recording.initial.values().containsKey(prefix + states[state]))
                stateErrors.put("STATE" + (motor * 6 + state), error(reference, recording.frames,
                        "STATE" + (motor * 6 + state), prefix + states[state], 0));
        }
        String[] compressorStates = {"Vmeas", "Fmeas", "TemperatureA", "TemperatureB", "UvTimer1", "UvTimer2", "StallTimer"};
        for (int i = 0; i < compressorStates.length; i++) if (recording.initial.values().containsKey("MotorD." + compressorStates[i]))
            stateErrors.put("STATE" + (18 + i), error(reference, recording.frames, "STATE" + (18 + i), "MotorD." + compressorStates[i], i == 1 ? -1 : 0));
        if (recording.initial.values().containsKey("MotorD.RestartTimer")) stateErrors.put("STATE26",
                error(reference, recording.frames, "STATE26", "MotorD.RestartTimer", 0));
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "boundary-regression-only-component-qualification-incomplete");
        result.put("fixture", fixture); result.put("method", staggered ? "staggered" : "modified-euler"); result.put("step_sec", h);
        result.put("boundary_errors", boundaries); result.put("mapped_state_errors_without_acceptance", stateErrors);
        result.put("unimplemented_allocated_coordinates", List.of("STATE5", "STATE11", "STATE17", "STATE25"));
        result.put("internal_load_voltage_error", error(reference, recording.frames, "VAR29", "Load.V", 0));
        Path directory = Files.createTempDirectory(Path.of("target"), "cmldblu1-independent-");
        try (var writer = BenchmarkOutputGuard.newWriter(directory.resolve("comparison.json"), 100000)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(result, writer);
        }
        recording.write(directory.resolve("actual.csv.gz"));
        System.out.println("Composite independent comparison " + directory.getFileName() + " " + fixture
                + " " + (staggered ? "staggered" : "euler") + " h=" + h + " " + boundaries);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
