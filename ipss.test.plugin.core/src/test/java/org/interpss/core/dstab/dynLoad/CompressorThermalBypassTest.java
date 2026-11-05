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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Zero thermal-time-constant equations, accepted actions and independent SMIB controls. */
public class CompressorThermalBypassTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "CompressorThermalBypassTest");
    private static final Map<String, Acmtblu1SharedSmibTest.Reference> REFERENCES = new TreeMap<>();
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
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
        read(Path.of(CONFIG.string("reference.archive", "testData/private/verification-artifacts/acmtblu1-thermal-parameters-20261101/captures.zip")), "84a128ef73c2b65b5384f583c52bb0b0aa04286832fab94687d94568beeb476f");
        read(Path.of(CONFIG.string("reference.refinements", "testData/private/verification-artifacts/acmtblu1-thermal-parameters-20261101/refinements.zip")), "2ff2b205263a909cbf3d61bf7f94b8b18defec146b362d5f311e4609a82b5fd4");
        assertEquals(6, REFERENCES.size());
    }
    private static void read(Path path, String hash) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(path), "Retain independent thermal-bypass captures"); assertEquals(hash, sha(Files.readAllBytes(path)));
        try (var z = new ZipFile(path.toFile())) {
            var manifest = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : manifest.getAsJsonObject("files").entrySet()) assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : manifest.getAsJsonObject("profiles").entrySet()) {
                var p = e.getValue().getAsJsonObject(); String entry = p.get("entry").getAsString(), directory = entry.substring(0, entry.lastIndexOf('/') + 1);
                byte[] csv = z.getInputStream(z.getEntry(entry)).readAllBytes(); assertArrayEquals(csv, z.getInputStream(z.getEntry(entry.replace("-a-part", "-b-part"))).readAllBytes());
                var source = rows(new String(csv, StandardCharsets.UTF_8)); assertEquals(59, source.getFirst().size()); assertEquals(p.get("rows").getAsInt(), source.size());
                assertEquals(2, source.stream().filter(r -> r.get("time_s") < 0).count()); int duplicates = 0; double previous = Double.NEGATIVE_INFINITY;
                for (var r : source) { double time = r.get("time_s"); assertTrue(time >= previous); if (time == previous) duplicates++; previous = time; assertEquals(r.get("V2"), r.get("VAR0")); }
                assertEquals(4, duplicates);
                var metadata = JsonParser.parseString(new String(z.getInputStream(z.getEntry(directory + "manifest.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(0., metadata.getAsJsonArray("initialized_constants").get(27).getAsDouble(), 0);
                REFERENCES.put(e.getKey(), new Acmtblu1SharedSmibTest.Reference(p.get("step_sec").getAsDouble(), .01, .82,
                        new String(z.getInputStream(z.getEntry(directory + "dynamic.dyr")).readAllBytes(), StandardCharsets.UTF_8), source));
            }
        }
    }
    @AfterAll static void release() { REFERENCES.clear(); }
    static Stream<Arguments> methods() { return REFERENCES.keySet().stream().flatMap(n -> Stream.of(false, true).map(s -> Arguments.of(n, s))); }
    private static LD1PACImpl motor() throws Exception { return CompressorProtectionConformanceTest.motor(REFERENCES.get("zero-h0.0005")); }
    @ParameterizedTest @ValueSource(strings = {"-1", "NaN", "Infinity"})
    void invalidThermalTimesCannotAttachAPartialNativeModel(String time) throws Exception {
        String source = REFERENCES.get("zero-h0.0005").dyr(); int model = source.indexOf("'USRLOD'"), start = source.lastIndexOf('\n', model) + 1;
        String[] fields = source.substring(start, source.lastIndexOf('/')).strip().split("\\s+"); assertEquals(46, fields.length); fields[38] = time;
        Path dir = Files.createTempDirectory(Path.of("target"), "compressor-thermal-bypass-invalid-"); Files.writeString(dir.resolve("dynamic.dyr"), source.substring(0, start) + String.join(" ", fields) + " /\n");
        var loader = new PSSEMultiFileLoader(); var context = loader.loadDStab("testData/adpter/psse/v33/HspModels/load-network.raw", dir.resolve("dynamic.dyr").toString());
        assertFalse(loader.getLastDynamicImportReport().isStrictlyComplete()); assertTrue(context.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().isEmpty());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bypassRatesAndAppliedStorageAreFiniteUnderBothMethods(boolean shared) throws Exception {
        var m = motor(); assertEquals(0, m.getTth()); double[] x = new double[9], rates = new double[9]; var bus = m.getDStabBus();
        for (double magnitude : new double[]{.2, .9, 1.03, 0}) {
            bus.setVoltage(new Complex(magnitude, 0)); m.getNortonCurInj();
            double temperature = m.getIntegrationStates().get("TemperatureA"); var histories = m.getIntegrationHistories();
            m.readStaggeredState(x, 0); x[2] = 999; x[3] = -999; m.applyStaggeredState(x, 0, 0);
            assertEquals(temperature, m.getIntegrationStates().get("TemperatureA"), 0);
            for (int i = 0; i < 5; i++) { m.evaluateStaggered(.0005, rates, 0); assertEquals(0, rates[2]); assertEquals(0, rates[3]); for (double v : rates) assertTrue(Double.isFinite(v)); }
            assertTrue(m.afterStep(0)); assertEquals(histories, m.getIntegrationHistories());
            if (shared) m.completeStaggeredStep(.0005);
            else { assertTrue(m.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 0)); assertTrue(m.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 1)); }
            assertTrue(Double.isFinite(m.getIntegrationStates().get("TemperatureA"))); assertTrue(Double.isFinite(m.getIntegrationStates().get("TemperatureB")));
        }
    }
    @Test void networkRefreshDoesNotCommitThermalTripOrAdvanceAcceptedHeating() throws Exception {
        var m = motor(); var bus = m.getDStabBus(); double[] x = new double[9];
        bus.setVoltage(new Complex(.1, 0)); m.acceptNetworkEventInput(.05); m.readStaggeredState(x, 0); x[6] = x[7] = m.getTstall() + .001;
        m.applyStaggeredState(x, 0, 0); m.completeStaggeredStep(.0005); assertTrue(m.afterStep(.0005));
        bus.setVoltage(new Complex(.88, 0)); m.acceptNetworkEventInput(.1); double action = m.getIntegrationHistories().get("ThermalActionTemperatureA");
        assertTrue(action > m.getTh2t()); assertEquals(1., m.getIntegrationHistories().get("ThermalFractionA"));
        bus.setVoltage(new Complex(.89, 0)); var before = m.getIntegrationHistories();
        for (int i = 0; i < 5; i++) { m.getNortonCurInj(); m.evaluateStaggered(.0005, x, 0); }
        assertEquals(action, m.getIntegrationHistories().get("ThermalActionTemperatureA"), 0); assertEquals(before, m.getIntegrationHistories());
        assertTrue(m.afterStep(0)); assertEquals(1., m.getIntegrationHistories().get("ThermalFractionA"));
        assertTrue(m.afterStep(.0005)); assertEquals(0., m.getIntegrationHistories().get("ThermalFractionA"));
        for (double v : new double[]{.5, 1.05}) { bus.setVoltage(new Complex(v, 0)); m.getNortonCurInj(); assertEquals(0., m.getIntegrationStates().get("TemperatureA"), 0); }
    }
    @Test void zeroThermalRhsReusesNumericalStorage() throws Exception {
        var generic = java.lang.management.ManagementFactory.getThreadMXBean(); Assumptions.assumeTrue(generic instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) generic; Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported()); bean.setThreadAllocatedMemoryEnabled(true);
        var m = motor(); double[] rates = new double[9]; for (int i = 0; i < 50000; i++) m.evaluateStaggered(.0005, rates, 0);
        long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 20000; i++) m.evaluateStaggered(.0005, rates, 0);
        assertEquals(0, bean.getThreadAllocatedBytes(thread) - before);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void legacyCompositeAttachmentUsesTheSameZeroThermalCoordinates(boolean shared) throws Exception {
        String source = Files.readString(Path.of("testData/adpter/psse/v33/HspModels/cmldblu1-restart.dyr"));
        int model = source.indexOf("'USRLOD'"), start = source.lastIndexOf('\n', model) + 1;
        String[] fields = source.substring(start, source.lastIndexOf('/')).strip().split("\\s+");
        int constants = 11 + Integer.parseInt(fields[6]); assertEquals(132, Integer.parseInt(fields[7])); fields[constants + 124] = "0";
        Path dir = Files.createTempDirectory(Path.of("target"), "composite-thermal-bypass-"); Files.writeString(dir.resolve("dynamic.dyr"), source.substring(0, start) + String.join(" ", fields) + " /\n");
        var loader = new PSSEMultiFileLoader(); var context = loader.loadDStab("testData/adpter/psse/v33/HspModels/load-network.raw", dir.resolve("dynamic.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete()); var a = context.getDynSimuAlgorithm(); assertTrue(a.getAclfAlgorithm().loadflow()); a.setSimuStepSec(.0005);
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor()); if (shared) a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a)); assertTrue(a.initialization());
        var composite = assertInstanceOf(org.interpss.dstab.dynLoad.impl.Cmldblu1Model.class, context.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel());
        var m = assertInstanceOf(LD1PACImpl.class, composite.get1PhaseACMotor()); assertEquals(0, m.getTth()); assertEquals(9, m.staggeredStateCount());
        double[] rates = new double[9]; m.evaluateStaggered(.0005, rates, 0); assertEquals(0, rates[2]); assertEquals(0, rates[3]);
        for (double r : rates) assertTrue(Double.isFinite(r)); assertTrue(a.solveDEqnStep(true));
    }
    private static final class Error {
        double max, sum, time; int n;
        void add(double actual, double expected, double at) { double d = actual - expected; assertTrue(Double.isFinite(d)); if (Math.abs(d) > max) { max = Math.abs(d); time = at; } sum += d * d; n++; }
        Map<String, Object> report() { return Map.of("maximum_absolute", max, "rmse", Math.sqrt(sum / n), "source_time_of_maximum", time, "samples", n); }
    }
    private static Complex voltage(Map<String, Double> r) { double a = Math.toRadians(r.get("A2")), v = r.get("V2"); return new Complex(v * Math.cos(a), v * Math.sin(a)); }
    @ParameterizedTest @MethodSource("methods")
    void recordedElectricalInputsQualifyAlgebraicTemperaturesAndAcceptedActions(String name, boolean shared) throws Exception {
        var ref = REFERENCES.get(name); var m = CompressorProtectionConformanceTest.motor(ref); var bus = m.getDStabBus(); var timers = new CompressorTimerReplay(m, shared);
        var errors = new LinkedHashMap<String, Error>(); for (String key : List.of("TemperatureA", "TemperatureB", "ThermalFractionA", "ThermalFractionB", "P", "Q")) errors.put(key, new Error());
        var output = new ArrayList<Map<String, Double>>(); int previous = 0;
        for (var source : ref.rows()) {
            double time = source.get("time_s"); int grid = (int) Math.round((time + 2 * ref.h()) / ref.h()); boolean advance = grid > previous, event = !advance && !output.isEmpty();
            if (event) timers.beforeEvent(ref.h()); double[] x = advance ? timers.advance(ref.h()) : timers.current();
            bus.setVoltage(voltage(source)); bus.setFreq(source.get("VAR1")); x[0] = source.get("STATE0"); x[1] = source.get("VAR1"); x[2] = -100; x[3] = 100;
            m.applyStaggeredState(x, 0, 0);
            if (advance) { assertEquals(previous + 1, grid); m.completeStaggeredStep(ref.h()); assertTrue(m.afterStep(ref.h())); previous = grid; }
            else if (event) timers.acceptEvent(time);
            var v = bus.getVoltage(); var current = v.multiply(m.getPosSeqEquivY()).subtract(m.getNortonCurInj()); var power = v.multiply(current.conjugate()).multiply(bus.getNetwork().getBaseMva() / m.getMvaBase());
            var states = m.getIntegrationStates(); var h = m.getIntegrationHistories(); var row = new LinkedHashMap<String, Double>(); row.put("source_time_s", time); row.put("replay_time_s", -2 * ref.h() + grid * ref.h());
            for (int g = 0; g < 2; g++) {
                String key = "Temperature" + "AB".substring(g, g + 1); double value = states.get(key); errors.get(key).add(value, source.get("STATE" + (2 + g)), time); row.put(key, value); row.put("source_" + key, source.get("STATE" + (2 + g)));
                key = "ThermalFraction" + "AB".substring(g, g + 1); value = h.get(key); errors.get(key).add(value, source.get(g == 0 ? "VAR14" : "VAR18"), time); row.put(key, value); row.put("source_" + key, source.get(g == 0 ? "VAR14" : "VAR18"));
                row.put("Run" + "AB".substring(g, g + 1), h.get("Run" + "AB".substring(g, g + 1))); row.put("source_Run" + "AB".substring(g, g + 1), source.get("VAR" + (16 + g)));
            }
            errors.get("P").add(power.getReal(), source.get("VAR2") / source.get("VAR26"), time); errors.get("Q").add(power.getImaginary(), source.get("VAR3") / source.get("VAR26"), time);
            row.put("P", power.getReal()); row.put("Q", power.getImaginary()); row.putAll(h); output.add(row);
            double[] rates = new double[9]; m.evaluateStaggered(ref.h(), rates, 0); assertEquals(0, rates[2]); assertEquals(0, rates[3]);
        }
        Path out = Files.createTempDirectory(Path.of("target"), "compressor-thermal-bypass-replay-"); var report = new LinkedHashMap<String, Object>();
        report.put("profile", name); report.put("method", shared ? "staggered" : "modified-euler"); report.put("status", "recorded-voltage-frequency-thermal-bypass-independent-timers"); for (var e : errors.entrySet()) report.put(e.getKey(), e.getValue().report());
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"), 100000)) { new GsonBuilder().setPrettyPrinting().create().toJson(report, w); }
        var keys = output.getFirst().keySet(); try (var w = BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"), BenchmarkOutputGuard.estimateCsvBytes(output.size(), keys.size()))) {
            w.write(String.join(",", keys) + "\n"); for (var r : output) { boolean first = true; for (String key : keys) { if (!first) w.write(","); first = false; w.write(r.get(key).toString()); } w.write("\n"); }
        }
        for (String key : List.of("TemperatureA", "TemperatureB", "P", "Q")) assertTrue(errors.get(key).max <= 2.5e-6, key + errors.get(key).report());
        for (String key : List.of("ThermalFractionA", "ThermalFractionB")) assertTrue(errors.get(key).max <= 1e-6, key + errors.get(key).report());
    }
    private static Map<String, Object> coupled(String name, boolean shared) throws Exception {
        var ref = REFERENCES.get(name); Path out = Files.createTempDirectory(Path.of("target"), "compressor-thermal-bypass-coupled-");
        var report = Acmtblu1SharedSmibTest.runReference(name, ref, shared, out, false, new Acmtblu1SharedSmibTest.RunSettings(2, shared, 1e-4, true));
        var errors = (Map<?, ?>) report.get("boundary_errors");
        var limits = new LinkedHashMap<String, Double>(); var stateLimits = new LinkedHashMap<String, Double>();
        for (String key : List.of("V1", "V2", "V3", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) {
            String norm = key.equals("ANGLE_DEG") || key.equals("SPEED") ? "rmse" : "range_normalized_rmse";
            double bound = shared ? (key.equals("ANGLE_DEG") ? 2e-4 : key.equals("SPEED") ? 2e-7 : key.equals("P_PU") ? 4e-5 : key.equals("Q_PU") ? 3e-6 : 3e-6)
                    : ref.h() / .0005 * (key.equals("ANGLE_DEG") ? .01 : key.equals("SPEED") ? 5e-6 : key.startsWith("V") ? 1e-4 : 1e-3);
            assertTrue(((Number) ((Map<?, ?>) errors.get(key)).get(norm)).doubleValue() <= bound, key + ": " + errors.get(key));
            limits.put(key + "_" + norm, bound);
        }
        var actual = rows(Files.readString(out.resolve("actual.csv"))); var states = (Map<?, ?>) report.get("state_errors");
        for (String key : List.of("TemperatureA", "TemperatureB", "ThermalFractionA", "ThermalFractionB")) {
            double bound = key.startsWith("Temperature") ? (shared ? 3e-5 : .001 * ref.h() / .0005) : shared ? 2e-6 : 1e-5;
            assertTrue(((Number) ((Map<?, ?>) states.get(key)).get("maximum_absolute")).doubleValue() <= bound, key + ": " + states.get(key)); stateLimits.put(key, bound);
        }
        if (shared) for (String key : List.of("UvTimer1", "UvTimer2", "StallTimer", "StallTimerB", "RestartTimer"))
            assertTrue(((Number) ((Map<?, ?>) states.get(key)).get("maximum_absolute")).doubleValue() <= 2e-8, key + ": " + states.get(key));
        var sourceEquation = new Error(); var inputResidual = new Error();
        double sourceResistance = (double) (float) .11, resistance = .11;
        var inputRows = new ArrayList<Map<String, Double>>();
        for (int i = 0; i < actual.size(); i++) for (int g = 0; g < 2; g++) {
            var a = actual.get(i); var s = ref.rows().get(i); String group = "AB".substring(g, g + 1); double time = s.get("time_s");
            assertEquals(s.get("VAR" + (16 + g)), a.get("Run" + group), "Every run/stall action remains on the source grid");
            double predicted = resistance * Math.pow(a.get("MotorCurrent" + group), 2) - sourceResistance * Math.pow(s.get("VAR" + (19 + g)), 2);
            double observed = a.get("Temperature" + group) - s.get("STATE" + (2 + g));
            inputResidual.add(observed, predicted, time); sourceEquation.add(s.get("STATE" + (2 + g)), sourceResistance * Math.pow(s.get("VAR" + (19 + g)), 2), time);
            var row = new LinkedHashMap<String, Double>(); row.put("source_time_s", time); row.put("group", (double) g); row.put("temperature_difference", observed); row.put("difference_from_individual_current_inputs", predicted); row.put("unexplained_difference", observed - predicted); inputRows.add(row);
        }
        var gate = Map.of("trajectory_limits", limits, "state_maximum_limits", stateLimits, "source_temperature_equation", sourceEquation.report(), "unexplained_temperature_difference", inputResidual.report(), "complete_model_parity", false);
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("method-gate.json"), 100000)) { new GsonBuilder().setPrettyPrinting().create().toJson(gate, w); }
        var keys = inputRows.getFirst().keySet();
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("thermal-inputs.csv"), BenchmarkOutputGuard.estimateCsvBytes(inputRows.size(), keys.size()))) {
            w.write(String.join(",", keys) + "\n"); for (var row : inputRows) { boolean first = true; for (String key : keys) { if (!first) w.write(","); first = false; w.write(row.get(key).toString()); } w.write("\n"); }
        }
        assertTrue(sourceEquation.max <= 5e-7, sourceEquation.report().toString()); assertTrue(inputResidual.max <= 5e-7, inputResidual.report().toString());
        return report;
    }
    static Stream<Arguments> coupledMethods() { return Stream.of("zero", "initial-partial", "initial-full").flatMap(n -> Stream.of(false, true).map(s -> Arguments.of(n, s))); }
    @ParameterizedTest @MethodSource("coupledMethods")
    void fullImportedZeroThermalControlsRetainSourceActionsAndRefine(String name, boolean shared) throws Exception {
        var coarse = coupled(name + "-h0.0005", shared); var fine = coupled(name + "-h0.00025", shared);
        if (!shared) for (String key : List.of("V1", "V2", "V3", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) {
            double a = ((Number) ((Map<?, ?>) ((Map<?, ?>) coarse.get("boundary_errors")).get(key)).get("rmse")).doubleValue();
            double b = ((Number) ((Map<?, ?>) ((Map<?, ?>) fine.get("boundary_errors")).get(key)).get("rmse")).doubleValue();
            double floor = key.equals("ANGLE_DEG") ? 2e-5 : key.equals("SPEED") ? 2e-8 : 2e-7;
            assertTrue(b <= .75 * a + floor, "Refinement must reduce " + key + " error: coarse=" + a + ", fine=" + b);
        }
    }
}
