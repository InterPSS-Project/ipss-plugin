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
import org.interpss.core.dstab.reference.RelativeRmse;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
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
    private static final double NETWORK_TOLERANCE = 1e-6;
    record Frame(double time, String kind, Map<String, Double> values) { }

    @Test void everyCapturedProfileMeetsVoltageAndAngleGatesWithTypedStages(@TempDir Path staged) throws Exception {
        compareMatrix(staged, false);
    }

    @Test void everyCapturedProfileMeetsVoltageAndAngleGatesWithSharedStaggeredStates(@TempDir Path staged) throws Exception {
        compareMatrix(staged, true);
    }

    private static void compareMatrix(Path staged, boolean staggered) throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        var references = new Pss2bReferenceFixture(staged);
        var results = new LinkedHashMap<String, Object>();
        Path output = Files.createTempDirectory(Path.of("target"), staggered ? "pss2b-staggered-coupled-" : "pss2b-coupled-");
        System.out.println("PSS2B coupled boundary output " + output.getFileName());
        for (var entry : references.matrix()) {
            if (!entry.get("repeat").asText().equals("a")) continue;
            String tag = entry.get("tag").asText();
            references.requireConstantPowerLoads("pss2b-native-" + tag);
            var root = references.stage("pss2b-native-" + tag);
            results.put(tag, run(root, entry.get("profile"), output, staggered));
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(), results);
        }
    }

    static Object run(Path root, JsonNode profile, Path output, boolean staggered) throws Exception {
        return run(root, profile, output, staggered, 1);
    }

    static Object run(Path root, JsonNode profile, Path output, boolean staggered, int exciterSubsteps) throws Exception {
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
        double end = Math.round(expected.getLast().get("time_s")/step)*step;
        assertTrue(end>=manifest.get("end_s").asDouble()-step/2,
                "Retain the reference's complete final integration interval");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER); algorithm.setSimuStepSec(step);
        algorithm.setTotalSimuTimeSec(end);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        String event = profile.get("event").asText();
        if (event.equals("fault")) net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P, new Complex(0., .2), null, .05, .05), "fault");
        var recorder = new Recording(algorithm, event.equals("reference"));
        if (staggered) {
            var solver = new StaggeredRecorder(algorithm, recorder);
            solver.setSt1Substeps(exciterSubsteps);
            algorithm.setSolver(solver);
        } else algorithm.setSolver(new Recorder(algorithm, recorder));
        assertTrue(algorithm.initialization());
        if (!staggered) assertTrue(pss.hasCompiledStageEvaluator(), "Automatic typed-stage coverage");
        recorder.coordinates = Pss2bReferenceReplayTest.coordinates(pss);
        var initial = recorder.capture(0., "INITIAL");
        recorder.enabled = true;
        while (algorithm.getSimuTime() < end - step / 2.) assertTrue(algorithm.solveDEqnStep(true));
        recorder.frames.add(recorder.capture(algorithm.getSimuTime(), "FINAL"));
        assertEquals(NETWORK_TOLERANCE, ((DefaultDynamicSimulationSolver) algorithm.getSolver())
                .getLastNetworkSolutionDiagnostics().voltageTolerance(), 0.);
        if (staggered) assertTrue(pss.getStaggeredEvaluationCount() > 1000, "Shared participant coverage");
        else assertTrue(pss.getCompiledStageEvaluationCount() > 1000);
        long startups = expected.stream().filter(r -> r.get("time_s") < 0.).count();
        assertEquals(expected.size(), recorder.frames.size() + startups, "Complete grid and interval-side sample count");
        var maximum = new LinkedHashMap<String, Double>(); var at = new LinkedHashMap<String, Double>();
        var sumSquares = new LinkedHashMap<String, Double>();
        var relative = new LinkedHashMap<String, RelativeRmse>();
        double prefaultAngleMaximum = 0., minimumOutput = Double.POSITIVE_INFINITY, maximumOutput = Double.NEGATIVE_INFINITY;
        double firstEventPowerStateError = 0.; int firstEventSamples = 0;
        for (String key : initial.values.keySet()) { maximum.put(key, 0.); at.put(key, 0.); sumSquares.put(key, 0.); relative.put(key,new RelativeRmse()); }
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
                if (staggered && event.equals("fault") && pss.getData().getIcs1() == 6 && pss.getData().getIcs2() == 3
                        && Math.abs(time - (.05 + step)) < step / 10) {
                    firstEventSamples++;
                    firstEventPowerStateError = Math.max(firstEventPowerStateError,
                            Math.abs(actual.values.get("PSS_STATE_3") - row.get("PSS_STATE_3")));
                }
                if (time >= 0.) assertEquals(time, actual.time, end>.5?step/3:2e-5,
                        "Retain physical timestamps and distinguish adjacent integration ordinals");
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
                    relative.get(value.getKey()).add(x,row.get(value.getKey()));
                    writer.write("," + x);
                }
                writer.write('\n');
            }
            assertEquals(recorder.frames.size(), frame);
        }
        var rmse = new LinkedHashMap<String, Double>();
        sumSquares.forEach((key, value) -> rmse.put(key, Math.sqrt(value / expected.size())));
        var relativeValues=new LinkedHashMap<String,Double>();
        relative.forEach((key,value)->relativeValues.put(key,value.value()));
        var normalizedVoltage = new LinkedHashMap<String, Double>();
        for (String channel : List.of("V_BUS1", "V_BUS2")) {
            double minimum = expected.stream().mapToDouble(row -> row.get(channel)).min().orElseThrow();
            double maximumReference = expected.stream().mapToDouble(row -> row.get(channel)).max().orElseThrow();
            double range = maximumReference - minimum;
            if (range == 0.) {
                normalizedVoltage.put(channel, 0.);
            } else {
                double nrmse = rmse.get(channel) / range;
                normalizedVoltage.put(channel, nrmse);
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.putAll(Map.of("status", "diagnostic-pending-gates", "maximum_error", maximum,
                "maximum_time_s", at, "rmse", rmse, "samples", expected.size(),
                "startup_policy", "Compare retained negative-time rows to the initialized snapshot; no negative-time integration",
                "voltage_range_nrmse", normalizedVoltage, "bus_frequency_filter_s", .04,
                "trace", trace.getFileName().toString(), "integration_method", staggered ? "STAGGERED_EULER" : "MODIFIED_EULER"));
        result.put("first_event_power_state_samples", firstEventSamples);
        result.put("relative_rmse",relativeValues);
        result.put("prefault_angle_maximum_degrees", prefaultAngleMaximum);
        result.put("exciter_local_substeps", staggered ? exciterSubsteps : 1);
        result.put("first_event_power_state_maximum", firstEventPowerStateError);
        boolean longRun = profile.path("duration").asDouble(0.) >= 10.;
        double firstWashoutPrefault = Math.abs(expected.getFirst().get("PSS_STATE_3"));
        if (longRun) {
            assertTrue(firstWashoutPrefault > 0. && Double.isFinite(firstWashoutPrefault),
                    "The long-run second-path washout requires a nonzero reference prefault state");
            result.put("second_path_first_washout_prefault", firstWashoutPrefault);
            result.put("second_path_first_washout_peak_fraction", maximum.get("PSS_STATE_3") / firstWashoutPrefault);
            result.put("second_path_first_washout_rmse_fraction", rmse.get("PSS_STATE_3") / firstWashoutPrefault);
        }
        Path diagnostic=output.resolve(root.getFileName()+".json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(diagnostic.toFile(),result);
        for(var entry:normalizedVoltage.entrySet())
            assertTrue(relativeValues.get(entry.getKey())<RelativeRmse.ONE_PERCENT,
                    root.getFileName()+" "+entry.getKey()+" relative RMSE="+relativeValues.get(entry.getKey()));
        if (longRun) {
            assertTrue(relativeValues.get("PSS_STATE_3")<RelativeRmse.ONE_PERCENT,
                    "Second-path first-washout relative RMSE="+relativeValues.get("PSS_STATE_3"));
        }
        assertTrue(relativeValues.get("MACH_ANGLE")<RelativeRmse.ONE_PERCENT,
                "Rotor-angle relative RMSE="+relativeValues.get("MACH_ANGLE"));
        assertTrue(prefaultAngleMaximum < .002, "Prefault maximum rotor-angle error in degrees");
        if (staggered && event.equals("fault") && pss.getData().getIcs1() == 6 && pss.getData().getIcs2() == 3) {
            assertEquals(1, firstEventSamples, "Retain the first postfault sample for the independent input-history gate");
            assertTrue(firstEventPowerStateError < 3e-7, "A sampled derivative cannot suppress power-input event averaging");
        }
        if (profile.get("variant").asText().equals("limits")) {
            assertEquals(pss.vstmin, minimumOutput, 1e-12, "Lower output bound must be active");
            assertEquals(pss.vstmax, maximumOutput, 1e-12, "Upper output bound must be active");
        }
        System.out.println("PSS2B coupled " + root.getFileName() + " max V=" + maximum.get("V_BUS1")
                + " angle=" + maximum.get("MACH_ANGLE") + " output=" + maximum.get("VOTHSG"));
        result.put("status","boundary-accepted-controller-states-diagnostic");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(diagnostic.toFile(),result);
        return result;
    }

    private static final class Recorder extends DefaultDynamicSimulationSolver {
        final Recording recording;
        Recorder(DynamicSimuAlgorithm algorithm, Recording recording) {
            super(algorithm); this.recording = recording; converge_tol = NETWORK_TOLERANCE;
        }
        @Override public void beforeStep(double time) throws DStabSimuException {
            recording.beforeStep(time); super.beforeStep(time);
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            if (solved && recording.enabled && "INITIAL".equals(networkSolutionStage))
                recording.frames.add(recording.capture(dstabAlgo.getSimuTime(), "GRID"));
            return solved;
        }
    }

    private static final class StaggeredRecorder extends StaggeredEulerSolver {
        final Recording recording;
        StaggeredRecorder(DynamicSimuAlgorithm algorithm, Recording recording) {
            super(algorithm); this.recording = recording; setReuseNetworkEndpoint(false);
            setNetworkSolutionTolerance(NETWORK_TOLERANCE);
        }
        @Override public void beforeStep(double time) throws DStabSimuException {
            if (recording.beforeStep(time)) notifyDiscontinuity();
            super.beforeStep(time);
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved = super.networkSolutionStep();
            if (solved && recording.enabled && "INITIAL".equals(networkSolutionStage))
                recording.frames.add(recording.capture(dstabAlgo.getSimuTime(), "GRID"));
            return solved;
        }
    }

    private static final class Recording {
        final DynamicSimuAlgorithm dstabAlgo;
        final List<Frame> frames = new ArrayList<>(); final boolean referenceStep;
        boolean enabled; DoubleSupplier[] coordinates;
        Recording(DynamicSimuAlgorithm algorithm, boolean referenceStep) { dstabAlgo = algorithm; this.referenceStep = referenceStep; }
        boolean beforeStep(double time) {
            boolean changed = false;
            if (enabled && (Math.abs(time - .05) < 1e-9 || Math.abs(time - .1) < 1e-9)) {
                frames.add(capture(time, "INTERVAL_PRE"));
                if (referenceStep && Math.abs(time - .05) < 1e-9) {
                    var exciter = (com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController)
                            dstabAlgo.getNetwork().getMachine("Bus1-mach1").getExciter();
                    exciter.setRefPoint(exciter.getRefPoint() + .01);
                    changed = true;
                }
            }
            return changed;
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
            var rotor = (org.interpss.dstab.mach.IeeeVcRoundRotorMachine) machine;
            values.put("EP_Q", rotor.getEq1()); values.put("EP_D", rotor.getEd1());
            values.put("PSIK_D", rotor.getPsikd()); values.put("PSIK_Q", rotor.getPsikq());
            var exciter = (org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter) machine.getExciter();
            var exciterStates = exciter.getNativeStateSnapshot();
            String[] exciterKeys = {"Measured voltage", "First lead-lag", "Second lead-lag", "Regulator output", "Feedback"};
            String[] exciterChannels = {"EXC_VMEAS", "EXC_LL1", "EXC_LL2", "EXC_VA", "EXC_FEEDBACK"};
            for (int i = 0; i < exciterKeys.length; i++) values.put(exciterChannels[i], exciterStates.get(exciterKeys[i]));
            values.put("VOTHSG", pss.getOutput(machine));
            for (int i = 0; i < coordinates.length; i++) values.put("PSS_STATE_" + i, coordinates[i].getAsDouble());
            values.put("PSS_MEMORY_1", pss.getInput1VoltageMemory()); values.put("PSS_DVDT_1", pss.getInput1VoltageDerivative());
            values.put("PSS_MEMORY_2", pss.getInput2VoltageMemory()); values.put("PSS_DVDT_2", pss.getInput2VoltageDerivative());
            return new Frame(time, kind, values);
        }
    }
}
