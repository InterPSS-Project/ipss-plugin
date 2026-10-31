package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.dstab.svc.Svsmo3t2MssController;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Independent MSS signal replay; electrical trajectory acceptance is separate. */
public class Svsmo3t2MssReplayTest extends CorePluginTestSetup {
    @Test void singleOperationCoupledProfilesPassEveryVoltageAngleAndStateGate() throws Exception { coupled(true); }

    @Test @Tag("extended") void coupledSwitchingMatrixDiagnosticsRetainEveryReferenceRow() throws Exception { coupled(false); }

    private static Path referenceRoot() throws Exception {
        String configured = System.getProperty("svsmo3t2.mss");
        if (configured != null) return Path.of(configured);
        Path root = Files.createTempDirectory(Path.of("target"), "svsmo3t2-mss-reference-").toAbsolutePath().normalize();
        String[][] archives = {{"mss.zip", "f95f32b00bc77a461a2aee39bda99ed63d6dc1fc39ecda869ec5ef126b988c67"},
                {"mss-reactors.zip", "99bb319112c66e7be8537971d411d000c82a1cd1dcad28b3b406047a5cb89a15"}};
        for (var archive : archives) extract(root, archive[0], archive[1]);
        return root;
    }

    private static void extract(Path root, String archive, String expectedHash) throws Exception {
        byte[] bytes;
        try (var stream = Svsmo3t2MssReplayTest.class.getResourceAsStream("/dynamic-models/svsmo3t2/" + archive)) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        assertEquals(expectedHash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path path = root.resolve(entry.getName()).normalize();
                assertTrue(path.startsWith(root));
                Files.createDirectories(path.getParent());
                try (var output = org.interpss.core.dstab.reference.BenchmarkOutputGuard.newOutputStream(path, 4L * 1024 * 1024)) {
                    zip.transferTo(output);
                }
            }
        }
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode entries(ObjectMapper mapper, Path matrix) throws Exception {
        var entries = (com.fasterxml.jackson.databind.node.ArrayNode) mapper.readTree(matrix.resolve("matrix.json").toFile());
        Path supplement = matrix.resolve("matrix-reactors.json");
        if (Files.exists(supplement)) entries.addAll((com.fasterxml.jackson.databind.node.ArrayNode) mapper.readTree(supplement.toFile()));
        return entries;
    }

