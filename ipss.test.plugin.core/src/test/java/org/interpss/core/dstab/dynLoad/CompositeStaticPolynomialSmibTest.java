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

/** Independent legacy polynomial normalization, linear stamp and coupled static-load controls. */
public class CompositeStaticPolynomialSmibTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "CompositeStaticPolynomialSmibTest");
    private record Reference(double h, String dyr, List<Map<String, Double>> rows, double[] constants) { }
    private static final Map<String, Reference> REFERENCES = new TreeMap<>();
    private static String sha(byte[] raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
    private static List<Map<String, Double>> rows(String text) {
        String[] lines = text.strip().split("\\R"), keys = lines[0].split(","); var result = new ArrayList<Map<String, Double>>();
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(","); assertEquals(keys.length, values.length); var row = new LinkedHashMap<String, Double>();
            for (int j = 0; j < keys.length; j++) { double value = Double.parseDouble(values[j]); assertTrue(Double.isFinite(value)); row.put(keys[j], value); }
            result.add(row);
        }
        return result;
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Path archive = Path.of(CONFIG.string("reference.archive", "testData/private/verification-artifacts/cmldblu1-static-normalization-20261101/captures.zip"));
        Assumptions.assumeTrue(Files.isRegularFile(archive), "Retain independent composite static-load controls");
        assertEquals("4b7a71503be4eeda85abc44ac89b3827649de93bcd75f06a9cbc5ec77eb824a6", sha(Files.readAllBytes(archive)));
        try (var z = new ZipFile(archive.toFile())) {
            var manifest = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : manifest.getAsJsonObject("files").entrySet()) assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : manifest.getAsJsonObject("profiles").entrySet()) {
                var p = e.getValue().getAsJsonObject(); byte[] raw = z.getInputStream(z.getEntry(p.get("entry").getAsString())).readAllBytes();
                assertArrayEquals(raw, z.getInputStream(z.getEntry(p.get("repeat_entry").getAsString())).readAllBytes());
                var source = rows(new String(raw, StandardCharsets.UTF_8)); assertEquals(p.get("rows").getAsInt(), source.size()); assertEquals(p.get("channels").getAsInt(), source.getFirst().size());
                assertEquals(2, source.stream().filter(r -> r.get("time_s") < 0).count());
                double previous = Double.NEGATIVE_INFINITY; int duplicates = 0;
                for (var r : source) { double t = r.get("time_s"); assertTrue(t >= previous); if (t == previous) duplicates++; previous = t; }
                assertEquals(2, duplicates);
                double[] constants = new double[132]; for (int i = 0; i < constants.length; i++) constants[i] = p.getAsJsonArray("constants").get(i).getAsDouble();
                for (int i : new int[]{18, 19, 20, 21, 22, 31, 36}) assertEquals(0, constants[i], "Static-only, frequency-disabled controls isolate the polynomial");
                String dyr = new String(z.getInputStream(z.getEntry(p.get("dyr").getAsString())).readAllBytes(), StandardCharsets.UTF_8);
                REFERENCES.put(e.getKey(), new Reference(p.get("step_sec").getAsDouble(), dyr, source, constants));
            }
        }
        assertEquals(10, REFERENCES.size());
    }
    @AfterAll static void release() { REFERENCES.clear(); }
    static Stream<String> profiles() { return REFERENCES.keySet().stream(); }
    static Stream<Arguments> methods() { return REFERENCES.keySet().stream().flatMap(n -> Stream.of(false, true).map(s -> Arguments.of(n, s))); }
    private static double shape(double v, double[] c, int j) { return c[j + 1] * Math.pow(v, c[j]) + c[j + 3] * Math.pow(v, c[j + 2]) + (1 - c[j + 1] - c[j + 3]); }
    @ParameterizedTest @MethodSource("profiles")
    void independentLegacyControlsDiscriminateRatioFromAbsoluteVoltageNormalization(String name) throws Exception {
        var ref = REFERENCES.get(name); var initial = ref.rows().getFirst(); double v0 = initial.get("VAR29"); var report = new LinkedHashMap<String, Object>();
        for (int j : new int[]{27, 32}) {
            String key = j == 27 ? "VAR25" : "VAR26"; double p0 = initial.get(key), ratioError = 0, otherError = 0;
            for (var r : ref.rows()) {
                double v = r.get("VAR29"); ratioError = Math.max(ratioError, Math.abs(r.get(key) - p0 * shape(v / v0, ref.constants(), j)));
                otherError = Math.max(otherError, Math.abs(r.get(key) - p0 * shape(v, ref.constants(), j) / shape(v0, ref.constants(), j)));
            }
            assertTrue(ratioError <= (j == 27 ? 6e-6 : 3e-6), key + ": " + ratioError);
            if (name.startsWith("mixed")) assertTrue(otherError > 1e-4, "Keep the independently distinguished legacy convention");
            report.put(key, Map.of("ratio_convention_peak_error", ratioError, "absolute_normalization_peak_error", otherError));
        }
        Path out = Files.createTempDirectory(Path.of("target"), "composite-static-equation-");
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"), 100000)) { new GsonBuilder().setPrettyPrinting().create().toJson(report, w); }
    }
    private record Error(double maximumAbsolute, double rmse, Double rangeNormalizedRmse, double timeOfMaximum) { }
    private static Error error(List<Map<String, Double>> source, List<Cmldblu1SharedRecording.Frame> actual, String nativeKey, String actualKey) {
        double maximum = 0, time = 0, sum = 0, low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < source.size(); i++) {
            double expected = source.get(i).get(nativeKey), value = actual.get(i).values().get(actualKey), d = value - expected; assertTrue(Double.isFinite(d));
            if (Math.abs(d) > maximum) { maximum = Math.abs(d); time = source.get(i).get("time_s"); } sum += d * d; low = Math.min(low, expected); high = Math.max(high, expected);
        }
        double rmse = Math.sqrt(sum / source.size()); return new Error(maximum, rmse, high > low ? rmse / (high - low) : null, time);
    }
    @ParameterizedTest @MethodSource("methods")
    void completeStaticOnlySmibTrajectoriesRemainAccurateThroughBothMethods(String name, boolean shared) throws Exception {
        var ref = REFERENCES.get(name); Path out = Files.createTempDirectory(Path.of("target"), "composite-static-coupled-");
        Path input = out.resolve("dynamic.dyr"); Files.writeString(input, ref.dyr()); var recording = Cmldblu1SharedRecording.simulateStaticControl(input, shared, ref.h());
        var source = ref.rows().stream().filter(r -> r.get("time_s") >= 0).toList(); assertEquals(source.size(), recording.frames.size());
        double[] clock = new double[(int) Math.round(.5 / ref.h()) + 1]; float t = 0; for (int i = 1; i < clock.length; i++) { t += (float) ref.h(); clock[i] = t; }
        for (int i = 0; i < source.size(); i++) assertEquals(clock[(int) Math.round(recording.frames.get(i).time() / ref.h())], source.get(i).get("time_s"), 6e-13);
        var errors = new LinkedHashMap<String, Error>(); var limits = new LinkedHashMap<String, Double>();
        for (String key : List.of("V1", "V2", "V3", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) {
            var e = error(source, recording.frames, key, key); errors.put(key, e);
            double peak = shared ? key.equals("V1") ? 3e-6 : key.equals("V2") ? 1.5e-6 : key.equals("V3") ? 3e-7 : key.equals("P_PU") ? 5e-6 : key.equals("Q_PU") ? 2e-5 : key.equals("ANGLE_DEG") ? 6e-5 : 2e-8
                    : key.equals("ANGLE_DEG") ? 6e-5 : ref.h() / .0005 * (key.equals("V1") ? 9e-5 : key.equals("V2") ? 3.5e-5 : key.equals("V3") ? 6e-6 : key.equals("P_PU") ? 1e-4 : key.equals("Q_PU") ? 6e-4 : 1.7e-6);
            assertTrue(e.maximumAbsolute() <= peak, key + ": " + e); limits.put(key + "_peak", peak);
            if (key.startsWith("V")) assertTrue(e.rangeNormalizedRmse() != null && e.rangeNormalizedRmse() < (shared ? 1.1e-5 : 4e-5), key + ": " + e);
            if (key.equals("ANGLE_DEG")) assertTrue(e.rmse() < 3.5e-5, e.toString());
        }
        for (String[] mapping : new String[][]{{"VAR29", "Load.V"}, {"VAR25", "STATIC.P"}, {"VAR26", "STATIC.Q"}}) {
            var e = error(source, recording.frames, mapping[0], mapping[1]); errors.put(mapping[1], e);
            double peak = shared ? mapping[1].equals("Load.V") ? 2e-6 : mapping[1].equals("STATIC.P") ? 6.5e-6 : 2.5e-6
                    : mapping[1].equals("Load.V") ? 4e-5 * ref.h() / .0005 + 5e-6 : mapping[1].equals("STATIC.P") ? 3.2e-5 : 1.2e-5;
            assertTrue(e.maximumAbsolute() <= peak, mapping[1] + ": " + e); limits.put(mapping[1] + "_peak", peak);
        }
        recording.write(out.resolve("actual.csv.gz"));
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"), 100000)) { new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("profile", name, "method", shared ? "staggered" : "modified-euler", "errors", errors, "limits", limits, "scope", "static-only moderate faults; complete composite qualification remains open"), w); }
    }
}
