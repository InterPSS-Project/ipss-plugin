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

/** Independent short-lag boundary, shared preparation and coupled SMIB controls. */
public class CompressorShortLagTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "CompressorShortLagTest");
    private static final Map<String, Acmtblu1SharedSmibTest.Reference> REFERENCES = new TreeMap<>();
    private static final Map<String, double[]> CONSTANTS = new TreeMap<>();
    private static String sha(byte[] raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
    private static List<Map<String, Double>> rows(String text) {
        String[] lines = text.strip().split("\\R"), keys = lines[0].split(","); var result = new ArrayList<Map<String, Double>>();
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(","); assertEquals(keys.length, values.length); var row = new LinkedHashMap<String, Double>();
            for (int j = 0; j < keys.length; j++) { double v = Double.parseDouble(values[j]); assertTrue(Double.isFinite(v)); row.put(keys[j], v); }
            result.add(row);
        }
        return result;
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Path archive = Path.of(CONFIG.string("reference.archive", "testData/private/verification-artifacts/acmtblu1-short-lags-20261101/captures.zip"));
        Assumptions.assumeTrue(Files.isRegularFile(archive), "Retain independent short-lag references");
        assertEquals("effe34b21222ab10de1c3d05ed5fb7c88ca030a3dd45475388940168ff34d40f", sha(Files.readAllBytes(archive)));
        try (var z = new ZipFile(archive.toFile())) {
            var manifest = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : manifest.getAsJsonObject("files").entrySet()) assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : manifest.getAsJsonObject("profiles").entrySet()) {
                var p = e.getValue().getAsJsonObject(); byte[] raw = z.getInputStream(z.getEntry(p.get("entry").getAsString())).readAllBytes();
                assertArrayEquals(raw, z.getInputStream(z.getEntry(p.get("repeat_entry").getAsString())).readAllBytes());
                var source = rows(new String(raw, StandardCharsets.UTF_8)); assertEquals(p.get("rows").getAsInt(), source.size()); assertEquals(59, source.getFirst().size());
                assertEquals(2, source.stream().filter(r -> r.get("time_s") < 0).count());
                int duplicates = 0; double previous = Double.NEGATIVE_INFINITY;
                for (var r : source) {
                    double time = r.get("time_s"); assertTrue(time >= previous); if (time == previous) duplicates++; previous = time;
                    for (String input : List.of("VAR0", "VAR13")) assertEquals(r.get("V2"), r.get(input), 2e-7, "Reject stale electrical inputs");
                }
                assertEquals(4, duplicates);
                double[] constants = new double[35]; var entered = p.getAsJsonArray("entered_constants"); var initialized = p.getAsJsonArray("initialized_constants");
                for (int i = 0; i < constants.length; i++) { constants[i] = initialized.get(i).getAsDouble(); assertEquals(entered.get(i).getAsDouble(), constants[i]); }
                CONSTANTS.put(e.getKey(), constants);
                String dyr = new String(z.getInputStream(z.getEntry(p.get("dyr").getAsString())).readAllBytes(), StandardCharsets.UTF_8);
                REFERENCES.put(e.getKey(), new Acmtblu1SharedSmibTest.Reference(p.get("step_sec").getAsDouble(), .01, .82, dyr, source));
            }
        }
        assertEquals(27, REFERENCES.size());
    }
    @AfterAll static void release() { REFERENCES.clear(); CONSTANTS.clear(); }
    static Stream<Arguments> methods() { return REFERENCES.keySet().stream().flatMap(n -> Stream.of(false, true).map(s -> Arguments.of(n, s))); }
    static Stream<String> profiles() { return REFERENCES.keySet().stream(); }
    private static LD1PACImpl motor(Acmtblu1SharedSmibTest.Reference ref) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("target"), "compressor-short-lag-input-"); Files.writeString(dir.resolve("dynamic.dyr"), ref.dyr());
        var loader = new PSSEMultiFileLoader(); var c = loader.loadDStab("testData/adpter/psse/v33/HspModels/load-network.raw", dir.resolve("dynamic.dyr").toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete()); var a = c.getDynSimuAlgorithm(); assertTrue(a.getAclfAlgorithm().loadflow()); a.setSimuStepSec(ref.h());
        a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor()); assertTrue(a.initialization());
        return assertInstanceOf(LD1PACImpl.class, c.getDStabilityNet().getDStabBus("Bus2").getDynLoadModelList().getFirst());
    }
    private static boolean bypass(double time, double h) { return (float) time <= 2.0 * (float) h; }
    @ParameterizedTest @MethodSource("profiles")
    void nativeCoordinatesDiscriminateBypassFromDifferentialBranches(String name) throws Exception {
        var ref = REFERENCES.get(name); var c = CONSTANTS.get(name); var m = motor(ref);
        // Decimal DYR readers can differ by one binary32 ulp; API controls preserve exact boundary values.
        assertEquals(c[2], (double) (float) m.getTv(), Math.ulp((float) c[2])); assertEquals(c[3], (double) (float) m.getTf(), Math.ulp((float) c[3])); assertEquals(c[27], (double) (float) m.getTth(), Math.ulp((float) c[27]));
        assertEquals(bypass(c[2], ref.h()), m.getEffectiveTv() == 0); assertEquals(bypass(c[3], ref.h()), m.getEffectiveTf() == 0); assertEquals(bypass(c[27], ref.h()), m.getEffectiveTth() == 0);
        double voltageResidual = 0, thermalResidual = 0;
        for (var row : ref.rows()) {
            voltageResidual = Math.max(voltageResidual, Math.abs(row.get("STATE0") - row.get("V2")));
            thermalResidual = Math.max(thermalResidual, Math.abs(row.get("STATE2") - c[7] * Math.pow(row.get("VAR19"), 2)));
        }
        if (bypass(c[2], ref.h())) assertTrue(voltageResidual <= 2e-7); else assertTrue(voltageResidual > .1);
        if (bypass(c[27], ref.h())) assertTrue(thermalResidual <= 5e-7); else assertTrue(thermalResidual > .1);
        if (name.startsWith("boundary-tf")) {
            var below = REFERENCES.get("boundary-tf--1").rows(); var equal = REFERENCES.get("boundary-tf-0").rows(); var above = REFERENCES.get("boundary-tf-1").rows(); double difference = 0;
            for (int i = 0; i < below.size(); i++) { assertEquals(below.get(i).get("STATE1"), equal.get(i).get("STATE1")); difference = Math.max(difference, Math.abs(equal.get(i).get("STATE1") - above.get(i).get("STATE1"))); }
            assertTrue(difference > .01, "One-ulp boundary controls distinguish the frequency branches");
        }
    }
    @Test void preparationPreservesParametersAndAcceptedHistoriesAndSupportsLiveWrites() throws Exception {
        var m = motor(REFERENCES.get("boundary-tv-0")); var states = m.getIntegrationStates(); var history = m.getIntegrationHistories(); double raw = m.getTv();
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE, Double.MAX_VALUE}) assertThrows(IllegalArgumentException.class, () -> m.configureIntegrationStep(invalid));
        m.configureIntegrationStep(.0005); assertEquals(raw, m.getTv()); assertEquals(raw, m.getEffectiveTv());
        m.configureIntegrationStep(1.0 / 240); assertEquals(0, m.getEffectiveTv()); assertEquals(states, m.getIntegrationStates()); assertEquals(history, m.getIntegrationHistories());
        m.setTv(Math.nextUp((float) raw)); assertEquals(m.getTv(), m.getEffectiveTv()); m.setTv(raw); assertEquals(0, m.getEffectiveTv());
        m.setTf(.004); m.setTth(.004); assertEquals(0, m.getEffectiveTf()); assertEquals(0, m.getEffectiveTth());
        m.configureIntegrationStep(.0005); assertEquals(.004, m.getEffectiveTf()); assertEquals(.004, m.getEffectiveTth());
        m.configureIntegrationStep(1.0 / 240); m.prepareIntegrationStep(0, .0001); m.evaluateStaggered(.0001, new double[9], 0);
        assertEquals(0, m.getEffectiveTv()); assertEquals(0, m.getEffectiveTf()); assertEquals(0, m.getEffectiveTth(), "Local stage intervals cannot change prepared lag branches");
        assertTrue(m.initStates()); assertEquals(0, m.getEffectiveTv());
    }
    @Test void typedAndZoneMotorConventionsRemainDifferential() {
        for (var policy : List.of(LD1PACImpl.CharacteristicPolicy.STANDALONE, LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE)) {
            var m = new LD1PACImpl(); m.setCharacteristicPolicy(policy); m.setTv(.004); m.setTf(.004); m.setTth(.004); m.configureIntegrationStep(1.0 / 240);
            assertEquals(.004, m.getEffectiveTv()); assertEquals(.004, m.getEffectiveTf()); assertEquals(.004, m.getEffectiveTth());
        }
    }
    @Test void positiveLagsKeepTheModifiedEulerStageEquationsAndSharedRhs() throws Exception {
        var ref = REFERENCES.get("boundary-tv-1"); var m = CompressorProtectionConformanceTest.motor(ref);
        double h = ref.h(), tau = Math.nextUp((float) (2 * (float) h)); m.setTv(tau); m.setTf(tau);
        var bus = m.getDStabBus(); bus.setVoltage(new Complex(.8, 0)); bus.setFreq(.99);
        m.acceptNetworkEventInput(.05);
        double[] x = new double[9], rates = new double[9]; m.readStaggeredState(x, 0); double oldV = x[0], oldF = x[1];
        m.evaluateStaggered(h / 8, rates, 0); assertEquals((.8 - oldV) / tau, rates[0], 1e-12); assertEquals((.99 - oldF) / tau, rates[1], 1e-12);
        double alpha = h / tau; assertTrue(m.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertEquals(oldV + alpha * (.8 - oldV), m.getMeasuredVoltage(), 1e-14); assertEquals(oldF + alpha * (.99 - oldF), m.getMeasuredFrequency(), 1e-14);
        assertTrue(m.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertEquals(oldV + (alpha - .5 * alpha * alpha) * (.8 - oldV), m.getMeasuredVoltage(), 1e-14);
        assertEquals(oldF + (alpha - .5 * alpha * alpha) * (.99 - oldF), m.getMeasuredFrequency(), 1e-14);
        m.setTv(2 * (float) h); m.setTf(2 * (float) h); m.setTth(2 * (float) h);
        m.evaluateStaggered(h / 8, rates, 0); for (int i = 0; i < 4; i++) assertEquals(0, rates[i], "Bypass has no hidden differential rate");
    }
    @Test void preparedAlgebraicRhsReusesNumericalStorage() throws Exception {
        var generic = java.lang.management.ManagementFactory.getThreadMXBean(); Assumptions.assumeTrue(generic instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) generic; Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported()); bean.setThreadAllocatedMemoryEnabled(true);
        var m = motor(REFERENCES.get("boundary-tth-0")); m.setTv(.004); m.setTf(.004); double[] rates = new double[9];
        for (int i = 0; i < 50000; i++) m.evaluateStaggered(.0001, rates, 0);
        long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 20000; i++) m.evaluateStaggered(.0001, rates, 0);
        assertEquals(0, bean.getThreadAllocatedBytes(thread) - before);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void legacyCompositeReceivesOuterIntervalBeforeItsMotorIsAttached(boolean shared) throws Exception {
        String source = Files.readString(Path.of("testData/adpter/psse/v33/HspModels/cmldblu1-restart.dyr"));
        int pos = source.indexOf("'USRLOD'"), start = source.lastIndexOf('\n', pos) + 1; String[] fields = source.substring(start, source.lastIndexOf('/')).strip().split("\\s+");
        int constants = 11 + Integer.parseInt(fields[6]); for (int offset : new int[]{99, 100, 124}) fields[constants + offset] = ".004";
        Path dir = Files.createTempDirectory(Path.of("target"), "composite-short-lags-"); Files.writeString(dir.resolve("dynamic.dyr"), source.substring(0, start) + String.join(" ", fields) + " /\n");
        var loader = new PSSEMultiFileLoader(); var c = loader.loadDStab("testData/adpter/psse/v33/HspModels/load-network.raw", dir.resolve("dynamic.dyr").toString()); assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        var root = assertInstanceOf(org.interpss.dstab.dynLoad.impl.Cmldblu1Model.class, c.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel());
        var m = assertInstanceOf(LD1PACImpl.class, root.get1PhaseACMotor()); assertEquals(.004, m.getEffectiveTv());
        var a = c.getDynSimuAlgorithm(); assertTrue(a.getAclfAlgorithm().loadflow()); a.setSimuStepSec(1.0 / 240); a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if (shared) a.setSolver(new org.interpss.dstab.algo.StaggeredEulerSolver(a)); assertTrue(a.initialization());
        assertEquals(0, m.getEffectiveTv()); assertEquals(0, m.getEffectiveTf()); assertEquals(0, m.getEffectiveTth()); assertEquals(.004, m.getTv()); assertEquals(.004, m.getTf()); assertEquals(.004, m.getTth());
        assertTrue(a.solveDEqnStep(true));
    }
    @ParameterizedTest @MethodSource("methods")
    void independentCoupledFaultsRetainAllSamplesAndMethodSpecificStates(String name, boolean shared) throws Exception {
        var ref = REFERENCES.get(name); Path out = Files.createTempDirectory(Path.of("target"), "compressor-short-lags-coupled-");
        var report = Acmtblu1SharedSmibTest.runReference(name, ref, shared, out, false, new Acmtblu1SharedSmibTest.RunSettings(2, shared, 1e-4, true));
        var boundary = (Map<?, ?>) report.get("boundary_errors"); var states = (Map<?, ?>) report.get("state_errors"); var limits = new LinkedHashMap<String, Double>();
        for (String key : List.of("V1", "V2", "V3", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) {
            String norm = List.of("ANGLE_DEG", "SPEED").contains(key) ? "rmse" : "range_normalized_rmse";
            double bound = shared ? key.equals("ANGLE_DEG") ? .0018 : key.equals("SPEED") ? 5e-7 : key.equals("V1") || key.equals("P_PU") ? 9e-5 : key.equals("Q_PU") ? 7e-5 : 2e-5
                    : 2 * ref.h() / .0005 * (key.equals("ANGLE_DEG") ? .01 : key.equals("SPEED") ? 5e-6 : key.startsWith("V") ? 1e-4 : 1e-3);
            limits.put(key + "_" + norm, bound); assertTrue(((Number) ((Map<?, ?>) boundary.get(key)).get(norm)).doubleValue() <= bound, key + ": " + boundary.get(key));
        }
        if (shared) for (String key : List.of("Vmeas", "Fmeas", "TemperatureA", "TemperatureB", "UvTimer1", "UvTimer2", "StallTimer", "StallTimerB", "RestartTimer", "ThermalFractionA", "ThermalFractionB")) {
            double bound = key.equals("Vmeas") ? 8e-5 : key.equals("Fmeas") ? 7e-6 : key.startsWith("Temperature") ? 2.5e-5 : key.startsWith("ThermalFraction") ? 8e-6 : 2e-8;
            limits.put(key + "_maximum_absolute", bound); assertTrue(((Number) ((Map<?, ?>) states.get(key)).get("maximum_absolute")).doubleValue() <= bound, key + ": " + states.get(key));
        }
        // Algebraic projection must explain the complete voltage-state error, under either method.
        if (bypass(CONSTANTS.get(name)[2], ref.h())) {
            var actual = rows(Files.readString(out.resolve("actual.csv"))); assertEquals(ref.rows().size(), actual.size());
            for (int i = 0; i < actual.size(); i++) assertEquals(actual.get(i).get("V2"), actual.get(i).get("Vmeas"), 3e-12);
        }
        try (var w = BenchmarkOutputGuard.newWriter(out.resolve("method-gate.json"), 100000)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("limits", limits, "complete_model_parity", false,
                    "differential_modified_euler_states", "diagnostic; independently tested numerical equations retain their method-specific realization"), w);
        }
    }
}
