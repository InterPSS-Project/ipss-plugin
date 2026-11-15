package com.interpss.dstab.controller.gov.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.gov.hyg3.PsseHyg3Governor;

/** Both solver lifecycles retain complete synthetic fault boundaries and governor-state diagnostics. */
public class Hyg3SharedSmibTest {
    private static final Path CASE = Path.of("testData/adpter/psse/v33/HspModels");
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json",
            "HspModelReferenceConformanceTest");
    private static final Path REFERENCE = Path.of(CONFIG.string("hsp.referenceRoot",
            "testData/private/verification-artifacts/2026-10-30-hsp-usage/references"));
    private static final String[] PROFILES = {"hyg3u1-0", "hyg3u1-1", "hyg3u1-deadband",
            "hyg3u1-deadband-zero", "hyg3u1-asymmetric", "hyg3u1-partial"};
    private static final String[] NAMES = {"Input filter", "Integral", "Derivative lag", "Valve", "Gate",
            "Electrical power", "Water flow", "Double derivative 1", "Double derivative 2"};
    private static final double H = .0005, TOLERANCE = 1e-6;
    private static JsonNode manifest;
    record Frame(double time, String kind, Map<String, Double> values) { }
    record ReferenceEvent(double time, double increment) { }

    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(REFERENCE.resolve("manifest.json")),
                "Retained independent small-network captures are required");
        byte[] bytes = Files.readAllBytes(REFERENCE.resolve("manifest.json"));
        assertEquals("d064ddf3cd340ecf365dadfffacc5125e5ae6b31fd47f06ec50094cce4973fd9", sha(bytes));
        manifest = new ObjectMapper().readTree(bytes);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void coupledFaultRetainsEveryIndependentSampleAndBoundaryGate(boolean staggered) throws Exception {
        Path out = Files.createTempDirectory(Path.of("target"), staggered ? "hyg3-shared-smib-" : "hyg3-euler-smib-");
        System.out.println("HYG3 coupled diagnostics " + out.getFileName());
        var diagnostics = new LinkedHashMap<String, Object>();
        for (String profile : PROFILES) {
            var metadata = manifest.path("profiles").path(profile);
            assertEquals(metadata.path("network_sha256").asText(), sha(Files.readAllBytes(CASE.resolve("network.raw"))));
            assertEquals(metadata.path("dynamic_sha256").asText(), sha(Files.readAllBytes(CASE.resolve(profile + ".dyr"))));
            byte[] archive = Files.readAllBytes(REFERENCE.resolve(metadata.path("file").asText()));
            assertEquals(metadata.path("archive_sha256").asText(), sha(archive));
            byte[] bytes;
            try (var stream = new GZIPInputStream(new ByteArrayInputStream(archive))) { bytes = stream.readAllBytes(); }
            assertEquals(metadata.path("raw_sha256").asText(), sha(bytes));
            var expected = csv(bytes);
            var run = simulate(profile, staggered, false);
            var maximum = new LinkedHashMap<String, Double>(); var maximumTime = new LinkedHashMap<String, Double>();
            var squares = new LinkedHashMap<String, Double>(); var low = new LinkedHashMap<String, Double>();
            var high = new LinkedHashMap<String, Double>();
            for (String key : expected.getFirst().keySet()) if (run.initial.values.containsKey(key)) {
                maximum.put(key, 0.); maximumTime.put(key, 0.); squares.put(key, 0.);
                low.put(key, Double.POSITIVE_INFINITY); high.put(key, Double.NEGATIVE_INFINITY);
            }
            int frame = 0, startup = 0;
            try (var stream = BenchmarkOutputGuard.newOutputStream(out.resolve(profile + ".csv.gz"),
                    BenchmarkOutputGuard.estimateCsvBytes(expected.size(), run.initial.values.size()+expected.getFirst().size()));
                    var gzip = new GZIPOutputStream(stream);
                    var writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {
                writer.write("reference_time_s,actual_time_s,sample_kind," + String.join(",", run.initial.values.keySet()));
                for (String key : expected.getFirst().keySet()) writer.write(",REFERENCE."+key);
                writer.write("\n");
                for (var row : expected) {
                    double time = row.get("time_s");
                    assertEquals(expected.getFirst().get("VAR2"), row.get("VAR2"), 0.,
                            "Bypassed backlash retains its initialized reference input-history slot");
                    Frame actual = time < 0. ? run.initial : run.frames.get(frame++);
                    if (time < 0.) startup++; else assertEquals(time, actual.time, 5e-6, "Independent accumulated clock");
                    writer.write(time + "," + actual.time + "," + (time < 0. ? "STARTUP_HELD_INITIAL" : actual.kind));
                    for (var value : actual.values.entrySet()) {
                        assertTrue(Double.isFinite(value.getValue()), value.getKey()); writer.write("," + value.getValue());
                    }
                    for (double value : row.values()) writer.write(","+value);
                    writer.write("\n");
                    for (String key : maximum.keySet()) {
                        double value = row.get(key), error = actual.values.get(key) - value;
                        if (Math.abs(error) > maximum.get(key)) { maximum.put(key, Math.abs(error)); maximumTime.put(key, time); }
                        squares.put(key, squares.get(key) + error*error);
                        low.put(key, Math.min(low.get(key), value)); high.put(key, Math.max(high.get(key), value));
                    }
                }
            }
            assertEquals(expected.size(), startup + run.frames.size(), "No grid/event samples may be discarded");
            var nrmse = new LinkedHashMap<String, Double>();
            for (String bus : List.of("V1", "V2")) {
                double range = high.get(bus)-low.get(bus), rmse = Math.sqrt(squares.get(bus)/expected.size());
                nrmse.put(bus, range == 0. ? rmse : rmse/range);
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("maximum_absolute_error", maximum); result.put("time_of_maximum", maximumTime);
            result.put("voltage_nrmse", nrmse); result.put("rotor_rmse_deg", Math.sqrt(squares.get("ANGLE_DEG")/expected.size()));
            result.put("state_policy", "Initialization accepted; complete transient state-coordinate parity remains diagnostic");
            result.put("unmapped_reference_channels", expected.getFirst().keySet().stream()
                    .filter(key -> !key.equals("time_s") && !run.initial.values.containsKey(key)).toList());
            diagnostics.put(profile, result);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out.resolve("diagnostic.json").toFile(), diagnostics);
            assertEquals(List.of(), result.get("unmapped_reference_channels"), "Every published diagnostic has an explicit observation");
            for (double normalized : nrmse.values()) assertTrue(normalized < .001, "Voltage range-normalized RMSE");
            assertTrue(Math.sqrt(squares.get("ANGLE_DEG")/expected.size()) < .01, "Rotor-angle RMSE in degrees");
            for (int i = 0; i < 9; i++) assertEquals(expected.getFirst().get("STATE"+i), run.initial.values.get("STATE"+i), 1e-6);
            Map<String, Double> bounds = Map.of("V1", .00021, "V2", .00008, "P_PU", .00014,
                    "Q_PU", .00185, "ANGLE_DEG", .00025, "PMECH", .00007, "SPEED", 1.6e-6);
            for (var bound : bounds.entrySet()) assertTrue(maximum.get(bound.getKey()) <= bound.getValue(),
                    profile+" staggered="+staggered+" "+bound.getKey()+" error="+maximum.get(bound.getKey())+" at="+maximumTime.get(bound.getKey()));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void noEventEquilibriumIsStationaryUnderBothMethods(boolean staggered) throws Exception {
        for (String profile : PROFILES) {
            var run = simulate(profile, staggered, true);
            var initial = run.initial.values;
            for (var frame : run.frames) {
                assertEquals(initial.get("V1"), frame.values.get("V1"), 1e-5);
                assertEquals(initial.get("ANGLE_DEG"), frame.values.get("ANGLE_DEG"), 1e-5);
                for (int i = 0; i < 9; i++) assertEquals(initial.get("STATE"+i), frame.values.get("STATE"+i), 1e-8);
            }
        }
    }

    private static Recording simulate(String profile, boolean staggered, boolean flat) throws Exception {
        return simulate(CASE.resolve("network.raw"), CASE.resolve(profile+".dyr"), staggered, flat, H, .5);
    }

    static Recording simulate(Path raw, Path dynamic, boolean staggered, boolean flat, double step, double duration) throws Exception {
        return simulate(raw,dynamic,staggered,flat,step,duration,List.of());
    }

    static Recording simulate(Path raw, Path dynamic, boolean staggered, boolean flat, double step, double duration,
            List<ReferenceEvent> events) throws Exception {
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(raw.toString(), dynamic.toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        var algorithm = context.getDynSimuAlgorithm(); var net = context.getDStabilityNet();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER); algorithm.setSimuStepSec(step); algorithm.setTotalSimuTimeSec(duration);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if (!flat && events.isEmpty()) net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net, SimpleFaultCode.GROUND_3P,
                new Complex(0., .2), null, .05, .05), "fault");
        var recording = new Recording(algorithm, flat, events);
        algorithm.setSolver(staggered ? new SharedRecorder(algorithm, recording) : new OrdinaryRecorder(algorithm, recording));
        assertTrue(algorithm.initialization());
        recording.initial = recording.capture(0., "INITIAL"); recording.enabled = true;
        while (algorithm.getSimuTime() < duration-step/2.) assertTrue(algorithm.solveDEqnStep(true));
        recording.frames.add(recording.capture(algorithm.getSimuTime(), "FINAL"));
        var governor = (PsseHyg3Governor) net.getMachine("Bus1-mach1").getGovernor();
        if (staggered) assertTrue(governor.getStaggeredEvaluationCount() > duration/step, "Actual shared-state dispatch");
        else assertEquals(0, governor.getStaggeredEvaluationCount());
        assertEquals(TOLERANCE, ((com.interpss.dstab.algo.dynamic.DynamicSimulationSolverBase)algorithm.getSolver())
                .getLastNetworkSolutionDiagnostics().voltageTolerance(), 0.);
        return recording;
    }

    static final class Recording {
        final DynamicSimuAlgorithm algorithm; final boolean flat; final List<Frame> frames = new ArrayList<>();
        final List<ReferenceEvent> events; int nextEvent;
        boolean enabled; Frame initial;
        final double[] published = new double[9];
        Recording(DynamicSimuAlgorithm algorithm, boolean flat, List<ReferenceEvent> events) {
            this.algorithm = algorithm; this.flat = flat; this.events = List.copyOf(events);
        }
        boolean before(double time) {
            if (enabled && nextEvent < events.size()) {
                var event = events.get(nextEvent);
                if (Math.abs(time-event.time()) < 1e-9) {
                    frames.add(capture(time,"REFERENCE_PRE"));
                    var governor = (PsseHyg3Governor) algorithm.getNetwork().getMachine("Bus1-mach1").getGovernor();
                    governor.setRefPoint(governor.getRefPoint()+event.increment()); nextEvent++; return true;
                }
                if (time > event.time()+1e-9) throw new IllegalStateException("Reference boundary was not on the physical grid");
            }
            if (enabled && !flat && events.isEmpty() && (Math.abs(time-.05)<1e-9 || Math.abs(time-.1)<1e-9))
                frames.add(capture(time, "INTERVAL_PRE"));
            return false;
        }
        Frame capture(double time, String kind) {
            var net = algorithm.getNetwork(); var machine = net.getMachine("Bus1-mach1");
            var governor = (PsseHyg3Governor) machine.getGovernor(); var voltage = machine.getDStabBus().getVoltage();
            var power = voltage.multiply(machine.getIgen().subtract(voltage.multiply(machine.getYgen())).conjugate());
            var values = new LinkedHashMap<String, Double>();
            values.put("V1", voltage.abs()); values.put("V2", net.getDStabBus("Bus2").getVoltageMag());
            values.put("A1", Math.toDegrees(voltage.getArgument()));
            values.put("A2", Math.toDegrees(net.getDStabBus("Bus2").getVoltage().getArgument()));
            values.put("P_PU", power.getReal()); values.put("Q_PU", power.getImaginary());
            values.put("ANGLE_DEG", Math.toDegrees(machine.getAngle())); values.put("SPEED", machine.getSpeed()-1.);
            values.put("PMECH", machine.getPm()); values.put("STABILIZER", 0.);
            values.put("REFERENCE_INPUT",governor.getRefPoint());
            var states = governor.getIntegrationStates();
            for (int i = 0; i < 9; i++) values.put("STATE"+i, states.get(NAMES[i]));
            governor.readPublishedState(published, 0);
            for (int i = 0; i < 9; i++) values.put("PUBLISHED"+i, published[i]);
            values.put("VAR0", governor.getControlValveCommand());
            values.put("VAR1", governor.applyFrequencyDeadband(machine.getSpeed()-1.));
            values.put("BACKLASH_INPUT", governor.getGatePosition()); values.put("VAR2",governor.getBacklashInputHistory());
            values.put("VAR3", governor.getGateOutput());
            values.put("VAR4", governor.getTurbineHead());
            governor.getIntegrationHistories().forEach((key, value) -> values.put("HISTORY."+key, value));
            return new Frame(time, kind, values);
        }
    }
    private static final class OrdinaryRecorder extends DefaultDynamicSimulationSolver {
        final Recording recording;
        OrdinaryRecorder(DynamicSimuAlgorithm algorithm, Recording recording) {
            super(algorithm); this.recording = recording; converge_tol = TOLERANCE;
        }
        @Override public void beforeStep(double time) throws DStabSimuException { recording.before(time); super.beforeStep(time); }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            if (solved && recording.enabled && "INITIAL".equals(networkSolutionStage))
                recording.frames.add(recording.capture(dstabAlgo.getSimuTime(), "GRID"));
            return solved;
        }
    }
    private static final class SharedRecorder extends StaggeredEulerSolver {
        final Recording recording;
        SharedRecorder(DynamicSimuAlgorithm algorithm, Recording recording) {
            super(algorithm); this.recording = recording; setReuseNetworkEndpoint(false); setNetworkSolutionTolerance(TOLERANCE);
        }
        @Override public void beforeStep(double time) throws DStabSimuException {
            if (recording.before(time)) notifyDiscontinuity(); super.beforeStep(time);
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            if (solved && recording.enabled && "INITIAL".equals(networkSolutionStage))
                recording.frames.add(recording.capture(dstabAlgo.getSimuTime(), "GRID"));
            return solved;
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static List<Map<String, Double>> csv(byte[] bytes) {
        String[] lines = new String(bytes, StandardCharsets.UTF_8).strip().split("\\R"), names = lines[0].split(",");
        var rows = new ArrayList<Map<String, Double>>();
        for (int i = 1; i < lines.length; i++) {
            String[] fields = lines[i].split(","); assertEquals(names.length, fields.length);
            var row = new LinkedHashMap<String, Double>();
            for (int k = 0; k < names.length; k++) { double value = Double.parseDouble(fields[k]); assertTrue(Double.isFinite(value)); row.put(names[k], value); }
            rows.add(row);
        }
        return rows;
    }
}
