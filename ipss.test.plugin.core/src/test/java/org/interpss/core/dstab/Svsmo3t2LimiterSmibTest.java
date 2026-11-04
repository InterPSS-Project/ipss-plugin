package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Coupled limiter diagnostics with the same independent inputs and both integration policies. */
public class Svsmo3t2LimiterSmibTest extends CorePluginTestSetup {
    private static final org.interpss.test.TestConfig CONFIG = org.interpss.test.TestConfig.load(
            "/test-config/svsmo3t2.json", "Svsmo3t2LimiterSmibTest");
    private static final double TOLERANCE = 1e-6;
    private static final String[] NAMES = {"V_1", "V_2", "V_3", "ANG_1", "ANG_2", "ANG_3",
            "MACHINE_ANGLE", "VAR_0", "VAR_1", "VAR_2", "VAR_6",
            "STATE_0", "STATE_1", "STATE_2", "STATE_3", "STATE_4", "STATE_5", "VAR_10"};

    @Test void blockingBoundaryAndRatingOnsetFollowTheIndependentReference() throws Exception {
        Path matrix = referenceRoot();
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-limiter-accepted-");
        var results = new LinkedHashMap<String, Object>();
        for (String profile : new String[]{"undervoltage-block", "short-rating"})
            for (boolean staggered : new boolean[]{false, true}) {
                String key = profile + (staggered ? "-staggered" : "-modified");
                Path root = Files.isDirectory(matrix.resolve(profile)) ? matrix.resolve(profile) : matrix.resolve(profile + "-a");
                results.put(key, compare(root, output.resolve(key + ".csv"), staggered,
                        profile.equals("undervoltage-block") ? "blocking" : "rating-onset"));
            }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), results);
    }

    @Test void completeDefiniteAndThermalRatingProfilesPassBothMethods() throws Exception {
        Path matrix = referenceRoot();
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-rating-accepted-");
        var results = new LinkedHashMap<String, Object>();
        for (String profile : new String[]{"short-rating", "thermal-active", "short-rating-no-ov", "thermal-no-ov",
                "short-rating-wide-ov", "short-rating-narrow-ov", "short-rating-high-multiplier", "thermal-active-half"})
            for (boolean staggered : new boolean[]{false, true}) {
                String key = profile + (staggered ? "-staggered" : "-modified");
                results.put(key, compare(matrix.resolve(profile), output.resolve(key + ".csv"), staggered, "rating"));
            }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), results);
        System.out.println("SVSMO3T2 complete rating acceptance " + output);
    }

    @Test void tripCommandAndNetworkRemovalFollowIndependentLifecycleUnderBothMethods() throws Exception {
        Path matrix = referenceRoot();
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-trip-accepted-");
        var results = new LinkedHashMap<String, Object>();
        for (String profile : new String[]{"overvoltage-trip-mild", "overvoltage-trip-mild-half",
                "overvoltage-no-trip", "overvoltage-trip-rebuild", "overvoltage-no-trip-rebuild"})
            for (boolean staggered : new boolean[]{false, true}) {
                String key = profile + (staggered ? "-staggered" : "-modified");
                results.put(key, compare(matrix.resolve(profile), output.resolve(key + ".csv"), staggered, "trip"));
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), results);
            }
        System.out.println("SVSMO3T2 trip lifecycle acceptance " + output);
    }

    @Test void directionalBlockingRecoveryMatchesEveryPhysicalOutputStatus() throws Exception {
        Path matrix = referenceRoot();
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-recovery-lifecycle-");
        var results = new LinkedHashMap<String, Object>();
        for (String profile : new String[]{"short-block", "short-block-zero-delay", "short-block-sensor-bypass",
                "long-block-sensed", "short-overvoltage-block", "short-overvoltage-zero-delay"})
            for (boolean staggered : new boolean[]{false, true}) {
                String key = profile + (staggered ? "-staggered" : "-modified");
                results.put(key, compare(matrix.resolve(profile), output.resolve(key + ".csv"), staggered, "recovery-lifecycle"));
            }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), results);
        System.out.println("SVSMO3T2 directional recovery lifecycle " + output);
    }

    @Test @Tag("extended") void compareBothMethodsWithoutDroppingLimiterOrEventSamples() throws Exception {
        var mapper = new ObjectMapper();
        Path matrix = referenceRoot();
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-limiter-coupled-");
        var results = new LinkedHashMap<String, Object>();
        String selected = CONFIG.string("profile");
        var entries = (com.fasterxml.jackson.databind.node.ArrayNode) mapper.readTree(matrix.resolve("matrix.json").toFile());
        Path supplement = matrix.resolve("matrix-ratings.json");
        if (Files.exists(supplement)) entries.addAll((com.fasterxml.jackson.databind.node.ArrayNode) mapper.readTree(supplement.toFile()));
        Path trips = matrix.resolve("matrix-trips.json");
        if (Files.exists(trips)) entries.addAll((com.fasterxml.jackson.databind.node.ArrayNode) mapper.readTree(trips.toFile()));
        for (var entry : entries) {
            if (!entry.get("repeat").asText().equals("a")) continue;
            String profile = entry.get("profile").asText();
            if (selected != null && !profile.equals(selected)) continue;
            for (boolean staggered : new boolean[]{false, true}) {
                String key = profile + (staggered ? "-staggered" : "-modified");
                results.put(key, compare(matrix.resolve(entry.get("path").asText()), output.resolve(key + ".csv"), staggered, "diagnostic"));
                mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), results);
            }
        }
        assertFalse(results.isEmpty());
        System.out.println("SVSMO3T2 coupled limiter diagnostics " + output);
    }

    static Path referenceRoot() throws Exception {
        String configured = CONFIG.string("matrix");
        if (configured != null) return Path.of(configured);
        Path root = Files.createTempDirectory(Path.of("target"), "svsmo3t2-limiter-reference-").toAbsolutePath().normalize();
        extract(root, "limiters.zip", "2f8edde0aff5b9699cbd0f3cf88aa908a5b584c2154f9b70bf55e332bd12bf28");
        extract(root, "ratings.zip", "49c580c091258ea4b208c65dd7d5ff3ba80864a9c07f2c7ec43ca56045a0a741");
        extract(root, "trips.zip", "866afbf13b6782f609d6a6f65b708f5b0f9a2fb6975bb1ac71239e3af5df580d");
        extract(root, "blocking-recovery.zip", "b9156274567854ff430d279d1ce7d5a3a3ec990d914cb43b253621e5a5dee4e0");
        return root;
    }

    private static void extract(Path root, String archive, String expectedHash) throws Exception {
        byte[] bytes;
        try (var stream = Svsmo3t2LimiterSmibTest.class.getResourceAsStream("/dynamic-models/svsmo3t2/" + archive)) {
            assertNotNull(stream); bytes = stream.readAllBytes();
        }
        assertEquals(expectedHash,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path file = root.resolve(entry.getName()).normalize();
                assertTrue(file.startsWith(root));
                Files.createDirectories(file.getParent());
                try (var output = org.interpss.core.dstab.reference.BenchmarkOutputGuard.newOutputStream(file, 4L * 1024 * 1024)) {
                    zip.transferTo(output);
                }
            }
        }
    }

    private static Object compare(Path root, Path output, boolean staggered, String qualification) throws Exception {
        var mapper = new ObjectMapper();
        var manifest = mapper.readTree(root.resolve("manifest.json").toFile());
        var files = manifest.get("files").fields();
        while (files.hasNext()) {
            var item = files.next();
            Path file = root.resolve(item.getKey()).toAbsolutePath().normalize();
            assertTrue(file.startsWith(root.toAbsolutePath().normalize()));
            assertEquals(item.getValue().asText(), java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("smib.raw").toString(), root.resolve("smib.dyr").toString());
        var net = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        net.setStaticLoadIncludedInYMatrix(true);
        net.setStaticLoadModel(StaticLoadModel.CONST_Z);
        net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().getVoltAdjConfig().setAdjTolerance(1e-9);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        double step = manifest.get("step_s").asDouble();
        algorithm.setSimuStepSec(step);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuOutputHandler(new StateMonitor());
        if (manifest.get("profile").asText().equals("fault")) {
            net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus3", net, SimpleFaultCode.GROUND_3P,
                    new Complex(0, manifest.get("fault_ohms").asDouble() / 529.), null, .05, manifest.path("fault_clear_s").asDouble(.1) - .05), "fault");
            var rebuildTime = manifest.get("trip_rebuild_time");
            if (rebuildTime != null && !rebuildTime.isNull())
                net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net, SimpleFaultCode.GROUND_3P,
                        new Complex(0, manifest.get("trip_rebuild_impedance_ohms").asDouble() / 529.), null,
                        rebuildTime.asDouble(), manifest.get("trip_rebuild_duration_s").asDouble()), "rebuild-discriminator");
        }
        var starts = new HashMap<Integer, double[]>();
        var staggeredSolver = staggered ? new StaggeredRecorder(algorithm, starts) : null;
        if (staggered) staggeredSolver.setEndpointTimePrecision(StaggeredEulerSolver.EndpointTimePrecision.SINGLE);
        algorithm.setSolver(staggered ? staggeredSolver : new Recorder(algorithm, starts));
        assertTrue(algorithm.initialization());
        var model = model(algorithm);
        double schedule = model.getScheduledVoltage();
        var frames = new ArrayList<double[]>();
        var lines = Files.readAllLines(root.resolve("reference.csv"));
        // A rounded reference clock can make RUN include one extra full step.
        // Simulate every supplied sample rather than truncate to nominal stop.
        int ticks = lines.stream().skip(1).mapToInt(line -> (int) Math.round(
                Double.parseDouble(line.substring(0, line.indexOf(','))) / step)).max().orElseThrow();
        assertTrue(Math.abs(ticks * step - manifest.get("stop_s").asDouble()) < 2 * step);
        for (int tick = 0; tick <= ticks; tick++) {
            frames.add(frame(algorithm));
            if (tick == ticks) break;
            if (tick == Math.round(.05 / step) && manifest.get("profile").asText().equals("reference-step")) {
                model.setScheduledVoltage(schedule + manifest.get("reference_change").asDouble());
                assertTrue(model.nextStep(0, DynamicSimuMethod.MODIFIED_EULER, 0));
                if (staggered) staggeredSolver.notifyDiscontinuity();
            }
            assertTrue(algorithm.solveDEqnStep(true));
        }
        assertEquals(TOLERANCE, ((DefaultDynamicSimulationSolver) algorithm.getSolver())
                .getLastNetworkSolutionDiagnostics().voltageTolerance(), 0.);
        var header = Arrays.asList(lines.getFirst().split(","));
        int[] columns = Arrays.stream(NAMES).mapToInt(header::indexOf).toArray();
        for (int column : columns) assertTrue(column >= 0);
        // Define the active-state phase from independent rating/status evidence,
        // never from the implementation's flags or its numerical error.
        int firstTripTick = Integer.MAX_VALUE;
        for (String line : lines.subList(1, lines.size())) {
            String[] row = line.split(",");
            if (Double.parseDouble(row[columns[17]]) == 0)
                firstTripTick = Math.min(firstTripTick, Math.max(0, (int) Math.round(Double.parseDouble(row[0]) / step)));
        }
        int removalTick = Integer.MAX_VALUE;
        if (firstTripTick != Integer.MAX_VALUE) {
            removalTick = (int) Math.round(manifest.path("fault_clear_s").asDouble(.1) / step);
            var rebuild = manifest.get("trip_rebuild_time");
            if (rebuild != null && !rebuild.isNull()) {
                int eventTick = (int) Math.round(rebuild.asDouble() / step);
                if (eventTick > firstTripTick) removalTick = eventTick;
            }
        }
        double[] maximum = new double[NAMES.length], at = new double[NAMES.length], squares = new double[NAMES.length];
        double[] attachedMaximum = new double[6], attachedSquares = new double[6], attachedAt = new double[6];
        double[] detachedState = null;
        int attachedSamples = 0, tripFlagMismatches = 0, removalFlagMismatches = 0, detachedSamples = 0;
        int blockingOutputMismatches = 0;
        double[] low = new double[NAMES.length], high = new double[NAMES.length];
        Arrays.fill(low, Double.POSITIVE_INFINITY); Arrays.fill(high, Double.NEGATIVE_INFINITY);
        double ratingOnsetMaximum = 0;
        double prefaultAngleMaximum = 0, referenceThermalMaximum = 0, minimumReferenceRating = Double.POSITIVE_INFINITY;
        int previousClock = Integer.MIN_VALUE;
        try (var writer = org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(output,
                org.interpss.core.dstab.reference.BenchmarkOutputGuard.estimateCsvBytes(lines.size(), 2 * NAMES.length + 8))) {
            writer.write("time_s,nominal_time_s,run_start");
            for (String name : NAMES) writer.write(",actual_" + name + ",reference_" + name);
            writer.write(",actual_trip_command,reference_trip_command,actual_network_detached,reference_network_detached,reference_state_active");
            writer.write('\n');
            for (String line : lines.subList(1, lines.size())) {
                double[] row = Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
                int tick = Math.max(0, (int) Math.round(row[0] / step));
                int clock = Float.floatToIntBits((float) row[0]);
                boolean repeated = previousClock == clock;
                previousClock = clock;
                var actual = repeated && row[0] >= 0 ? starts.getOrDefault(tick, frames.get(tick)) : frames.get(tick);
                if ((actual[7] == 0) != (row[columns[7]] == 0)) blockingOutputMismatches++;
                boolean referenceTripped = row[columns[17]] == 0;
                boolean referenceDetached = tick > removalTick || tick == removalTick && repeated;
                if ((actual[NAMES.length] == 1) != referenceTripped) tripFlagMismatches++;
                if ((actual[NAMES.length + 1] == 1) != referenceDetached) removalFlagMismatches++;
                if (!referenceDetached) {
                    attachedSamples++;
                    for (int i = 0; i < 6; i++) {
                        double error = Math.abs(actual[11 + i] - row[columns[11 + i]]);
                        attachedSquares[i] += error * error;
                        if (error > attachedMaximum[i]) { attachedMaximum[i] = error; attachedAt[i] = row[0]; }
                    }
                } else if (qualification.equals("trip")) {
                    detachedSamples++;
                    if (detachedState == null) detachedState = Arrays.copyOfRange(actual, 11, 17);
                    assertArrayEquals(detachedState, Arrays.copyOfRange(actual, 11, 17),
                            "Detached continuous states must freeze instead of integrating inactive source storage");
                }
                writer.write(row[0] + "," + (tick * step) + "," + (repeated ? 1 : 0));
                for (int i = 0; i < NAMES.length; i++) {
                    double reference = row[columns[i]], error = Math.abs(actual[i] - reference);
                    assertTrue(Double.isFinite(error), NAMES[i]);
                    squares[i] += error * error;
                    low[i] = Math.min(low[i], reference); high[i] = Math.max(high[i], reference);
                    if (i == 6 && row[0] < .05) prefaultAngleMaximum = Math.max(prefaultAngleMaximum, error);
                    if (i == 7 && tick * step >= .074 && tick * step < .08)
                        ratingOnsetMaximum = Math.max(ratingOnsetMaximum, error);
                    if (error > maximum[i]) { maximum[i] = error; at[i] = row[0]; }
                    writer.write("," + actual[i] + "," + reference);
                }
                referenceThermalMaximum = Math.max(referenceThermalMaximum, row[header.indexOf("STATE_4")]);
                minimumReferenceRating = Math.min(minimumReferenceRating, row[header.indexOf("VAR_10")]);
                writer.write("," + actual[NAMES.length] + "," + (referenceTripped ? 1 : 0)
                        + "," + actual[NAMES.length + 1] + "," + (referenceDetached ? 1 : 0)
                        + "," + (referenceDetached ? 0 : 1));
                writer.write('\n');
            }
        }
        var errors = new LinkedHashMap<String, Object>();
        for (int i = 0; i < NAMES.length; i++) errors.put(NAMES[i], Map.of("maximum", maximum[i], "time_s", at[i],
                "rmse", Math.sqrt(squares[i] / (lines.size() - 1))));
        var attachedErrors = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 6; i++) attachedErrors.put("STATE_" + i, Map.of("maximum", attachedMaximum[i],
                "time_s", attachedAt[i], "rmse", Math.sqrt(attachedSquares[i] / attachedSamples)));
        if (qualification.equals("recovery-lifecycle")) {
            assertEquals(0, blockingOutputMismatches, "Every physical blocking/release row must match");
            assertTrue(prefaultAngleMaximum < .002, "Prefault rotor-angle maximum degrees");
            assertTrue(Math.sqrt(squares[6] / (lines.size() - 1)) < .01, "Rotor-angle RMSE degrees");
            // Keep complete trajectory errors diagnostic: coarse ordinary integration
            // has a separate accuracy envelope, not an excuse to shift timer actions.
        } else if (qualification.equals("blocking")) {
            double count = lines.size() - 1;
            for (int i = 0; i < 3; i++) {
                assertTrue(Math.sqrt(squares[i] / count) / (high[i] - low[i]) < .001, NAMES[i] + " range NRMSE");
                assertTrue(maximum[i] < (staggered ? .000008 : .0031), NAMES[i] + " maximum");
            }
            for (int i = 3; i <= 6; i++) {
                assertTrue(Math.sqrt(squares[i] / count) < .00055, NAMES[i] + " angle RMSE degrees");
                assertTrue(maximum[i] < (staggered ? .0011 : .012), NAMES[i] + " angle maximum degrees");
            }
            assertTrue(maximum[7] < (staggered ? .00004 : .015), "Physical current maximum");
            assertTrue(Math.sqrt(squares[7] / count) < (staggered ? .000006 : .00042), "Physical current RMSE");
            assertTrue(maximum[8] < (staggered ? .00005 : .0145), "Physical reactive power maximum");
            assertTrue(Math.sqrt(squares[8] / count) < (staggered ? .000007 : .00042), "Physical reactive power RMSE");
            assertTrue(maximum[10] < .00000004, "Voltage reference maximum");
            double[] maxState = staggered ? new double[]{.000027, .0000003, .00004, 1e-12, 1e-12, .000067}
                    : new double[]{.0024, .00006, .0145, 1e-12, 1e-12, .0004};
            double[] rmsState = staggered ? new double[]{.0000032, .00000023, .000006, 1e-12, 1e-12, .0000048}
                    : new double[]{.000085, .0000017, .00043, 1e-12, 1e-12, .000032};
            for (int i = 0; i < 6; i++) {
                assertTrue(maximum[11 + i] < maxState[i], "STATE_" + i + " maximum");
                assertTrue(Math.sqrt(squares[11 + i] / count) < rmsState[i], "STATE_" + i + " RMSE");
            }
        } else if (qualification.equals("rating-onset")) {
            // This gate localizes expiration timing. All later samples remain
            // in the trace and metrics; post-clearing rating parity is still open.
            assertTrue(ratingOnsetMaximum < (staggered ? .000001 : .007), "Rating-onset current maximum");
        } else if (qualification.equals("rating")) {
            double count = lines.size() - 1;
            boolean coarseThermal = model.isThermalRatingActive() && step == .0005
                    && high[2] > model.getData().ov1();
            for (int i = 0; i < 3; i++) {
                assertTrue(Math.sqrt(squares[i] / count) / (high[i] - low[i]) < .001, NAMES[i] + " range NRMSE");
                assertTrue(maximum[i] < (staggered ? coarseThermal ? .0032 : .000019 : .004), NAMES[i] + " maximum");
            }
            for (int i = 3; i < 6; i++) {
                assertTrue(Math.sqrt(squares[i] / count) < (staggered ? .0008 : .002), NAMES[i] + " RMSE degrees");
                assertTrue(maximum[i] < .015, NAMES[i] + " maximum degrees");
            }
            assertTrue(prefaultAngleMaximum < .002, "Prefault rotor angle maximum degrees");
            assertTrue(Math.sqrt(squares[6] / count) < (staggered ? .00055 : .0016), "Rotor angle RMSE degrees");
            assertTrue(maximum[6] < (staggered ? .0015 : .003), "Rotor angle maximum degrees");
            double currentMax = staggered ? coarseThermal ? .0155 : .00009 : .019;
            double currentRms = staggered ? coarseThermal ? .0009 : .000022 : .0025;
            assertTrue(maximum[7] < currentMax, "Physical current maximum");
            assertTrue(Math.sqrt(squares[7] / count) < currentRms, "Physical current RMSE");
            assertTrue(Math.sqrt(squares[8] / count) / (high[8] - low[8]) < .003, "Reactive power range NRMSE");
            assertTrue(maximum[8] < (staggered ? coarseThermal ? .020 : .00011 : .025), "Reactive power maximum");
            assertTrue(maximum[10] < .00000004, "Voltage reference maximum");
            double[] maxState = staggered ? coarseThermal
                    ? new double[]{.000056, .00028, currentMax, 1e-12, .00000003, .000027}
                    : new double[]{.000013, .0002, currentMax, 1e-12, .00000003, .000014}
                    : new double[]{.0017, .0013, currentMax, 1e-12, .000013, .00044};
            double[] rmsState = staggered ? coarseThermal
                    ? new double[]{.000009, .00019, currentRms, 1e-12, .000000011, .0000092}
                    : new double[]{.000004, .000096, currentRms, 1e-12, .000000006, .0000042}
                    : new double[]{.00009, .00026, currentRms, 1e-12, .0000082, .00008};
            for (int i = 0; i < 6; i++) {
                assertTrue(maximum[11 + i] < maxState[i], "STATE_" + i + " maximum");
                assertTrue(Math.sqrt(squares[11 + i] / count) < rmsState[i], "STATE_" + i + " RMSE");
            }
            assertTrue(minimumReferenceRating < model.getData().continuousCurrent() * model.getData().shortCurrentMultiplier() - .1,
                    "Reference must exercise rating reduction");
            if (model.isThermalRatingActive()) assertTrue(referenceThermalMaximum > model.getData().thermalLimit(),
                    "Reference must exercise the thermal integrator threshold");
            else assertEquals(0, maximum[15], 0., "Definite rating must leave thermal state inactive");
        } else if (qualification.equals("trip")) {
            double count = lines.size() - 1;
            for (int i = 0; i < 3; i++) {
                assertTrue(Math.sqrt(squares[i] / count) / (high[i] - low[i]) < .001, NAMES[i] + " range NRMSE");
                assertTrue(maximum[i] < (staggered ? .00007 : .00025), NAMES[i] + " maximum");
            }
            for (int i = 3; i <= 6; i++) {
                assertTrue(Math.sqrt(squares[i] / count) < .0005, NAMES[i] + " angle RMSE degrees");
                assertTrue(maximum[i] < .001, NAMES[i] + " angle maximum degrees");
            }
            assertTrue(prefaultAngleMaximum < .002, "Prefault rotor angle maximum degrees");
            assertTrue(maximum[7] < (staggered ? .00031 : .0011), "Physical current maximum");
            assertTrue(Math.sqrt(squares[7] / count) < .0001, "Physical current RMSE");
            assertTrue(maximum[8] < (staggered ? .00033 : .0013), "Physical reactive power maximum");
            assertTrue(Math.sqrt(squares[8] / count) < .00011, "Physical reactive power RMSE");
            assertTrue(maximum[10] < .00000004, "Voltage reference maximum");
            assertEquals(0, maximum[17], "Available rating must match every reference row");
            assertEquals(0, tripFlagMismatches, "Trip commands must match every reference row");
            assertEquals(0, removalFlagMismatches, "Removal must match both sides of the next network event");
            double[] maxState = staggered ? new double[]{.000033, .000001, .00031, 1e-12, 1e-12, .000016}
                    : new double[]{.00032, .000003, .0011, 1e-12, 1e-12, .000055};
            double[] rmsState = staggered ? new double[]{.000003, .0000006, .00004, 1e-12, 1e-12, .0000021}
                    : new double[]{.000035, .0000008, .0003, 1e-12, 1e-12, .000026};
            for (int i = 0; i < 6; i++) {
                assertTrue(attachedMaximum[i] < maxState[i], "Attached STATE_" + i + " maximum");
                assertTrue(Math.sqrt(attachedSquares[i] / attachedSamples) < rmsState[i], "Attached STATE_" + i + " RMSE");
            }
            boolean expectedTrip = !root.getFileName().toString().contains("no-trip");
            assertEquals(expectedTrip, firstTripTick != Integer.MAX_VALUE, "Profile must activate its intended trip/control path");
            assertEquals(expectedTrip, detachedSamples > 0);
            var observations = manifest.get("lifecycle_observations");
            assertNotNull(observations);
            for (var observation : observations) assertEquals(1, observation.get("model_status").asInt(),
                    "Model bypass flag is independent of electrical device mode");
            assertEquals(expectedTrip ? 0 : 1, observations.get(observations.size() - 1).get("device_mode").asInt());
        }
        System.out.println("SVSMO3T2 limiter " + output.getFileName() + " " + errors);
        var result = new LinkedHashMap<String, Object>();
        result.put("blocking_output_mismatches", blockingOutputMismatches);
        result.putAll(Map.of("status", qualification.equals("recovery-lifecycle") ? "coupled-blocking-lifecycle-accepted-trajectory-diagnostic"
                : qualification.equals("blocking") ? "coupled-blocking-accepted-cached-mvar-diagnostic"
                : qualification.equals("rating-onset") ? "rating-onset-accepted-full-trajectory-diagnostic"
                : qualification.equals("rating") ? "coupled-rating-accepted-cached-mvar-diagnostic"
                : qualification.equals("trip") ? "coupled-trip-accepted-inactive-storage-and-cached-mvar-diagnostic" : "diagnostic-not-accepted",
                "integration_method", staggered ? "STAGGERED_EULER" : "MODIFIED_EULER", "rating_onset_current_maximum", ratingOnsetMaximum,
                "endpoint_clock", staggered ? "SINGLE" : "DOUBLE", "network_tolerance", TOLERANCE,
                "step_s", step, "samples", lines.size() - 1, "channels", errors));
        result.put("reference_attached_state_samples", attachedSamples);
        result.put("reference_attached_state_channels", attachedErrors);
        result.put("trip_command_mismatches", tripFlagMismatches);
        result.put("network_removal_mismatches", removalFlagMismatches);
        return result;
    }

    private static Svsmo3t2Model model(DynamicSimuAlgorithm algorithm) {
        return (Svsmo3t2Model) algorithm.getNetwork().getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                .filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
    }

    private static double[] frame(DynamicSimuAlgorithm algorithm) {
        var net = algorithm.getNetwork();
        var model = model(algorithm);
        var result = new double[NAMES.length + 2];
        for (int i = 0; i < 3; i++) {
            result[i] = net.getBus("Bus" + (i + 1)).getVoltageMag();
            result[i + 3] = Math.toDegrees(net.getBus("Bus" + (i + 1)).getVoltageAng());
        }
        result[6] = Math.toDegrees(net.getMachine("Bus2-mach1").getAngle());
        result[7] = model.getCurrent();
        result[8] = model.getMvarOutput() / model.getData().statcomBaseMva();
        result[9] = model.getMvarOutput();
        result[10] = model.getVoltageReference();
        int i = 11;
        for (double value : model.getNamedStates().values()) result[i++] = value;
        result[i] = model.getAvailableCurrentRating();
        result[NAMES.length] = model.isTripped() ? 1 : 0;
        result[NAMES.length + 1] = model.isNetworkDetached() ? 1 : 0;
        return result;
    }

    private static final class Recorder extends DefaultDynamicSimulationSolver {
        final Map<Integer, double[]> starts;
        Recorder(DynamicSimuAlgorithm algorithm, Map<Integer, double[]> starts) {
            super(algorithm); this.starts = starts; converge_tol = TOLERANCE;
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean result = super.networkSolutionStep();
            if (result && "INITIAL".equals(networkSolutionStage))
                starts.put((int) Math.round(dstabAlgo.getSimuTime() / dstabAlgo.getSimuStepSec()), frame(dstabAlgo));
            return result;
        }
        @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method) throws DStabSimuException {
            super.rebaseDynamicModelInputs(method);
            starts.put((int) Math.round(dstabAlgo.getSimuTime() / dstabAlgo.getSimuStepSec()), frame(dstabAlgo));
        }
    }

    private static final class StaggeredRecorder extends StaggeredEulerSolver {
        final Map<Integer, double[]> starts;
        StaggeredRecorder(DynamicSimuAlgorithm algorithm, Map<Integer, double[]> starts) {
            super(algorithm); this.starts = starts; setNetworkSolutionTolerance(TOLERANCE);
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean result = super.networkSolutionStep();
            if (result && "INITIAL".equals(networkSolutionStage))
                starts.put((int) Math.round(dstabAlgo.getSimuTime() / dstabAlgo.getSimuStepSec()), frame(dstabAlgo));
            return result;
        }
        @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method) throws DStabSimuException {
            super.rebaseDynamicModelInputs(method);
            starts.put((int) Math.round(dstabAlgo.getSimuTime() / dstabAlgo.getSimuStepSec()), frame(dstabAlgo));
        }
    }
}