    private static void coupled(boolean acceptedOnly) throws Exception {
        Path matrix = referenceRoot();
        var mapper = new ObjectMapper();
        var results = new LinkedHashMap<String, Object>();
        Path outputRoot = Files.createTempDirectory(Path.of("target"), "svsmo3t2-mss-coupled-");
        for (var entry : entries(mapper, matrix)) {
            if (!entry.get("repeat").asText().equals("a")) continue;
            Path root = matrix.resolve(entry.get("path").asText());
            String profile = entry.get("profile").asText();
            if (acceptedOnly && java.util.Set.of("capacitor-discharge", "capacitor-pair", "cooling-slot-skip", "close-reactor-pair", "open-reactor-pair").contains(profile)) continue;
            var manifest = mapper.readTree(root.resolve("manifest.json").toFile());
            var ctx = new PSSEMultiFileLoader().loadDStab(root.resolve("smib.raw").toString(), root.resolve("smib.dyr").toString());
            var net = ctx.getDStabilityNet();
            var algo = ctx.getDynSimuAlgorithm();
            net.setStaticLoadIncludedInYMatrix(true);
            net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
            net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
            algo.getAclfAlgorithm().setTolerance(1e-10);
            algo.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
            algo.getAclfAlgorithm().getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1e-9);
            assertTrue(algo.getAclfAlgorithm().loadflow());
            double step = manifest.get("step_s").asDouble();
            algo.setSimuStepSec(step);
            algo.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
            var recorder = new StartRecorder(algo);
            algo.setSolver(recorder);
            algo.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algo.initialization());
            var model = (Svsmo3t2Model) net.getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                    .filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
            double initialSchedule = model.getScheduledVoltage();
            String[] names = {"V_1", "V_2", "V_3", "MACHINE_ANGLE", "VAR_0", "STATE_0", "STATE_1", "STATE_2", "STATE_3", "STATE_4", "STATE_5"};
            var frames = new ArrayList<double[]>();
            var statuses = new ArrayList<boolean[]>();
            for (int tick = 0; tick <= Math.round(manifest.get("stop_s").asDouble() / step); tick++) {
                double t = tick * step;
                frames.add(frame(algo));
                boolean[] status = new boolean[8];
                for (int i = 0; i < 8; i++) if (model.getData().mssDevices().get(i).busNumber() != 0) status[i] = model.getMssController().isInService(i);
                statuses.add(status);
                if (tick < Math.round(manifest.get("stop_s").asDouble() / step)) {
                    boolean referenceEvent = false;
                    if (tick == Math.round(.05 / step)) {
                        model.setScheduledVoltage(initialSchedule + manifest.get("reference_change").asDouble());
                        referenceEvent = true;
                    }
                    for (var point : manifest.get("reference_points"))
                        if (tick == Math.round(point.get(0).asDouble() / step)) {
                            model.setScheduledVoltage(initialSchedule + point.get(1).asDouble());
                            referenceEvent = true;
                        }
                    if (referenceEvent) assertTrue(model.nextStep(0, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));
                    assertTrue(algo.solveDEqnStep(true));
                }
            }
            var lines = Files.readAllLines(root.resolve("reference.csv"));
            var header = Arrays.asList(lines.getFirst().split(","));
            double[] max = new double[names.length], squares = new double[names.length], at = new double[names.length];
            double[] low = new double[3], high = new double[3];
            Arrays.fill(low, Double.POSITIVE_INFINITY); Arrays.fill(high, Double.NEGATIVE_INFINITY);
            double prefaultAngleMaximum = 0;
            int previousClock = Integer.MIN_VALUE;
            Path trace = outputRoot.resolve(profile + ".csv");
            try (var writer = org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(trace,
                    org.interpss.core.dstab.reference.BenchmarkOutputGuard.estimateCsvBytes(lines.size(), 2 * names.length + 1))) {
            writer.write("time_s,run_start");
            for (String name : names) writer.write(",actual_" + name + ",reference_" + name);
            writer.write('\n');
            for (String line : lines.subList(1, lines.size())) {
                var row = Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
                int tick = Math.max(0, (int) Math.round(row[0] / step));
                int clock = Float.floatToIntBits((float) row[0]);
                boolean repeated = clock == previousClock;
                previousClock = clock;
                // RUN-end and next RUN-start rows can differ at the same clock:
                // the latter follows the fresh algebraic network solution.
                var actual = row[0] >= 0 && repeated ? recorder.starts.getOrDefault(tick, frames.get(tick)) : frames.get(tick);
                writer.write(row[0] + "," + (repeated ? 1 : 0));
                for (int i = 0; i < names.length; i++) {
                    double reference = row[header.indexOf(names[i])];
                    double error = Math.abs(actual[i] - reference);
                    assertTrue(Double.isFinite(error), profile + " " + names[i]);
                    squares[i] += error * error;
                    if (error > max[i]) { max[i] = error; at[i] = row[0]; }
                    if (i < 3) { low[i] = Math.min(low[i], reference); high[i] = Math.max(high[i], reference); }
                    if (i == 3 && row[0] < .05) prefaultAngleMaximum = Math.max(prefaultAngleMaximum, error);
                    writer.write("," + actual[i] + "," + reference);
                }
                writer.write('\n');
            }
            }
            var metrics = new LinkedHashMap<String, Object>();
            for (int i = 0; i < names.length; i++) metrics.put(names[i], Map.of("max", max[i], "at_s", at[i], "rmse", Math.sqrt(squares[i] / (lines.size() - 1))));
            var statusLines = Files.readAllLines(root.resolve("mss-status.csv"));
            var statusHeader = Arrays.asList(statusLines.getFirst().split(","));
            int mismatches = 0;
            for (String line : statusLines.subList(1, statusLines.size())) {
                var row = line.split(",");
                int tick = Math.max(0, (int) Math.round(Double.parseDouble(row[0]) / step));
                for (int i = 0; i < 8; i++) {
                    var key = model.getData().mssDevices().get(i);
                    if (key.busNumber() != 0 && statuses.get(tick)[i] != row[statusHeader.indexOf(key.id())].equals("1")) mismatches++;
                }
            }
            if (acceptedOnly) {
                for (int i = 0; i < 3; i++) assertTrue(Math.sqrt(squares[i] / (lines.size() - 1)) / (high[i] - low[i]) < .001, profile + " " + names[i] + " range NRMSE");
                assertTrue(Math.sqrt(squares[3] / (lines.size() - 1)) < .01, profile + " angle RMSE degrees");
                assertTrue(prefaultAngleMaximum < .002, profile + " prefault angle maximum degrees");
                double[] maximum = {.00012, .00003, .009, 1e-12, 1e-12, .0005};
                double[] rms = {.000006, .000003, .00038, 1e-12, 1e-12, .00003};
                for (int i = 0; i < 6; i++) {
                    assertTrue(max[i + 5] < maximum[i], profile + " STATE_" + i + " maximum");
                    assertTrue(Math.sqrt(squares[i + 5] / (lines.size() - 1)) < rms[i], profile + " STATE_" + i + " RMSE");
                }
                assertEquals(0, mismatches, profile + " every physical bank status");
            }
            results.put(profile, Map.of("status", acceptedOnly ? "coupled-single-operation-accepted" : "coupled-diagnostic-not-accepted", "channels", metrics, "status_mismatches", mismatches));
            System.out.println("SVSMO3T2 MSS coupled " + profile + " " + results.get(profile));
        }
        assertEquals(acceptedOnly ? 10 : 15, results.size());
        Path output = outputRoot.resolve("diagnostic.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), results);
        System.out.println("SVSMO3T2 MSS coupled diagnostics " + output);
    }

    private static double[] frame(com.interpss.dstab.algo.DynamicSimuAlgorithm algo) {
        var net = algo.getNetwork();
        var model = (Svsmo3t2Model) net.getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                .filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
        var values = new double[11];
        for (int i = 0; i < 3; i++) values[i] = net.getBus("Bus" + (i + 1)).getVoltageMag();
        values[3] = Math.toDegrees(net.getMachine("Bus2-mach1").getAngle());
        values[4] = model.getCurrent();
        int i = 5;
        for (double state : model.getNamedStates().values()) values[i++] = state;
        return values;
    }

    private static final class StartRecorder extends org.interpss.dstab.algo.DefaultDynamicSimulationSolver {
        final Map<Integer, double[]> starts = new HashMap<>();
        StartRecorder(com.interpss.dstab.algo.DynamicSimuAlgorithm algo) { super(algo); }
        @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
            boolean solved = super.networkSolutionStep();
            if (solved && "INITIAL".equals(networkSolutionStage))
                starts.put((int) Math.round(dstabAlgo.getSimuTime() / dstabAlgo.getSimuStepSec()), frame(dstabAlgo));
            return solved;
        }
    }

    @Test void everyObservedPhysicalStatusFollowsItsOwnCapturedCurrent() throws Exception {
        Path matrix = referenceRoot();
        var mapper = new ObjectMapper();
        var results = new LinkedHashMap<String, Object>();
        long totalMismatches = 0;
        for (var entry : entries(mapper, matrix)) {
            Path root = matrix.resolve(entry.get("path").asText());
            var manifest = mapper.readTree(root.resolve("manifest.json").toFile());
            var files = manifest.get("files").fields();
            while (files.hasNext()) {
                var file = files.next();
                Path artifact = root.resolve(file.getKey()).toAbsolutePath().normalize();
                assertTrue(artifact.startsWith(root.toAbsolutePath().normalize()));
                assertEquals(file.getValue().asText(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))), file.getKey());
            }
            if (!entry.get("repeat").asText().equals("a")) continue;
            String profile = entry.get("profile").asText();
            var ctx = new PSSEMultiFileLoader().loadDStab(root.resolve("smib.raw").toString(), root.resolve("smib.dyr").toString());
            var net = ctx.getDStabilityNet();
            var model = (Svsmo3t2Model) net.getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                    .filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
            var c = new Svsmo3t2MssController(model.getData());
            var nominalClock = new Svsmo3t2MssController(model.getData());
            double[] b = new double[8];
            boolean[] initial = new boolean[8];
            var keys = model.getData().mssDevices();
            for (int i = 0; i < 8; i++) if (keys.get(i).busNumber() != 0) {
                var device = net.getDStabBus("Bus" + keys.get(i).busNumber()).getCompensator(keys.get(i).id());
                b[i] = device.getB(); initial[i] = device.isActive();
            }
            c.initialize(b, initial);
            nominalClock.initialize(b, initial);
            List<String> lines = Files.readAllLines(root.resolve("reference.csv"));
            var header = Arrays.asList(lines.getFirst().split(","));
            int currentColumn = header.indexOf("VAR_0");
            var undefined = new java.util.HashSet<String>();
            for (var name : manifest.get("canonical_excluded_channels")) undefined.add(name.asText());
            var rows = new HashMap<Integer, double[]>();
            for (String line : lines.subList(1, lines.size())) {
                double[] row = Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
                assertEquals(header.size(), row.length);
                for (int i = 0; i < row.length; i++) if (!undefined.contains(header.get(i))) assertTrue(Double.isFinite(row[i]), header.get(i));
                // Lookup only: the original rows and all call-boundary duplicates remain in the capture.
                rows.put(Float.floatToIntBits((float) row[0]), row);
            }
            List<String> observations = Files.readAllLines(root.resolve("mss-status.csv"));
            var statusHeader = Arrays.asList(observations.getFirst().split(","));
            var nativeActions = new ArrayList<String>();
            var actualActions = new ArrayList<String>();
            boolean[] previous = initial.clone();
            int mismatches = 0, samples = 0;
            double firstMismatch = -1;
            double timerMaximum = 0;
            int clockOnlyMismatches = 0;
            double accumulatedTime = 0, previousTime = -1;
            for (String line : observations.subList(1, observations.size())) {
                var values = line.split(",");
                double time = Double.parseDouble(values[0]);
                var row = rows.get(Float.floatToIntBits((float) time));
                assertNotNull(row, "Every observed endpoint requires its own captured current");
                if (time >= 0) {
                    int changed = c.advance(time, row[currentColumn]);
                    if (changed >= 0) actualActions.add(keys.get(changed).id() + "@" + time);
                    if (previousTime >= 0 && time > previousTime) accumulatedTime += manifest.get("step_s").asDouble();
                    nominalClock.advance(accumulatedTime, row[currentColumn]);
                    previousTime = time;
                }
                double rounding = 2 * Math.ulp((float) Math.max(1., Math.abs(time)));
                for (int direction = 0; direction < 2; direction++) {
                    double reference = row[header.indexOf("VAR_" + (11 + direction))];
                    double actual = direction == 0 ? c.upperDemandStart() : c.lowerDemandStart();
                    double expected = reference >= 99999 ? -1 : reference;
                    assertEquals(expected, actual, rounding, profile + " demand timer at " + time);
                    timerMaximum = Math.max(timerMaximum, Math.abs(expected - actual));
                }
                double breaker = row[header.indexOf("VAR_21")];
                if (c.pendingDirection() != 0) {
                    assertTrue(breaker < 99999, profile + " issued breaker remains pending");
                    assertEquals(breaker, c.breakerStart(), rounding, profile + " breaker timer at " + time);
                    timerMaximum = Math.max(timerMaximum, Math.abs(breaker - c.breakerStart()));
                } else assertTrue(breaker == 0 || breaker >= 99999, profile + " no issued breaker at " + time);
                for (int slot = 0; slot < 8; slot++) if (b[slot] != 0) {
                    boolean expected = values[statusHeader.indexOf(keys.get(slot).id())].equals("1");
                    if (expected != previous[slot]) nativeActions.add(keys.get(slot).id() + "@" + time);
                    previous[slot] = expected;
                    if (expected != nominalClock.isInService(slot)) clockOnlyMismatches++;
                    if (b[slot] > 0 && !expected) {
                        double reference = row[header.indexOf("VAR_" + (13 + slot))];
                        assertEquals(reference, c.openedAt(slot), rounding, profile + " capacitor discharge history at " + time);
                        timerMaximum = Math.max(timerMaximum, Math.abs(reference - c.openedAt(slot)));
                    }
                    if (expected != c.isInService(slot)) {
                        mismatches++;
                        if (firstMismatch < 0) firstMismatch = time;
                    }
                }
                samples++;
            }
            assertFalse(nativeActions.isEmpty(), profile + " must activate switching");
            var result = Map.of("status_mismatches", mismatches, "first_mismatch_s", firstMismatch,
                    "observations", samples, "native_actions", nativeActions, "actual_actions", actualActions,
                    "attempts", c.attemptCount(), "completions", c.completionCount(), "timer_maximum_s", timerMaximum,
                    "diagnostic_double_clock_status_mismatches", clockOnlyMismatches);
            results.put(profile, result);
            totalMismatches += mismatches;
            System.out.println("SVSMO3T2 MSS " + profile + " " + result);
        }
        assertEquals(15, results.size(), "The complete independently captured switching matrix is required");
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-mss-replay-").resolve("comparison.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), results);
        System.out.println("SVSMO3T2 MSS comparison " + output);
        assertEquals(0, totalMismatches, "Physical statuses must match at every observed endpoint");
    }
}
