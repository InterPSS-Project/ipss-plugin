package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.mach.Machine;

/** Coupled voltage/angle acceptance and complete controller diagnostics retain all reference samples. */
public class Pss2bCoupledBoundaryConformanceTest {
    record Frame(double time, String kind, Map<String, Double> values) { }

    @Test void everyCapturedProfileMeetsVoltageAndAngleGatesWithTypedStages(@TempDir Path staged) throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        var references = new Pss2bReferenceFixture(staged);
        var results = new LinkedHashMap<String, Object>();
        Path output = Files.createTempDirectory(Path.of("target"), "pss2b-coupled-");
        System.out.println("PSS2B coupled boundary output " + output.getFileName());
        for (var entry : references.matrix()) {
            if (!entry.get("repeat").asText().equals("a")) continue;
            String tag = entry.get("tag").asText();
            references.requireConstantPowerLoads("pss2b-native-" + tag);
            var root = references.stage("pss2b-native-" + tag);
            results.put(tag, run(root, entry.get("profile"), output));
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), results);
        }
    }

    private static Object run(Path root, JsonNode profile, Path output) throws Exception {
        var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
        var expected = Pss2bReferenceReplayTest.rows(root.resolve("native.csv"));
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(), root.resolve("dynamic.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm(); var net = context.getDStabilityNet();
        net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);
        net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);
        net.setStaticLoadIncludedInYMatrix(false);
        net.setStaticLoadZPartInYMatrix(true);
        // Reference solution setting, established from the retained probe and primary documentation.
        for (var bus : net.getBusList()) {
            var frequency = assertInstanceOf(com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl.class,
                    bus.getBusFreqMeasureBlock());
            frequency.setTf(.04);
        }
        var machine = net.getMachine("Bus1-mach1");
        var pss = (Ieee1992PSS2BStabilizer) machine.getStabilizer();
        Pss2bReferenceReplayTest.useCapturedCoefficients(pss, manifest);
        algorithm.getAclfAlgorithm().setTolerance(1e-10); assertTrue(algorithm.getAclfAlgorithm().loadflow());
        double step = manifest.get("step_s").asDouble();
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER); algorithm.setSimuStepSec(step);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        String event = profile.get("event").asText();
        if (event.equals("fault")) net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P, new Complex(0., .2), null, .05, .05), "fault");
        var recorder = new Recorder(algorithm, event.equals("reference")); algorithm.setSolver(recorder);
        assertTrue(algorithm.initialization());
        assertTrue(pss.hasCompiledStageEvaluator(), "Automatic typed-stage coverage");
        recorder.coordinates = Pss2bReferenceReplayTest.coordinates(pss);
        var initial = recorder.capture(0., "INITIAL");
        recorder.enabled = true;
        while (algorithm.getSimuTime() < .5 - step / 2.) assertTrue(algorithm.solveDEqnStep(true));
        recorder.frames.add(recorder.capture(algorithm.getSimuTime(), "FINAL"));
        assertTrue(pss.getCompiledStageEvaluationCount() > 1000);
        long startups = expected.stream().filter(r -> r.get("time_s") < 0.).count();
        assertEquals(expected.size(), recorder.frames.size() + startups, "Complete grid and interval-side sample count");
        var maximum = new LinkedHashMap<String, Double>(); var at = new LinkedHashMap<String, Double>();
        var sumSquares = new LinkedHashMap<String, Double>();
        double prefaultAngleMaximum = 0., minimumOutput = Double.POSITIVE_INFINITY, maximumOutput = Double.NEGATIVE_INFINITY;
        for (String key : initial.values.keySet()) { maximum.put(key, 0.); at.put(key, 0.); sumSquares.put(key, 0.); }
        Path trace = output.resolve(root.getFileName() + ".csv.gz");
        long estimate = BenchmarkOutputGuard.estimateCsvBytes(expected.size(), initial.values.size());
        try (var raw = BenchmarkOutputGuard.newOutputStream(trace, estimate);
                var gzip = new java.util.zip.GZIPOutputStream(raw);
                var writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(gzip, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.write("reference_time_s,actual_time_s,sample_kind," + String.join(",", initial.values.keySet()) + "\n");
            int frame = 0;
            for (var row : expected) {
                double time = row.get("time_s");
                Frame actual = time < 0. ? initial : recorder.frames.get(frame++);
                if (time >= 0.) assertEquals(time, actual.time, 2e-5, "Accumulated independent single-precision clock");
                if (time < .05) prefaultAngleMaximum = Math.max(prefaultAngleMaximum,
                        Math.abs(actual.values.get("MACH_ANGLE") - row.get("MACH_ANGLE")));
                double y = actual.values.get("VOTHSG");
                assertTrue(y >= pss.vstmin - 1e-12 && y <= pss.vstmax + 1e-12, "Output limiter at every retained sample");
                minimumOutput = Math.min(minimumOutput, y); maximumOutput = Math.max(maximumOutput, y);
                writer.write(time + "," + actual.time + "," + (time < 0. ? "STARTUP_HELD_INITIAL" : actual.kind));
                for (var value : actual.values.entrySet()) {
                    double x = value.getValue(); assertTrue(Double.isFinite(x), value.getKey());
                    double error = x - row.get(value.getKey());
                    if (Math.abs(error) > maximum.get(value.getKey())) { maximum.put(value.getKey(), Math.abs(error)); at.put(value.getKey(), time); }
                    sumSquares.merge(value.getKey(), error * error, Double::sum);
                    writer.write("," + x);
                }
                writer.write('\n');
            }
            assertEquals(recorder.frames.size(), frame);
        }
        var rmse = new LinkedHashMap<String, Double>();
        sumSquares.forEach((key, value) -> rmse.put(key, Math.sqrt(value / expected.size())));
        var normalizedVoltage = new LinkedHashMap<String, Double>();
        for (String channel : List.of("V_BUS1", "V_BUS2")) {
            double minimum = expected.stream().mapToDouble(row -> row.get(channel)).min().orElseThrow();
            double maximumReference = expected.stream().mapToDouble(row -> row.get(channel)).max().orElseThrow();
            double range = maximumReference - minimum;
            if (range == 0.) {
                assertEquals(0., rmse.get(channel), 1e-12, "Constant reference channel uses an absolute roundoff check");
                normalizedVoltage.put(channel, 0.);
            } else {
                double nrmse = rmse.get(channel) / range;
                assertTrue(nrmse < .001, root.getFileName() + " " + channel + " NRMSE=" + nrmse);
                normalizedVoltage.put(channel, nrmse);
            }
        }
        assertTrue(rmse.get("MACH_ANGLE") < .01, "Rotor-angle RMSE in degrees");
        assertTrue(prefaultAngleMaximum < .002, "Prefault maximum rotor-angle error in degrees");
        if (profile.get("variant").asText().equals("limits")) {
            assertEquals(pss.vstmin, minimumOutput, 1e-12, "Lower output bound must be active");
            assertEquals(pss.vstmax, maximumOutput, 1e-12, "Upper output bound must be active");
        }
        System.out.println("PSS2B coupled " + root.getFileName() + " max V=" + maximum.get("V_BUS1")
                + " angle=" + maximum.get("MACH_ANGLE") + " output=" + maximum.get("VOTHSG"));
        return Map.of("status", "boundary-accepted-controller-states-diagnostic", "maximum_error", maximum,
                "maximum_time_s", at, "rmse", rmse, "samples", expected.size(),
                "startup_policy", "Compare retained negative-time rows to the initialized snapshot; no negative-time integration",
                "voltage_range_nrmse", normalizedVoltage, "bus_frequency_filter_s", .04,
                "trace", trace.getFileName().toString());
    }

    private static final class Recorder extends DefaultDynamicSimulationSolver {
        final List<Frame> frames = new ArrayList<>(); final boolean referenceStep;
        boolean enabled; DoubleSupplier[] coordinates;
        Recorder(DynamicSimuAlgorithm algorithm, boolean referenceStep) { super(algorithm); this.referenceStep = referenceStep; }
        @Override public void beforeStep(double time) throws DStabSimuException {
            if (enabled && (Math.abs(time - .05) < 1e-9 || Math.abs(time - .1) < 1e-9)) {
                frames.add(capture(time, "INTERVAL_PRE"));
                if (referenceStep && Math.abs(time - .05) < 1e-9) {
                    var exciter = (com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)
                            dstabAlgo.getNetwork().getMachine("Bus1-mach1").getExciter();
                    exciter.setRefPoint(exciter.getRefPoint() + .01);
                }
            }
            super.beforeStep(time);
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            if (solved && enabled && "INITIAL".equals(networkSolutionStage)) frames.add(capture(dstabAlgo.getSimuTime(), "GRID"));
            return solved;
        }
        Frame capture(double time, String kind) {
            var net = dstabAlgo.getNetwork(); Machine machine = net.getMachine("Bus1-mach1");
            var pss = (Ieee1992PSS2BStabilizer) machine.getStabilizer();
            var values = new LinkedHashMap<String, Double>();
            for (int id : new int[]{1, 2}) {
                var bus = net.getDStabBus("Bus" + id);
                values.put("V_BUS" + id, bus.getVoltageMag());
                values.put("ANGLE_BUS" + id, Math.toDegrees(bus.getVoltage().getArgument()));
                values.put("FREQ" + id, bus.getFreq() - 1.);
            }
            var voltage = machine.getDStabBus().getVoltage();
            var power = voltage.multiply(machine.getIgen().subtract(voltage.multiply(machine.getYgen())).conjugate());
            values.put("P_PU", power.getReal()); values.put("Q_PU", power.getImaginary());
            values.put("MACH_ANGLE", Math.toDegrees(machine.getAngle())); values.put("MACH_SPEED", machine.getSpeed() - 1.);
            values.put("EFD", machine.getEfd()); values.put("PMECH", machine.getPm());
            values.put("VOTHSG", pss.getOutput(machine));
            for (int i = 0; i < coordinates.length; i++) values.put("PSS_STATE_" + i, coordinates[i].getAsDouble());
            values.put("PSS_MEMORY_1", pss.getInput1VoltageMemory()); values.put("PSS_DVDT_1", pss.getInput1VoltageDerivative());
            values.put("PSS_MEMORY_2", pss.getInput2VoltageMemory()); values.put("PSS_DVDT_2", pss.getInput2VoltageDerivative());
            return new Frame(time, kind, values);
        }
    }
}
