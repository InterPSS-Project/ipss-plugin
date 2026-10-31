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
    private static final double TOLERANCE = 1e-6;
    private static final String[] NAMES = {"V_1", "V_2", "V_3", "ANG_1", "ANG_2", "ANG_3",
            "MACHINE_ANGLE", "VAR_0", "VAR_1", "VAR_2", "VAR_6",
            "STATE_0", "STATE_1", "STATE_2", "STATE_3", "STATE_4", "STATE_5"};

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

    @Test @Tag("extended") void compareBothMethodsWithoutDroppingLimiterOrEventSamples() throws Exception {
        var mapper = new ObjectMapper();
        Path matrix = referenceRoot();
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-limiter-coupled-");
        var results = new LinkedHashMap<String, Object>();
        String selected = System.getProperty("svsmo3t2.profile");
        for (var entry : mapper.readTree(matrix.resolve("matrix.json").toFile())) {
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

    private static Path referenceRoot() throws Exception {
        String configured = System.getProperty("svsmo3t2.matrix");
        if (configured != null) return Path.of(configured);
        Path root = Files.createTempDirectory(Path.of("target"), "svsmo3t2-limiter-reference-").toAbsolutePath().normalize();
        byte[] bytes;
        try (var stream = Svsmo3t2LimiterSmibTest.class.getResourceAsStream("/dynamic-models/svsmo3t2/limiters.zip")) {
            assertNotNull(stream); bytes = stream.readAllBytes();
        }
        assertEquals("2f8edde0aff5b9699cbd0f3cf88aa908a5b584c2154f9b70bf55e332bd12bf28",
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
        return root;
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
                    new Complex(0, manifest.get("fault_ohms").asDouble() / 529.), null, .05, .05), "fault");
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
        double[] maximum = new double[NAMES.length], at = new double[NAMES.length], squares = new double[NAMES.length];
        double[] low = new double[3], high = new double[3];
        Arrays.fill(low, Double.POSITIVE_INFINITY); Arrays.fill(high, Double.NEGATIVE_INFINITY);
        double ratingOnsetMaximum = 0;
        int previousClock = Integer.MIN_VALUE;
        try (var writer = org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(output,
                org.interpss.core.dstab.reference.BenchmarkOutputGuard.estimateCsvBytes(lines.size(), 2 * NAMES.length + 3))) {
            writer.write("time_s,nominal_time_s,run_start");
            for (String name : NAMES) writer.write(",actual_" + name + ",reference_" + name);
            writer.write('\n');
            for (String line : lines.subList(1, lines.size())) {
                double[] row = Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
                int tick = Math.max(0, (int) Math.round(row[0] / step));
                int clock = Float.floatToIntBits((float) row[0]);
                boolean repeated = previousClock == clock;
                previousClock = clock;
                var actual = repeated && row[0] >= 0 ? starts.getOrDefault(tick, frames.get(tick)) : frames.get(tick);
                writer.write(row[0] + "," + (tick * step) + "," + (repeated ? 1 : 0));
                for (int i = 0; i < NAMES.length; i++) {
                    double reference = row[columns[i]], error = Math.abs(actual[i] - reference);
                    assertTrue(Double.isFinite(error), NAMES[i]);
                    squares[i] += error * error;
                    if (i < 3) { low[i] = Math.min(low[i], reference); high[i] = Math.max(high[i], reference); }
                    if (i == 7 && tick * step >= .074 && tick * step < .08)
                        ratingOnsetMaximum = Math.max(ratingOnsetMaximum, error);
                    if (error > maximum[i]) { maximum[i] = error; at[i] = row[0]; }
                    writer.write("," + actual[i] + "," + reference);
                }
                writer.write('\n');
            }
        }
        var errors = new LinkedHashMap<String, Object>();
        for (int i = 0; i < NAMES.length; i++) errors.put(NAMES[i], Map.of("maximum", maximum[i], "time_s", at[i],
                "rmse", Math.sqrt(squares[i] / (lines.size() - 1))));
        if (qualification.equals("blocking")) {
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
        }
        System.out.println("SVSMO3T2 limiter " + output.getFileName() + " " + errors);
        return Map.of("status", qualification.equals("blocking") ? "coupled-blocking-accepted-cached-mvar-diagnostic"
                : qualification.equals("rating-onset") ? "rating-onset-accepted-full-trajectory-diagnostic" : "diagnostic-not-accepted",
                "integration_method", staggered ? "STAGGERED_EULER" : "MODIFIED_EULER", "rating_onset_current_maximum", ratingOnsetMaximum,
                "endpoint_clock", staggered ? "SINGLE" : "DOUBLE", "network_tolerance", TOLERANCE,
                "samples", lines.size() - 1, "channels", errors);
    }

    private static Svsmo3t2Model model(DynamicSimuAlgorithm algorithm) {
        return (Svsmo3t2Model) algorithm.getNetwork().getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                .filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
    }

    private static double[] frame(DynamicSimuAlgorithm algorithm) {
        var net = algorithm.getNetwork();
        var model = model(algorithm);
        var result = new double[NAMES.length];
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
