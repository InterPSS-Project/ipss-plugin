package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import com.google.gson.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent nonflat-startup phase history and coupled frequency sensitivity. */
public class CompressorStartupFrequencyTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "CompressorStartupFrequencyTest");
    private static final Path ARCHIVE = Path.of(CONFIG.string("reference.archive",
            "testData/private/verification-artifacts/acmtblu1-startup-frequency-20261101/captures.zip"));
    private static final Path BYPASS = Path.of(CONFIG.string("reference.bypass.archive",
            "testData/private/verification-artifacts/acmtblu1-startup-frequency-20261101/bypass-captures.zip"));
    private static final Map<String, Acmtblu1SharedSmibTest.Reference> REFERENCES = new TreeMap<>();
    private static final Map<String, Double> FILTERS = new TreeMap<>();
    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    private static List<Map<String, Double>> rows(String text) {
        String[] lines = text.strip().split("\\R"), keys = lines[0].split(",");
        var result = new ArrayList<Map<String, Double>>();
        for (int i = 1; i < lines.length; i++) {
            String[] fields = lines[i].split(","); assertEquals(keys.length, fields.length);
            var row = new LinkedHashMap<String, Double>();
            for (int j = 0; j < keys.length; j++) {
                double value = Double.parseDouble(fields[j]); assertTrue(Double.isFinite(value)); row.put(keys[j], value);
            }
            result.add(row);
        }
        return result;
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        read(ARCHIVE, "29923a8f4d7ba0facb119cc298ff46f06c80c8a6d32dde85418b1dcb10e45c46");
        read(BYPASS, "781bd59455cd8b4a4e7d37d8278d24c5db3c84bd7a01222fe13ef7adfd719eb6");
        assertEquals(12, REFERENCES.size());
    }
    private static void read(Path archive, String expectedHash) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(archive), "Retain independent startup-frequency controls");
        assertEquals(expectedHash, sha(Files.readAllBytes(archive)));
        try (var z = new ZipFile(archive.toFile())) {
            var manifest = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : manifest.getAsJsonObject("files").entrySet())
                assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : manifest.getAsJsonObject("profiles").entrySet()) {
                var p = e.getValue().getAsJsonObject(); String entry = p.get("entry").getAsString();
                byte[] raw = z.getInputStream(z.getEntry(entry)).readAllBytes();
                assertArrayEquals(raw, z.getInputStream(z.getEntry(entry.replace("-a-part", "-b-part"))).readAllBytes());
                var source = rows(new String(raw, StandardCharsets.UTF_8));
                assertEquals(59, source.getFirst().size()); assertEquals(p.get("rows").getAsInt(), source.size());
                assertEquals(2, source.stream().filter(r -> r.get("time_s") < 0).count());
                int duplicates = 0; double previous = Double.NEGATIVE_INFINITY;
                for (var r : source) {
                    double time = r.get("time_s"); assertTrue(time >= previous);
                    if (time == previous) duplicates++; previous = time;
                    assertEquals(r.get("V2"), r.get("VAR0"));
                }
                assertEquals(4, duplicates);
                String dir = entry.substring(0, entry.lastIndexOf('/') + 1);
                var metadata = JsonParser.parseString(new String(z.getInputStream(z.getEntry(dir + "manifest.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                FILTERS.put(e.getKey(), metadata.getAsJsonArray("initialized_constants").get(3).getAsDouble());
                String dyr = new String(z.getInputStream(z.getEntry(dir + "dynamic.dyr")).readAllBytes(), StandardCharsets.UTF_8);
                REFERENCES.put(e.getKey(), new Acmtblu1SharedSmibTest.Reference(p.get("step_sec").getAsDouble(), .01, .82, dyr, source));
            }
        }
    }
    @AfterAll static void release() { REFERENCES.clear(); FILTERS.clear(); }
    static Stream<Arguments> methods() {
        return REFERENCES.keySet().stream().flatMap(n -> Stream.of(false, true).map(s -> Arguments.of(n, s)));
    }
    private static LD1PACImpl motor() throws Exception {
        return motor(REFERENCES.get("partial-h0.0005"));
    }
    private static LD1PACImpl motor(Acmtblu1SharedSmibTest.Reference ref) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "compressor-startup-input-");
        Files.writeString(directory.resolve("dynamic.dyr"), ref.dyr());
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab("testData/adpter/psse/v33/HspModels/load-network.raw", directory.resolve("dynamic.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        var algorithm = context.getDynSimuAlgorithm(); assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(ref.h()); algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.initialization());
        var motor = assertInstanceOf(LD1PACImpl.class, context.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().getFirst());
        assertEquals(LD1PACImpl.FrequencyInputMode.TERMINAL_PHASE_BINARY32, motor.getFrequencyInputMode());
        return motor;
    }
    @Test void preparingThePhysicalClockCannotAdvanceAnyAcceptedHistory() throws Exception {
        var m = motor(); var states = m.getIntegrationStates(); var histories = m.getIntegrationHistories();
        for (int i = 0; i < 5; i++) m.prepareIntegrationStep(-.001, .0005);
        double[] rates = new double[9]; m.evaluateStaggered(.0005, rates, 0); m.getNortonCurInj();
        assertTrue(m.afterStep(0)); assertEquals(states, m.getIntegrationStates()); assertEquals(histories, m.getIntegrationHistories());
        for (double[] invalid : new double[][]{{Double.NaN, .001}, {0, Double.POSITIVE_INFINITY}, {0, -.001}, {Double.MAX_VALUE, Double.MAX_VALUE}})
            assertThrows(IllegalArgumentException.class, () -> m.prepareIntegrationStep(invalid[0], invalid[1]));
        assertEquals(states, m.getIntegrationStates()); assertEquals(histories, m.getIntegrationHistories());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void startupHoldsOnlyPhaseMemoryWhileOtherCoordinatesIntegrate(boolean shared) throws Exception {
        var m = motor(); double h = .0005; var bus = m.getDStabBus(); double[] x = new double[9], rates = new double[9];
        var initial = m.getIntegrationHistories(); double phase = initial.get("FrequencyPhase");
        m.readStaggeredState(x, 0); x[0] = .8; x[2] = .05; m.applyStaggeredState(x, 0, 0);
        double beforeVoltage = x[0], beforeTemperature = x[2];
        for (double start : new double[]{-2 * h, -h}) {
            m.prepareIntegrationStep(start, h); bus.setVoltage(new Complex(.9, .1));
            if (shared) {
                m.readStaggeredState(x, 0); m.evaluateStaggered(h, rates, 0);
                for (int i = 0; i < x.length; i++) x[i] += h * rates[i];
                m.applyStaggeredState(x, 0, h); m.completeStaggeredStep(h);
            } else {
                assertTrue(m.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, 0));
                assertTrue(m.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, 1));
            }
            assertTrue(m.afterStep(h));
            assertEquals(0., m.getIntegrationHistories().get("FrequencySampleTime"), 0);
            assertEquals(phase, m.getIntegrationHistories().get("FrequencyPhase"), 0);
            assertEquals(1., m.getMeasuredFrequency(), 0);
        }
        assertNotEquals(beforeVoltage, m.getIntegrationStates().get("Vmeas"));
        assertNotEquals(beforeTemperature, m.getIntegrationStates().get("TemperatureA"));
        m.prepareIntegrationStep(0, h); assertTrue(m.afterStep(h));
        assertEquals((double) (float) h, m.getIntegrationHistories().get("FrequencySampleTime"), 0);
        assertNotEquals(phase, m.getIntegrationHistories().get("FrequencyPhase"));
        m.prepareIntegrationStep(-2 * h, h); assertTrue(m.initStates());
        // Reinitialization clears pending startup context for duration-only clients.
        assertTrue(m.afterStep(h));
        assertEquals((double) (float) h, m.getIntegrationHistories().get("FrequencySampleTime"), 0);
    }
    private static final class Error {
        double maximum, sum, time; int count;
        void add(double actual, double expected, double at) { double d = actual - expected; assertTrue(Double.isFinite(d)); if (Math.abs(d) > maximum) { maximum = Math.abs(d); time = at; } sum += d * d; count++; }
        Map<String, Object> report() { return Map.of("maximum_absolute", maximum, "rmse", Math.sqrt(sum / count), "source_time_of_maximum", time, "samples", count); }
    }
    private static Complex recordedPhase(Map<String, Double> r) { return new Complex(r.get("VAR21"), r.get("VAR22")); }
    private static Complex terminal(Map<String, Double> r) { double angle = Math.toRadians(r.get("A2")); return new Complex(r.get("V2") * Math.cos(angle), r.get("V2") * Math.sin(angle)); }
    @ParameterizedTest @MethodSource("methods")
    void recordedPhaseReplayPreservesStartupClockAndMethodEquations(String name, boolean shared) throws Exception {
        var ref = REFERENCES.get(name); var m = motor(ref); var bus = m.getDStabBus();
        double h = ref.h(), origin = -2 * h, previousRate = 0, exact = 0, envelope = 0, maximumExcess = 0;
        double sampleTime = 0, sampledPhase = recordedPhase(ref.rows().getFirst()).getArgument();
        int previousGrid = 0; double[] x = new double[9], rates = new double[9];
        var output = new ArrayList<Map<String, Double>>(); var stateError = new Error(); var outputError = new Error();
        for (var source : ref.rows()) {
            double time = source.get("time_s"); int grid = (int) Math.round((time - origin) / h);
            if (grid > previousGrid) {
                assertEquals(previousGrid + 1, grid); m.prepareIntegrationStep(origin + previousGrid * h, h);
                double u = m.getIntegrationHistories().get("FrequencyInput") - 1, a = m.getTf() > 0 ? h / m.getTf() : 0;
                if (m.getTf() > 0) {
                    double decay = Math.exp(-a), stability = 1 - a + .5 * a * a;
                    envelope = stability * envelope + Math.abs(stability - decay) * Math.abs(exact - u);
                    exact = decay * exact + (1 - decay) * u;
                }
                m.readStaggeredState(x, 0); double old = x[1];
                if (shared) {
                    m.evaluateStaggered(h, rates, 0); x[1] += h * (1.5 * rates[1] - .5 * previousRate); previousRate = rates[1];
                } else {
                    assertTrue(m.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, 0)); assertTrue(m.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, 1)); m.readStaggeredState(x, 0);
                    if (m.getTf() > 0) assertEquals(old + (a - .5 * a * a) * (1 + u - old), x[1], 2e-15, "Independent held-input Heun equation");
                }
                // Only the frequency coordinate is independently integrated in this replay.
                for (int i = 0; i < 9; i++) if (i != 1) x[i] = source.get("STATE" + i);
                bus.setVoltage(time > 0 ? recordedPhase(source) : terminal(source)); m.applyStaggeredState(x, 0, 0);
                if (shared) m.completeStaggeredStep(h); assertTrue(m.afterStep(h)); previousGrid = grid;
                if (time > 0) {
                    double nextTime = (double) ((float) sampleTime + (float) h), phase = recordedPhase(source).getArgument();
                    double raw = Math.IEEEremainder(phase - sampledPhase, 2 * Math.PI) / (2 * Math.PI * 60 * (nextTime - sampleTime));
                    assertEquals(raw, m.getIntegrationHistories().get("FrequencySampleDeviation"), 2e-6);
                    sampleTime = nextTime; sampledPhase = phase;
                }
                if (!shared && m.getTf() > 0) { double excess = Math.abs(m.getMeasuredFrequency() - 1 - exact) - envelope; maximumExcess = Math.max(maximumExcess, excess); assertTrue(excess <= 3e-9); }
            } else {
                bus.setVoltage(time > 0 ? recordedPhase(source) : terminal(source));
                if (!output.isEmpty()) m.acceptNetworkEventInput(time);
            }
            assertEquals(sampleTime, m.getIntegrationHistories().get("FrequencySampleTime"), 0);
            if (time <= 0) { assertEquals(0., m.getMeasuredFrequency() - 1, 0); assertEquals(0., m.getIntegrationHistories().get("FrequencySampleDeviation"), 0); }
            stateError.add(m.getMeasuredFrequency() - 1, source.get("STATE1"), time); outputError.add(m.getFrequencyOutput(), source.get("VAR1"), time);
            var row = new LinkedHashMap<String, Double>(); row.put("source_time_s", time); row.put("state", m.getMeasuredFrequency() - 1); row.put("source_state", source.get("STATE1")); row.put("output", m.getFrequencyOutput()); row.put("source_output", source.get("VAR1")); row.put("analytic_held_state", exact); row.put("analytic_truncation_bound", envelope); row.putAll(m.getIntegrationHistories()); output.add(row);
        }
        Path out = Files.createTempDirectory(Path.of("target"), "compressor-startup-replay-");
        var report = Map.of("profile", name, "method", shared ? "staggered" : "modified-euler", "status", "recorded-phase-replay-other-continuous-coordinates-fed", "filter_time_constant", m.getTf(), "held_input_truncation_bound_applies", m.getTf() > 0, "state_error", stateError.report(), "output_error", outputError.report(), "maximum_analytic_excess", maximumExcess);
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"), 100000)) { new GsonBuilder().setPrettyPrinting().create().toJson(report, w); }
        var keys = output.getFirst().keySet();
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"), BenchmarkOutputGuard.estimateCsvBytes(output.size(), keys.size()))) {
            w.write(String.join(",", keys) + "\n");
            for (var row : output) { boolean first = true; for (String key : keys) { if (!first) w.write(","); first = false; w.write(row.get(key).toString()); } w.write("\n"); }
        }
        if (shared || m.getTf() == 0) { assertTrue(stateError.maximum <= (m.getTf() == 0 ? 2e-6 : 2e-7), stateError.report().toString()); assertTrue(outputError.maximum <= 2e-7, outputError.report().toString()); }
    }
    private static Map<String, Object> coupled(String name, boolean shared) throws Exception {
        var ref = REFERENCES.get(name); Path out = Files.createTempDirectory(Path.of("target"), "compressor-startup-coupled-");
        var report = Acmtblu1SharedSmibTest.runReference(name, ref, shared, out, false, new Acmtblu1SharedSmibTest.RunSettings(2, shared, 1e-4, true));
        var boundary = (Map<?, ?>) report.get("boundary_errors"); var states = (Map<?, ?>) report.get("state_errors");
        var limits = new LinkedHashMap<String, Double>(); var stateLimits = new LinkedHashMap<String, Double>();
        var checks = new ArrayList<Executable>();
        for (String key : List.of("V1", "V2", "V3", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) {
            String norm = key.equals("ANGLE_DEG") || key.equals("SPEED") ? "rmse" : "range_normalized_rmse";
            double bound = shared ? (key.equals("ANGLE_DEG") ? 2e-4 : key.equals("SPEED") ? 2e-7 : key.equals("P_PU") ? 4e-5 : key.equals("Q_PU") ? 3e-6 : 2e-6)
                    : ref.h() / .0005 * (key.equals("ANGLE_DEG") ? .01 : key.equals("SPEED") ? 5e-6 : key.startsWith("V") ? 1e-4 : 1e-3);
            checks.add(() -> assertTrue(((Number) ((Map<?, ?>) boundary.get(key)).get(norm)).doubleValue() <= bound, key + ": " + boundary.get(key)));
            limits.put(key + "_" + norm, bound);
        }
        var actual = rows(Files.readString(out.resolve("actual.csv")));
        for (int i = 0; i < actual.size(); i++) if (ref.rows().get(i).get("time_s") <= 0) {
            assertEquals(0., actual.get(i).get("FrequencySampleTime"), 0); assertEquals(1., actual.get(i).get("Fmeas"), 0);
        }
        if (shared) {
            for (String key : List.of("TemperatureA", "TemperatureB", "Fmeas", "FrequencyOutput", "ThermalFractionA", "ThermalFractionB", "UvTimer1", "UvTimer2", "StallTimer", "StallTimerB", "RestartTimer")) {
                if (FILTERS.get(name) == 0 && (key.equals("Fmeas") || key.equals("FrequencyOutput"))) continue;
                double bound = key.startsWith("Temperature") ? 3e-6 : key.contains("Timer") ? 2e-8 : key.startsWith("Thermal") ? 5e-5 : 5e-6;
                checks.add(() -> assertTrue(((Number) ((Map<?, ?>) states.get(key)).get("maximum_absolute")).doubleValue() <= bound, key + ": " + states.get(key)));
                stateLimits.put(key, bound);
            }
            for (int i = 0; i < actual.size(); i++) for (int g = 0; g < 2; g++)
                assertEquals(ref.rows().get(i).get("VAR" + (16 + g)), actual.get(i).get(g == 0 ? "RunA" : "RunB"));
        }
        if (FILTERS.get(name) == 0) explainBypassInputs(ref, actual, out);
        var gate = Map.of("trajectory_limits", limits, "state_maximum_limits", stateLimits, "clock_policy", "phase sampling only at positive physical endpoints; all other coordinates integrate during startup", "frequency_state_parity_required", shared && FILTERS.get(name) > 0, "complete_model_parity", false);
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("method-gate.json"), 100000)) { new GsonBuilder().setPrettyPrinting().create().toJson(gate, w); }
        assertAll(name + " " + (shared ? "staggered" : "modified-euler"), checks);
        return report;
    }
    static Stream<Arguments> coupledMethods() {
        return REFERENCES.keySet().stream().map(n -> n.substring(0, n.lastIndexOf("-h"))).distinct().flatMap(n -> Stream.of(false, true).map(s -> Arguments.of(n, s)));
    }
    private static void explainBypassInputs(Acmtblu1SharedSmibTest.Reference ref, List<Map<String, Double>> actual, Path out) throws Exception {
        double previousTime = Double.NEGATIVE_INFINITY, clock = 0;
        double actualPhase = actual.getFirst().get("FrequencyPhase"), sourcePhase = recordedPhase(ref.rows().getFirst()).getArgument();
        double actualRaw = 0, sourceRaw = 0; var residual = new Error(); var sourceEquation = new Error();
        var rows = new ArrayList<Map<String, Double>>();
        for (int i = 0; i < actual.size(); i++) {
            var a = actual.get(i); var s = ref.rows().get(i); double time = s.get("time_s");
            if (time > 0 && time > previousTime) {
                double next = (double) ((float) clock + (float) ref.h());
                double ap = Math.toRadians(a.get("A2")), sp = recordedPhase(s).getArgument();
                actualRaw = Math.IEEEremainder(ap - actualPhase, 2 * Math.PI) / (2 * Math.PI * 60 * (next - clock));
                sourceRaw = Math.IEEEremainder(sp - sourcePhase, 2 * Math.PI) / (2 * Math.PI * 60 * (next - clock));
                actualPhase = ap; sourcePhase = sp; clock = next;
            } else if (time == previousTime) { actualRaw = 0; sourceRaw = 0; }
            double observed = a.get("Fmeas") - 1 - s.get("STATE1"), predicted = actualRaw - sourceRaw;
            residual.add(observed, predicted, time); sourceEquation.add(s.get("STATE1"), sourceRaw, time);
            var row = new LinkedHashMap<String, Double>(); row.put("source_time_s", time); row.put("observed_state_difference", observed); row.put("difference_from_terminal_phase_inputs", predicted); row.put("unexplained_residual", observed - predicted); row.put("source_state", s.get("STATE1")); row.put("source_phase_equation", sourceRaw); rows.add(row); previousTime = time;
        }
        var report = Map.of("status", "coupled-bypass-raw-trajectory-diagnostic-input-increment-error-localized", "unexplained_state_difference", residual.report(), "source_phase_equation_error", sourceEquation.report());
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("bypass-inputs.json"), 100000)) { new GsonBuilder().setPrettyPrinting().create().toJson(report, w); }
        var keys = rows.getFirst().keySet();
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("bypass-inputs.csv"), BenchmarkOutputGuard.estimateCsvBytes(rows.size(), keys.size()))) {
            w.write(String.join(",", keys) + "\n");
            for (var row : rows) { boolean first = true; for (String key : keys) { if (!first) w.write(","); first = false; w.write(row.get(key).toString()); } w.write("\n"); }
        }
        assertTrue(residual.maximum <= 2e-6, residual.report().toString());
        assertTrue(sourceEquation.maximum <= 2e-6, sourceEquation.report().toString());
    }
    @ParameterizedTest @MethodSource("coupledMethods")
    void bothSolversDeliverPhysicalTimeThroughFullImportedStartup(String name, boolean shared) throws Exception {
        var reports = new ArrayList<Map<String, Object>>();
        // A failing coarse gate must not suppress the independent refinement run.
        assertAll("Both complete integration intervals",
                () -> reports.add(coupled(name + "-h0.0005", shared)),
                () -> reports.add(coupled(name + "-h0.00025", shared)));
        var coarse = reports.get(0); var fine = reports.get(1);
        if (!shared) for (String key : List.of("V1", "V2", "V3", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) {
            double a = ((Number) ((Map<?, ?>) ((Map<?, ?>) coarse.get("boundary_errors")).get(key)).get("rmse")).doubleValue();
            double b = ((Number) ((Map<?, ?>) ((Map<?, ?>) fine.get("boundary_errors")).get(key)).get("rmse")).doubleValue();
            double noise = key.equals("ANGLE_DEG") ? 2e-5 : key.equals("SPEED") ? 2e-8 : 2e-7;
            assertTrue(b <= .75 * a + noise, "Step refinement must reduce " + key + " error: coarse=" + a + ", fine=" + b);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"none-h0.0005", "none-h0.00025", "none-bypass-h0.0005", "none-bypass-h0.00025"})
    void heatingCurrentReplaySeparatesThermalIntegrationFromCoupledInputError(String name) throws Exception {
        var ref = REFERENCES.get(name); var m = motor(ref);
        Path out = Files.createTempDirectory(Path.of("target"), "compressor-startup-heating-");
        Acmtblu1SharedSmibTest.runReference(name, ref, true, out, false,
                new Acmtblu1SharedSmibTest.RunSettings(2, true, 1e-4, true));
        var actual = rows(Files.readString(out.resolve("actual.csv")));
        assertEquals(ref.rows().size(), actual.size());
        var output = new ArrayList<Map<String, Double>>();
        var errors = new LinkedHashMap<String, Object>();
        var checks = new ArrayList<Executable>();
        for (int group = 0; group < 2; group++) {
            String suffix = group == 0 ? "A" : "B", temperature = "Temperature" + suffix;
            String fraction = "ThermalFraction" + suffix, sourceFraction = group == 0 ? "VAR14" : "VAR18";
            String sourceTemperature = "STATE" + (group + 2), sourceCurrent = "VAR" + (group + 19);
            // Both reconstructions start at the solved implementation state, with entered
            // coefficients. No source-state offset or coefficient fitting is permitted.
            double[] x = {actual.getFirst().get(temperature), actual.getFirst().get(temperature)};
            double[] olderRate = new double[2], leftRate = new double[2];
            var equation = new Error(); var recordedInput = new Error();
            var currentContribution = new Error(); var coupledError = new Error();
            int eventSides = 0, startupRows = 0;
            for (int j = 0; j < actual.size(); j++) {
                var a = actual.get(j); var s = ref.rows().get(j); double time = s.get("time_s");
                boolean active = a.get(fraction) != 0, sourceActive = s.get(sourceFraction) != 0;
                // Projection is an explicit prescribed-input diagnostic. Confirm that it
                // does not hide a disagreement in the actual complete-trip action sample.
                assertEquals(sourceActive, active, name + " trip side at " + time);
                if (!active) x[0] = 0;
                if (!sourceActive) x[1] = 0;
                equation.add(x[0], a.get(temperature), time);
                recordedInput.add(x[1], s.get(sourceTemperature), time);
                currentContribution.add(x[0], x[1], time);
                coupledError.add(a.get(temperature), s.get(sourceTemperature), time);
                var row = new LinkedHashMap<String, Double>();
                row.put("source_time_s", time); row.put("group", (double) group);
                row.put("actual_temperature", a.get(temperature)); row.put("reference_temperature", s.get(sourceTemperature));
                row.put("actual_heating_squared", a.get("ElectricalHeatingSquared" + suffix));
                row.put("reference_heating_squared", s.get(sourceCurrent) * s.get(sourceCurrent));
                row.put("actual_input_recurrence", x[0]); row.put("reference_input_recurrence", x[1]);
                row.put("equation_residual", x[0] - a.get(temperature));
                row.put("recorded_input_reference_error", x[1] - s.get(sourceTemperature));
                row.put("input_current_contribution", x[0] - x[1]);
                row.put("coupled_reference_error", a.get(temperature) - s.get(sourceTemperature));
                output.add(row);
                if (time < 0) startupRows++;
                if (j + 1 == actual.size()) continue;
                double[] heat = {a.get("ElectricalHeatingSquared" + suffix), s.get(sourceCurrent) * s.get(sourceCurrent)};
                double[] rates = new double[2];
                for (int k = 0; k < 2; k++) rates[k] = active ? (heat[k] * m.getRstall() - x[k]) / m.getTth() : 0;
                if (time == ref.rows().get(j + 1).get("time_s")) {
                    System.arraycopy(rates, 0, leftRate, 0, 2); eventSides++; continue;
                }
                int grid = (int) Math.round((time + 2 * ref.h()) / ref.h());
                int nextGrid = (int) Math.round((ref.rows().get(j + 1).get("time_s") + 2 * ref.h()) / ref.h());
                assertEquals(grid + 1, nextGrid, "No missing integration samples");
                boolean afterEvent = j > 0 && time == ref.rows().get(j - 1).get("time_s");
                for (int k = 0; k < 2; k++) {
                    double rate = afterEvent ? .5 * (rates[k] + leftRate[k]) : rates[k];
                    x[k] += ref.h() * (1.5 * rate - .5 * olderRate[k]); olderRate[k] = rate;
                }
            }
            assertEquals(2, startupRows); assertEquals(4, eventSides);
            errors.put(suffix, Map.of("equation_residual", equation.report(), "recorded_input_reference_error", recordedInput.report(),
                    "input_current_contribution", currentContribution.report(), "coupled_reference_error", coupledError.report()));
            checks.add(() -> assertTrue(equation.maximum <= 2e-13, "Independent thermal recurrence: " + equation.report()));
            double inputLimit = ref.h() == .0005 ? 4.6e-7 : 1.1e-6;
            checks.add(() -> assertTrue(recordedInput.maximum <= inputLimit, "Recorded-current diagnostic: " + recordedInput.report()));
        }
        var report = Map.of("profile", name, "status", "input-localization-only-not-coupled-acceptance",
                "initial_state", "solved implementation state", "coefficients", "entered model values",
                "thermal_trip_projection", "recorded zero-fraction side; coupled trip sides checked separately",
                "errors", errors);
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("thermal-inputs.json"), 100000)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(report, w);
        }
        var keys = output.getFirst().keySet();
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("thermal-inputs.csv"), BenchmarkOutputGuard.estimateCsvBytes(output.size(), keys.size()))) {
            w.write(String.join(",", keys) + "\n");
            for (var row : output) {
                boolean first = true;
                for (String key : keys) { if (!first) w.write(","); first = false; w.write(row.get(key).toString()); }
                w.write("\n");
            }
        }
        assertAll(name, checks);
    }
}
