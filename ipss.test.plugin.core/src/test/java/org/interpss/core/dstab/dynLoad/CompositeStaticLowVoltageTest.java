package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.google.gson.*;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent static-port histories and complete coupled boundaries under both methods. */
public class CompositeStaticLowVoltageTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "CompositeStaticLowVoltageTest");
    private record Reference(double h, double faultX, double secondFaultX, String dyr, List<Map<String, Double>> rows) { }
    private static final Map<String, Reference> REFERENCES = new TreeMap<>();
    private static String sha(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    private static List<Map<String, Double>> rows(byte[] raw) {
        String[] lines = new String(raw, StandardCharsets.UTF_8).strip().split("\\R"), keys = lines[0].split(",");
        var result = new ArrayList<Map<String, Double>>();
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(","); assertEquals(keys.length, values.length); var row = new LinkedHashMap<String, Double>();
            for (int j = 0; j < keys.length; j++) { double value = Double.parseDouble(values[j]); assertTrue(Double.isFinite(value)); row.put(keys[j], value); }
            result.add(row);
        }
        return result;
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Path archive = Path.of(CONFIG.string("reference.archive", "testData/private/verification-artifacts/cmldblu1-low-voltage-static-20261101/captures-qualified.zip"));
        Assumptions.assumeTrue(Files.isRegularFile(archive), "Retain independent static-port controls");
        assertEquals("0443967f697781b70520a28dfd9f26fd3614aca75ebdb32e661c7d27d4cf85ed", sha(Files.readAllBytes(archive)));
        try (var z = new ZipFile(archive.toFile())) {
            var m = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : m.getAsJsonObject("files").entrySet()) assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : m.getAsJsonObject("profiles").entrySet()) {
                var p = e.getValue().getAsJsonObject();
                var source = rows(z.getInputStream(z.getEntry(p.get("entry").getAsString())).readAllBytes());
                var repeat = rows(z.getInputStream(z.getEntry(p.get("repeat_entry").getAsString())).readAllBytes());
                assertEquals(source.size(), repeat.size()); assertEquals(p.get("rows").getAsInt(), source.size());
                assertEquals(p.get("columns").getAsInt(), source.getFirst().size());
                var undefined = new HashSet<String>(); p.getAsJsonArray("undefined_inactive_field_differences").forEach(v -> undefined.add(v.getAsString()));
                assertTrue(Set.of("VAR69", "VAR70", "VAR71", "VAR72").containsAll(undefined));
                // Motor A is absent. Keep its raw allocated storage, but do not call it an active measurement.
                assertEquals(0, p.getAsJsonArray("constants").get(18).getAsDouble());
                for (int i = 0; i < source.size(); i++) for (var field : source.get(i).entrySet())
                    if (!undefined.contains(field.getKey())) assertEquals(field.getValue(), repeat.get(i).get(field.getKey()));
                assertEquals(2, source.stream().filter(row -> row.get("time_s") < 0).count());
                int duplicates = 0; double previous = Double.NEGATIVE_INFINITY;
                for (var row : source) { double t = row.get("time_s"); assertTrue(t >= previous); if (t == previous) duplicates++; previous = t; }
                double second = p.get("second_fault_x_pu").getAsDouble(); assertEquals(second > 0 ? 4 : 2, duplicates);
                REFERENCES.put(e.getKey(), new Reference(p.get("step_sec").getAsDouble(), p.get("fault_x_pu").getAsDouble(), second,
                        new String(z.getInputStream(z.getEntry(p.get("dyr").getAsString())).readAllBytes(), StandardCharsets.UTF_8), source));
            }
        }
        assertEquals(32, REFERENCES.size());
    }
    @AfterAll static void clear() { REFERENCES.clear(); }
    static Stream<Arguments> methods() { return REFERENCES.keySet().stream().filter(name -> !name.startsWith("full-"))
            .flatMap(name -> Stream.of(false, true).map(shared -> Arguments.of(name, shared))); }
    private record Error(double maximum, double rmse, Double nrmse) { }
    @ParameterizedTest @MethodSource("methods")
    void completeStaticPortFollowsIndependentFaultAndRecovery(String name, boolean shared) throws Exception {
        var ref = REFERENCES.get(name); Path out = Files.createTempDirectory(Path.of("target"), "composite-static-low-coupled-");
        Path input = out.resolve("dynamic.dyr"); Files.writeString(input, ref.dyr());
        var actual = Cmldblu1SharedRecording.simulateStaticControl(input, shared, ref.h(), ref.faultX(), ref.secondFaultX());
        actual.write(out.resolve("actual.csv.gz"));
        var source = ref.rows().stream().filter(row -> row.get("time_s") >= 0).toList(); assertEquals(source.size(), actual.frames.size());
        assertSame(actual.model.getDStabBus(), actual.model.getLoadBus()); assertEquals(3, actual.algorithm.getNetwork().getNoBus());
        assertEquals(source.getFirst().get("V2"), actual.initial.values().get("V2"), 2.2e-7);
        double[] clock = new double[(int) Math.round(.5 / ref.h()) + 1]; float time = 0;
        for (int i = 1; i < clock.length; i++) { time += (float) ref.h(); clock[i] = time; }
        for (int i = 0; i < source.size(); i++) assertEquals(clock[(int) Math.round(actual.frames.get(i).time() / ref.h())], source.get(i).get("time_s"), 6e-13);
        var errors = new LinkedHashMap<String, Error>();
        for (String[] mapping : new String[][]{{"V1","V1"},{"V2","V2"},{"V3","V3"},{"P_PU","P_PU"},{"Q_PU","Q_PU"},{"SPEED","SPEED"},{"ANGLE_DEG","ANGLE_DEG"},
                {"VAR25","STATIC.P"},{"VAR26","STATIC.Q"},{"VAR57","STATIC.LowVoltageG"},{"VAR58","STATIC.LowVoltageB"}}) {
            double max = 0, sum = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < source.size(); i++) {
                double expected = source.get(i).get(mapping[0]), delta = actual.frames.get(i).values().get(mapping[1]) - expected;
                assertTrue(Double.isFinite(delta)); max = Math.max(max, Math.abs(delta)); sum += delta * delta; lo = Math.min(lo, expected); hi = Math.max(hi, expected);
            }
            double rmse = Math.sqrt(sum / source.size()); var error = new Error(max, rmse, hi > lo ? rmse / (hi - lo) : null); errors.put(mapping[1], error);
        }
        try (var writer = BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"), 100000)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("profile", name, "method", shared ? "staggered" : "modified-euler", "errors", errors,
                    "scope", "whole static merged-node current port; mixed components and represented distribution remain open"), writer);
        }
        for (var entry : errors.entrySet()) {
            String key = entry.getKey(); var error = entry.getValue(); double max = error.maximum(), rmse = error.rmse();
            if (Set.of("V1", "V2", "V3", "P_PU", "Q_PU", "STATIC.P", "STATIC.Q").contains(key))
                assertTrue(error.nrmse() == null ? max < 1e-8 : error.nrmse() < .001, key + ": " + error);
            else if (key.equals("ANGLE_DEG")) assertTrue(rmse < .0002, error.toString());
            else if (key.equals("SPEED")) {
                assertTrue(error.nrmse() != null && error.nrmse() < .001, error.toString());
                assertTrue(max < 2.4e-6 * ref.h() / .0005 + 3e-8, error.toString());
            } else assertTrue(max < (name.startsWith("continuous-entry") ? .0002 : .000005), key + ": " + error);
        }
    }
    @Test void sourceAdmittanceIsPreparedOnlyBelowThresholdAndRetainedAfterRecovery() {
        for (var ref : REFERENCES.values()) {
            double g = 0;
            for (var row : ref.rows()) {
                double next = row.get("VAR57"); if (g != 0) assertEquals(g, next);
                g = next;
                if (row.get("time_s") >= 0 && row.get("V2") < .7) {
                    assertTrue(g > 0); double squared = row.get("V2") * row.get("V2");
                    assertEquals(g * squared, row.get("VAR25"), 1e-7);
                    assertEquals(-row.get("VAR58") * squared, row.get("VAR26"), 1e-7);
                }
            }
        }
        assertEquals(0, REFERENCES.get("threshold-x0.11-h0.0005").rows().getLast().get("VAR57"));
        assertTrue(REFERENCES.get("threshold-x0.1-h0.0005").rows().getLast().get("VAR57") > 0);
    }
    @ParameterizedTest @CsvSource({"false,0.0005", "true,0.0005", "false,0.00025", "true,0.00025"})
    void signedLegacyPowerFactorKeepsNativeMagnitudeWithRepresentedDistribution(boolean shared, double h) throws Exception {
        String label = h == .0005 ? "0.0005" : "0.00025";
        var positive = REFERENCES.get("full-positive-pf-h" + label); var negative = REFERENCES.get("full-negative-pf-h" + label);
        assertEquals(positive.rows().size(), negative.rows().size());
        for (int i = 0; i < positive.rows().size(); i++) for (String key : positive.rows().get(i).keySet())
            if (!Set.of("VAR69", "VAR70", "VAR71", "VAR72").contains(key)) assertEquals(positive.rows().get(i).get(key), negative.rows().get(i).get(key));
        Cmldblu1SharedRecording first = null;
        for (var ref : List.of(positive, negative)) {
            Path out = Files.createTempDirectory(Path.of("target"), "composite-static-full-pf-coupled-"); Path input = out.resolve("dynamic.dyr"); Files.writeString(input, ref.dyr());
            var actual = Cmldblu1SharedRecording.simulateStaticControl(input, shared, h, ref.faultX(), 0); actual.write(out.resolve("actual.csv.gz"));
            assertEquals(5, actual.algorithm.getNetwork().getNoBus()); assertNotSame(actual.model.getDStabBus(), actual.model.getLoadBus());
            var source = ref.rows().stream().filter(row -> row.get("time_s") >= 0).toList(); assertEquals(source.size(), actual.frames.size());
            assertTrue(source.stream().allMatch(row -> row.get("VAR57") == 0 && row.get("VAR58") == 0), "This checks PF initialization, not the unqualified distributed low-voltage cache");
            var errors = new LinkedHashMap<String, Error>();
            for (String[] mapping : new String[][]{{"V1","V1"},{"V2","V2"},{"V3","V3"},{"P_PU","P_PU"},{"Q_PU","Q_PU"},
                    {"VAR25","STATIC.P"},{"VAR26","STATIC.Q"},{"VAR29","Load.V"},{"VAR30","Low.V"},{"ANGLE_DEG","ANGLE_DEG"}}) {
                double max = 0, sum = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < source.size(); i++) { double expected = source.get(i).get(mapping[0]), delta = actual.frames.get(i).values().get(mapping[1]) - expected;
                    assertTrue(Double.isFinite(delta)); max = Math.max(max, Math.abs(delta)); sum += delta * delta; lo = Math.min(lo, expected); hi = Math.max(hi, expected); }
                double rmse = Math.sqrt(sum / source.size()); errors.put(mapping[1], new Error(max, rmse, hi > lo ? rmse / (hi - lo) : null));
            }
            try (var writer = BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"), 100000)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("profile", "full-pf-" + actual.model.getLegacyConstants()[26], "h", h,
                        "method", shared ? "staggered" : "modified-euler", "errors", errors, "scope", "legacy PF magnitude with represented distribution; no active low-voltage cache"), writer);
            }
            for (var e : errors.entrySet()) assertTrue(e.getKey().equals("ANGLE_DEG") ? e.getValue().rmse() < .0002
                    : e.getValue().nrmse() != null && e.getValue().nrmse() < .001, e.toString());
            if (first == null) first = actual;
            else {
                assertEquals(first.initial, actual.initial);
                assertEquals(first.frames, actual.frames, "Legacy PF sign must not change any recorded numerical field");
                assertTrue(actual.model.getLegacyConstants()[26] < 0);
            }
        }
    }
}
