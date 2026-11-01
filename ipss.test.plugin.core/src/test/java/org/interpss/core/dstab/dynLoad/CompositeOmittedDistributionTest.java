package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.google.gson.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Exact omitted topology, scoped merged-bus ownership and independent coupled controls. */
public class CompositeOmittedDistributionTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "CompositeOmittedDistributionTest");
    private record Reference(double h, String dyr, double[] constants, List<Map<String, Double>> rows) { }
    private static final Map<String, Reference> REFERENCES = new TreeMap<>();
    private static String sha(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    private static List<Map<String, Double>> rows(byte[] raw) {
        String[] lines = new String(raw, StandardCharsets.UTF_8).strip().split("\\R"), keys = lines[0].split(","); var result = new ArrayList<Map<String, Double>>();
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(","); assertEquals(keys.length, values.length); var r = new LinkedHashMap<String, Double>();
            for (int j = 0; j < keys.length; j++) { double v = Double.parseDouble(values[j]); assertTrue(Double.isFinite(v)); r.put(keys[j], v); } result.add(r);
        }
        return result;
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Path archive = Path.of(CONFIG.string("reference.archive", "testData/private/verification-artifacts/cmldblu1-omitted-elements-20261101/captures-v2.zip"));
        Assumptions.assumeTrue(Files.isRegularFile(archive), "Retain independent omitted-element controls");
        assertEquals("6fbcd9258323e6bb74956868abb8b6062158ecc88b4238106cac4bdb72cf699a", sha(Files.readAllBytes(archive)));
        try (var z = new ZipFile(archive.toFile())) {
            var m = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : m.getAsJsonObject("files").entrySet()) assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : m.getAsJsonObject("profiles").entrySet()) {
                var p = e.getValue().getAsJsonObject(); assertTrue(p.get("common_channels_equal").getAsBoolean()); List<Map<String, Double>> combined = null; String dyr = null; double[] constants = new double[132];
                for (var item : p.getAsJsonArray("parts")) {
                    var part = item.getAsJsonObject(); byte[] raw = z.getInputStream(z.getEntry(part.get("entry").getAsString())).readAllBytes();
                    assertArrayEquals(raw, z.getInputStream(z.getEntry(part.get("repeat_entry").getAsString())).readAllBytes()); var parsed = rows(raw);
                    if (combined == null) combined = parsed;
                    else for (int i = 0; i < parsed.size(); i++) for (var field : parsed.get(i).entrySet()) { var previous = combined.get(i).putIfAbsent(field.getKey(), field.getValue()); if (previous != null) assertEquals(previous, field.getValue()); }
                    String input = new String(z.getInputStream(z.getEntry(part.get("dyr").getAsString())).readAllBytes(), StandardCharsets.UTF_8); if (dyr != null) assertEquals(dyr, input); dyr = input;
                    for (int i = 0; i < constants.length; i++) constants[i] = part.getAsJsonArray("constants").get(i).getAsDouble();
                }
                assertNotNull(combined); assertEquals(p.get("source_rows").getAsInt(), combined.size()); assertEquals(p.get("source_channels").getAsInt(), combined.getFirst().size());
                assertEquals(2, combined.stream().filter(r -> r.get("time_s") < 0).count()); double previous = Double.NEGATIVE_INFINITY; int duplicates = 0;
                for (var r : combined) { double t = r.get("time_s"); assertTrue(t >= previous); if (t == previous) duplicates++; previous = t; } assertEquals(2, duplicates);
                REFERENCES.put(e.getKey(), new Reference(p.get("step_sec").getAsDouble(), dyr, constants, combined));
            }
        }
        assertEquals(14, REFERENCES.size());
    }
    @AfterAll static void clear() { REFERENCES.clear(); }
    static Stream<Arguments> methods() { return REFERENCES.keySet().stream().flatMap(n -> Stream.of(false, true).map(s -> Arguments.of(n, s))); }
    private record Error(double maximum, double rmse, Double rangeNormalizedRmse) { }
    private static Error error(List<Map<String, Double>> source, List<Cmldblu1SharedRecording.Frame> actual, String sourceKey, String observed) {
        double max = 0, sum = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < source.size(); i++) { double expected = source.get(i).get(sourceKey), d = actual.get(i).values().get(observed) - expected; assertTrue(Double.isFinite(d)); max = Math.max(max, Math.abs(d)); sum += d * d; lo = Math.min(lo, expected); hi = Math.max(hi, expected); }
        double rmse = Math.sqrt(sum / source.size()); return new Error(max, rmse, hi > lo ? rmse / (hi - lo) : null);
    }
    @ParameterizedTest @MethodSource("methods")
    void omittedElementsUseExactSharedNodesAndRetainIndependentTrajectories(String name, boolean shared) throws Exception {
        var ref = REFERENCES.get(name); Path out = Files.createTempDirectory(Path.of("target"), "composite-omitted-coupled-"); Path input = out.resolve("dynamic.dyr"); Files.writeString(input, ref.dyr());
        var r = Cmldblu1SharedRecording.simulateStaticControl(input, shared, ref.h()); var model = r.model; var parent = model.getDStabBus(); var net = r.algorithm.getNetwork();
        boolean omitXf = ref.constants()[5] == 0, omitFdr = ref.constants()[3] == 0;
        assertEquals(omitXf, model.getLowVoltBus() == parent); assertEquals(omitFdr, model.getLoadBus() == model.getLowVoltBus());
        assertEquals(3 + (omitXf ? 0 : 1) + (omitFdr ? 0 : 1), net.getNoBus()); assertEquals(2 + (omitXf ? 0 : 1) + (omitFdr ? 0 : 1), net.getBranchList().size());
        for (var branch : net.getBranchList()) { assertTrue(branch.getZ().abs() > 0); assertNotSame(branch.getFromBus(), branch.getToBus()); }
        var source = ref.rows().stream().filter(row -> row.get("time_s") >= 0).toList(); assertEquals(source.size(), r.frames.size());
        double[] clock = new double[(int) Math.round(.5 / ref.h()) + 1]; float t = 0; for (int i = 1; i < clock.length; i++) { t += (float) ref.h(); clock[i] = t; }
        for (int i = 0; i < source.size(); i++) assertEquals(clock[(int) Math.round(r.frames.get(i).time() / ref.h())], source.get(i).get("time_s"), 6e-13);
        assertEquals(source.getFirst().get("VAR29"), r.initial.values().get("Load.V"), 2.2e-7); assertEquals(source.getFirst().get("VAR30"), r.initial.values().get("Low.V"), 2.2e-7);
        var errors = new LinkedHashMap<String, Error>(); boolean mixed = name.startsWith("both-mixed"); double scale = ref.h() / .0005;
        for (String key : List.of("V1", "V2", "V3", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) {
            var e = error(source, r.frames, key, key); errors.put(key, e);
            double peak = mixed ? key.equals("V1") ? .00010 * scale + 2e-6 : key.equals("V2") ? .00008 * scale + 2e-6 : key.equals("V3") ? .000014 * scale + 5e-7 : key.equals("P_PU") ? .00018 * scale + 2e-6 : key.equals("Q_PU") ? .00065 * scale + 2e-6 : key.equals("ANGLE_DEG") ? .0003 * scale + 2e-5 : (shared ? 4e-7 : 1.4e-6) * scale + 2e-8
                    : shared ? key.equals("V1") ? 3.5e-6 : key.equals("V2") ? 1.6e-6 : key.equals("V3") ? 3e-7 : key.equals("P_PU") ? 5e-6 : key.equals("Q_PU") ? 2e-5 : key.equals("ANGLE_DEG") ? 6e-5 : 2e-8
                    : key.equals("ANGLE_DEG") ? 6e-5 : scale * (key.equals("V1") ? 9e-5 : key.equals("V2") ? 3.5e-5 : key.equals("V3") ? 6e-6 : key.equals("P_PU") ? 1e-4 : key.equals("Q_PU") ? 6e-4 : 1.7e-6);
            assertTrue(e.maximum() <= peak, key + ": " + e); if (key.startsWith("V")) assertTrue(e.rangeNormalizedRmse() != null && e.rangeNormalizedRmse() < 2e-4, key + ": " + e);
            if (key.equals("ANGLE_DEG")) assertTrue(e.rmse() < (mixed ? .00015 * scale + 2e-5 : 3e-5), e.toString());
        }
        for (String[] mapping : new String[][]{{"VAR29", "Load.V"}, {"VAR30", "Low.V"}, {"VAR25", "STATIC.P"}, {"VAR26", "STATIC.Q"}}) if (r.initial.values().containsKey(mapping[1])) {
            var e = error(source, r.frames, mapping[0], mapping[1]); errors.put(mapping[1], e);
            double peak = mapping[1].endsWith(".V") ? mixed ? .00008 * scale + 2e-6 : shared ? 1.8e-6 : .000035 * scale + 2e-6 : shared ? mapping[1].endsWith("P") ? 1e-5 : 5e-6 : mapping[1].endsWith("P") ? 3e-5 : 1.2e-5;
            assertTrue(e.maximum() <= peak, mapping[1] + ": " + e);
        }
        int index = 0; for (var motor : new org.interpss.dstab.dynLoad.InductionMotor[]{model.getInductionMotorA(), model.getInductionMotorB(), model.getInductionMotorC()}) {
            if (motor != null) { assertEquals(source.getFirst().get("VAR" + (47 + index * 2)), motor.getInitLoadPQ().getReal(), 2e-7); assertEquals(source.getFirst().get("VAR" + (48 + index * 2)), motor.getInitLoadPQ().getImaginary(), 2e-7); } index++;
        }
        if (model.get1PhaseACMotor() != null) assertEquals(source.getFirst().get("VAR53"), model.get1PhaseACMotor().getInitLoadPQ().getReal(), 2e-7);
        r.write(out.resolve("actual.csv.gz")); try (var w = BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"), 100000)) { new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("profile", name, "method", shared ? "staggered" : "modified-euler", "errors", errors, "scope", "omitted-element topology and boundaries; complete internal-coordinate qualification remains open"), w); }
    }
    @Test void absentTransformerIgnoresItsFixedAndControlledTapSettings() {
        for (double h : new double[]{.0005, .00025}) {
            String label = h == .0005 ? "0.0005" : "0.00025";
            var ordinary = REFERENCES.get("xf-static-h" + label).rows(); var ignored = REFERENCES.get("xf-ignored-controls-h" + label).rows(); assertEquals(ordinary.size(), ignored.size());
            for (int i = 0; i < ordinary.size(); i++) for (String key : List.of("time_s", "V1", "V2", "V3", "VAR29", "VAR30", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) assertEquals(ordinary.get(i).get(key), ignored.get(i).get(key));
            var noFeeder = REFERENCES.get("feeder-static-h" + label).rows(); var resistance = REFERENCES.get("resistive-feeder-h" + label).rows();
            for (int i = 0; i < noFeeder.size(); i++) for (String key : List.of("time_s", "V1", "V2", "V3", "VAR29", "VAR30", "VAR25", "VAR26", "P_PU", "Q_PU", "ANGLE_DEG", "SPEED")) assertEquals(noFeeder.get(i).get(key), resistance.get(i).get(key));
        }
    }
    @Test void compressorAllocationRejectsInvalidOrConflictingOwnersBeforeInitialization() throws Exception {
        var m = new LD1PACImpl(); assertThrows(NullPointerException.class, () -> m.setInitialLoadAllocation(null));
        for (Complex invalid : new Complex[]{Complex.NaN, Complex.INF, Complex.ZERO, new Complex(-.1, 0)}) assertThrows(IllegalArgumentException.class, () -> m.setInitialLoadAllocation(invalid));
        assertNull(m.getInitialLoadAllocation()); var assigned = new Complex(.1, .03); m.setInitialLoadAllocation(assigned); assertSame(assigned, m.getInitialLoadAllocation());
        assertThrows(IllegalStateException.class, () -> m.setParentLoad(com.interpss.core.CoreObjectFactory.createAclfLoad("other")));
        var net = com.interpss.dstab.DStabObjectFactory.createDStabilityNetwork(); var bus = (com.interpss.dstab.DStabBus) com.interpss.dstab.DStabObjectFactory.createDStabBus("B", net).orElseThrow();
        var source = com.interpss.dstab.DStabObjectFactory.createDStabLoad("source"); bus.getContributeLoadList().add(source); var bound = new LD1PACImpl(); bound.setDStabBus(bus); bound.setParentLoad(source);
        assertThrows(IllegalStateException.class, () -> bound.setInitialLoadAllocation(assigned)); assertNull(bound.getInitialLoadAllocation());
    }
    @ParameterizedTest @CsvSource({"false,false,false", "true,false,false", "false,true,false", "true,true,false",
            "false,false,true", "true,false,true", "false,true,true", "true,true,true"})
    void mergedBusPreservesUnownedLoadShuntsAndColocatedGeneration(boolean shared, boolean colocated, boolean allStatic) throws Exception {
        var ref = REFERENCES.get((allStatic ? "both-static" : "both-compressor") + "-h0.0005"); Path dir = Files.createTempDirectory(Path.of("target"), "composite-omitted-ownership-"); Files.writeString(dir.resolve("dynamic.dyr"), ref.dyr());
        var c = new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/HspModels/load-network.raw", dir.resolve("dynamic.dyr").toString()); var net = c.getDStabilityNet(); var parent = net.getDStabBus("Bus2");
        var outside = com.interpss.dstab.DStabObjectFactory.createDStabLoad("outside"); outside.setCode(com.interpss.core.aclf.AclfLoadCode.CONST_P); outside.setLoadCP(new Complex(.2, .04)); outside.setStatus(true); parent.getContributeLoadList().add(outside); assertTrue(parent.initContributeLoad(false)); parent.setShuntY(new Complex(.003, .02));
        if (colocated) {
            var b = new org.interpss.fadapter.builder.AclfNetworkBuilder(net); b.addContributeGen("Bus2", "S", true, .05, 0, 100, 1, 1, -1, 1, 0, new Complex(0, .2), null, 0, null, 0, 0); b.setPVBus("Bus2", .05, 1, 1, -1, false);
            new org.interpss.fadapter.builder.DStabNetworkBuilder(net).addGencls("Bus2", "S", 100, 230, 5, 0, 0, .2);
        }
        var a = c.getDynSimuAlgorithm(); a.getAclfAlgorithm().setTolerance(1e-10); assertTrue(a.getAclfAlgorithm().loadflow()); a.setSimuStepSec(.0005); a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        net.setStaticLoadIncludedInYMatrix(false); net.setStaticLoadZPartInYMatrix(true);
        net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_I); net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z); a.setSolver(shared ? new StaggeredEulerSolver(a) : new DefaultDynamicSimulationSolver(a)); assertTrue(a.initialization());
        var model = assertInstanceOf(Cmldblu1Model.class, parent.getInfoOnlyDynModel()); assertSame(parent, model.getLoadBus()); assertSame(outside, parent.getContributeLoad("outside")); assertNull(parent.getContributeLoad("1"));
        assertEquals(.6, parent.getLoadP(), 1e-12); assertEquals(.2, parent.calNetLoadResults().getReal(), 1e-12); assertEquals(.04, parent.calNetLoadResults().getImaginary(), 1e-12); assertEquals(.003, parent.getShuntY().getReal(), 0);
        assertEquals(.2, parent.getInitConstIStaticLoad().getReal(), 1e-12); assertEquals(.04, parent.getInitConstZStaticLoad().getImaginary(), 1e-12);
        double ownedP = 0; for (var load : parent.getDynLoadModelList()) ownedP += load.getInitLoadPQ().getReal(); assertEquals(.4, ownedP, 1e-12);
        if (!allStatic) {
            var motor = assertInstanceOf(LD1PACImpl.class, model.get1PhaseACMotor()); assertEquals(.02, motor.getInitialLoadAllocation().getReal(), 1e-12); var assigned = motor.getInitialLoadAllocation(); motor.setInitialLoadAllocation(assigned); assertThrows(IllegalStateException.class, () -> motor.setInitialLoadAllocation(new Complex(.03, .01))); assertEquals(assigned, motor.getInitialLoadAllocation());
        } else { assertNull(model.get1PhaseACMotor()); assertEquals(0, model.getNamedStates().get("STATIC.LowVoltageG")); }
        assertEquals(0, net.getCustomBusCurrInjHashtable().getOrDefault(parent.getId(), Complex.ZERO).abs(), 1e-9, "Scope accounting must not rely on a fixed compensating current");
        var initial = parent.getVoltage(); for (int i = 0; i < 20; i++) assertTrue(a.solveDEqnStep(true)); assertEquals(0, parent.getVoltage().subtract(initial).abs(), 1e-9);
        if (!allStatic) assertEquals(.02, model.get1PhaseACMotor().getInitLoadPQ().getReal(), 1e-12); assertEquals(.4, ownedP, 1e-12);
    }
}
