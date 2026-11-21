package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import com.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full recorded-input state diagnostics; execution is not a trajectory acceptance gate. */
@Tag("extended")
public class Svsmo3t2MatrixReplayTest extends CorePluginTestSetup {
    private static final org.interpss.test.TestConfig CONFIG = org.interpss.test.TestConfig.load(
            "/test-config/svsmo3t2.json", "Svsmo3t2MatrixReplayTest");
    @Test void replayEveryCapturedInputWithoutRemovingAnySamples() throws Exception {
        String configured = CONFIG.string("matrix");
        Path matrix = configured == null ? Svsmo3t2LimiterSmibTest.referenceRoot() : Path.of(configured);
        var mapper = new ObjectMapper();
        var results = new LinkedHashMap<String, Object>();
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-replay-");
        for (var entry : mapper.readTree(matrix.resolve("matrix.json").toFile())) {
            if (!entry.get("repeat").asText().equals("a")) continue;
            String profile = entry.get("profile").asText();
            Path root = matrix.resolve(entry.get("path").asText());
            results.put(profile, run(root, output, profile));
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), results);
        }
        assertFalse(results.isEmpty());
        System.out.println("SVSMO3T2 matrix diagnostics " + output);
    }

    @Test void overvoltageRecoveryRecordedInputsLocalizeTheControllerResidual() throws Exception {
        Path matrix = Svsmo3t2LimiterSmibTest.referenceRoot();
        Path output = Files.createTempDirectory(Path.of("target"), "svsmo3t2-recovery-replay-");
        var result = new LinkedHashMap<String, Object>();
        for (int local : new int[]{0, 1, 2, 4, 8}) {
            String name = "short-overvoltage-block-local-" + local;
            result.put(name, run(matrix.resolve("short-overvoltage-block"), output, name, local));
        }
        var ordinary = Files.readAllLines(output.resolve("short-overvoltage-block-local-0.csv"));
        var shared = Files.readAllLines(output.resolve("short-overvoltage-block-local-1.csv"));
        assertEquals(ordinary.size(), shared.size());
        for (int row = 1; row < ordinary.size(); row++) {
            var expected = Arrays.stream(ordinary.get(row).split(",")).mapToDouble(Double::parseDouble).toArray();
            var actual = Arrays.stream(shared.get(row).split(",")).mapToDouble(Double::parseDouble).toArray();
            assertArrayEquals(expected, actual, 2e-12, "One shared-state Heun step preserves ordinary recurrence");
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), result);
        System.out.println("SVSMO3T2 recovery recorded-input diagnostics " + output);
    }

    private static Object run(Path root, Path output, String profile) throws Exception {
        return run(root, output, profile, 0);
    }

    private static Object run(Path root, Path output, String profile, int localSteps) throws Exception {
        var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
        var files = manifest.get("files").fields();
        while (files.hasNext()) {
            var file = files.next();
            Path artifact = root.resolve(file.getKey()).toAbsolutePath().normalize();
            assertTrue(artifact.startsWith(root.toAbsolutePath().normalize()), "Artifact must stay inside its capture");
            assertEquals(file.getValue().asText(), java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))), file.getKey());
        }
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("smib.raw").toString(),
                root.resolve("smib.dyr").toString());
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
        assertTrue(algorithm.initialization());
        var bus = net.getDStabBus("Bus3");
        var model = bus.getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance)
                .map(Svsmo3t2Model.class::cast).findFirst().orElseThrow();
        List<String> lines = Files.readAllLines(root.resolve("reference.csv"));
        List<String> header = Arrays.asList(lines.get(0).split(","));
        int voltage = header.indexOf("V_3");
        int remote = header.indexOf("V_" + (model.getData().remoteBusNumber() == 0 ? 3 : model.getData().remoteBusNumber()));
        int schedule = header.indexOf("VAR_25");
        int auxiliary = header.indexOf("VAR_26");
        var rows = lines.stream().skip(1).map(line -> Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
        int[] stateColumns = new int[6];
        for (int i = 0; i < 6; i++) stateColumns[i] = header.indexOf("STATE_" + i);
        var maximum = new double[6];
        var squares = new double[6];
        var times = new double[6];
        double[] previous = null;
        // Test-only local integration; coefficients and discrete history keep the outer interval.
        double[] initial = new double[6], trial = new double[6], first = new double[6], second = new double[6];
        Path trace = output.resolve(profile + ".csv");
        try (var writer = BenchmarkOutputGuard.newWriter(trace, BenchmarkOutputGuard.estimateCsvBytes(rows.size(), 7))) {
            writer.write("time_s,STATE_0,STATE_1,STATE_2,STATE_3,STATE_4,STATE_5\n");
            for (var row : rows) {
                if (previous != null && row[0] > previous[0] + step / 2 && row[0] > 0) {
                    if (localSteps == 0) {
                        setInputs(model, previous, voltage, remote, schedule, auxiliary);
                        assertTrue(model.nextStep(step, DynamicSimuMethod.MODIFIED_EULER, 0));
                        setInputs(model, row, voltage, remote, schedule, auxiliary);
                        assertTrue(model.nextStep(step, DynamicSimuMethod.MODIFIED_EULER, 1));
                    } else {
                        double h = step / localSteps;
                        for (int local = 0; local < localSteps; local++) {
                            setInterpolatedInputs(model, previous, row, (double) local / localSteps, voltage, remote, schedule, auxiliary);
                            model.readStaggeredState(initial, 0);
                            model.evaluateStaggered(step, first, 0);
                            for (int state = 0; state < 6; state++) trial[state] = initial[state] + h * first[state];
                            model.applyStaggeredState(trial, 0, step);
                            setInterpolatedInputs(model, previous, row, (double) (local + 1) / localSteps, voltage, remote, schedule, auxiliary);
                            model.evaluateStaggered(step, second, 0);
                            for (int state = 0; state < 6; state++) trial[state] = initial[state] + .5 * h * (first[state] + second[state]);
                            model.applyStaggeredState(trial, 0, step);
                        }
                        model.completeStaggeredStep(step);
                    }
                } else {
                    setInputs(model, row, voltage, remote, schedule, auxiliary);
                    if (previous != null && row[0] >= 0 && row[0] == previous[0])
                        assertTrue(model.nextStep(0, DynamicSimuMethod.MODIFIED_EULER, 0));
                }
                var states = new ArrayList<>(model.getNamedStates().values());
                writer.write(Double.toString(row[0]));
                for (int i = 0; i < 6; i++) {
                    double value = states.get(i);
                    assertTrue(Double.isFinite(value), profile + " STATE_" + i);
                    double error = Math.abs(value - row[stateColumns[i]]);
                    squares[i] += error * error;
                    if (error > maximum[i]) { maximum[i] = error; times[i] = row[0]; }
                    writer.write("," + value);
                }
                writer.write('\n');
                previous = row;
            }
        }
        var errors = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 6; i++) errors.put("STATE_" + i,
                Map.of("maximum", maximum[i], "time_s", times[i], "rmse", Math.sqrt(squares[i] / rows.size())));
        System.out.println("SVSMO3T2 replay " + profile + " " + errors);
        return Map.of("status", "diagnostic-not-accepted", "states", errors, "samples", rows.size(),
                "input_policy", localSteps == 0 ? "Own independent trace, original rows and interval-side duplicates; each positive grid interval is one configured modified-Euler step."
                        : "Test-only local Heun steps with linearly interpolated recorded boundary inputs; outer coefficients/history unchanged; all original rows/event duplicates retained.",
                "local_steps", localSteps,
                "trace", trace.getFileName().toString());
    }

    private static void setInterpolatedInputs(Svsmo3t2Model model, double[] left, double[] right, double fraction,
            int local, int remote, int schedule, int auxiliary) {
        model.getDStabBus().setVoltage(new Complex(left[local] + fraction * (right[local] - left[local]), 0));
        model.getRemoteBus().setVoltage(new Complex(left[remote] + fraction * (right[remote] - left[remote]), 0));
        model.setScheduledVoltage(left[schedule] + fraction * (right[schedule] - left[schedule]));
        model.setAuxiliarySignal(left[auxiliary] + fraction * (right[auxiliary] - left[auxiliary]));
    }

    private static void setInputs(Svsmo3t2Model model, double[] row, int local, int remote, int schedule, int auxiliary) {
        model.getDStabBus().setVoltage(new Complex(row[local], 0));
        model.getRemoteBus().setVoltage(new Complex(row[remote], 0));
        model.setScheduledVoltage(row[schedule]);
        model.setAuxiliarySignal(row[auxiliary]);
    }
}
