package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.mach.Machine;

/** Recorded-boundary replay localizes state coordinates independently of network feedback. */
public class Pss2bReferenceReplayTest {
    @TempDir Path staged;
    private Pss2bReferenceFixture references;

    @Test void derivativeReplayReportsBothIndependentStepSizes() throws Exception {
        for (int slot : new int[]{1, 2}) {
            double[] coarse = replay(caseDirectory("pss2b-native-s6-i" + slot + "-r0-fault-standard-h0p0005-a-20261030"));
            double[] fine = replay(caseDirectory("pss2b-native-s6-i" + slot + "-r0-fault-standard-h0p00025-a-20261030"));
            for (int channel = 0; channel < fine.length; channel++)
                assertTrue(fine[channel] <= .75 * coarse[channel] + 1e-8, "Step-halving convergence, slot=" + slot + " channel=" + channel);
        }
    }

    @Test void publishedStatesReconstructTheOutputForEveryCapturedProfile() throws Exception {
        IpssCorePlugin.init();
        var archive = referenceArchive();
        com.fasterxml.jackson.databind.JsonNode matrix;
        try (var zip = new java.util.zip.ZipFile(archive.toFile());
                var stream = zip.getInputStream(zip.getEntry("capture-matrix.json"))) {
            matrix = new com.fasterxml.jackson.databind.ObjectMapper().readTree(stream);
        }
        for (var entry : matrix) {
            if (!entry.get("repeat").asText().equals("a")) continue;
            Path root = caseDirectory("pss2b-native-" + entry.get("tag").asText());
            reconstructPublishedOutput(root);
        }
    }

    static void reconstructPublishedOutput(Path root) throws Exception {
        reconstructPublishedOutput(root, false);
    }

    static void reconstructPublishedOutput(Path root, boolean precedingBypassCoordinates) throws Exception {
        assertTrue(Files.isRegularFile(root.resolve("manifest.json")), "Missing required capture " + root.getFileName());
        var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(root.resolve("manifest.json").toFile());
        var icons = manifest.get("components").get("STAB").get("actual_icons");
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(), root.resolve("dynamic.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().setTolerance(1e-10); assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var machine = context.getDStabilityNet().getMachine("Bus1-mach1");
        var pss = (Ieee1992PSS2BStabilizer) machine.getStabilizer();
        useCapturedCoefficients(pss, manifest);
        pss.configureIntegrationStep(manifest.get("step_s").asDouble());
        assertTrue(pss.initStates(machine.getDStabBus(), machine)); pss.compileRuntimePlan();
        assertTrue(pss.hasCompiledStageEvaluator());
        if (precedingBypassCoordinates) {
            assertTrue(pss.tw1 > 0. && pss.tw3 > 0.,
                    "The retained sampled-path discriminator uses active first washouts");
        }
        Map<String, Double> previous = null;
        double previousAlgebraicOutput = 0.;
        for (var row : rows(root.resolve("native.csv"))) {
            impose(machine, row);
            pss.input1Signal = selected(pss.getData().getIcs1(), machine, row, 1,
                    icons.get(1).asInt() == 0 ? 1 : 2, pss.vsi1max, pss.vsi1min);
            pss.input2Signal = selected(pss.getData().getIcs2(), machine, row, 2,
                    icons.get(3).asInt() == 0 ? 1 : 2, pss.vsi2max, pss.vsi2min);
            if (precedingBypassCoordinates && previous != null) {
                // Reconstruct the prior published algebraic path value using current
                // differential storage. This is an observation discriminator, not a
                // fitted physical input or a production integration policy.
                if (pss.t6 == 0.) pss.input1Signal = previous.get("PSS_STATE_2")
                        + row.get("PSS_STATE_0") + (pss.tw2 > 0. ? row.get("PSS_STATE_1") : 0.);
                if (pss.t7 == 0.) pss.input2Signal = previous.get("PSS_STATE_5")
                        + row.get("PSS_STATE_3") + (pss.tw4 > 0. ? row.get("PSS_STATE_4") : 0.);
            }
            injectPublishedStates(pss, row);
            double expected = row.get("VOTHSG");
            if (pss.n == 0) {
                // Fully bypassed profiles publish the preceding controller-stage output,
                // while their allocated algebraic coordinates describe the current boundary.
                assertEquals(bypassedOutput(pss, previous == null ? row : previous), expected, 1e-7,
                        "Sampled output retains the preceding stage, including event duplicates");
                expected = bypassedOutput(pss, row);
            }
            double algebraicOutput = pss.getOutput(machine);
            if (previous != null && row.get("time_s").equals(previous.get("time_s")) && pss.n != 0) {
                // A zero-duration network refresh can update bypassed coordinates while
                // the sampled output still represents the preceding accepted evaluation.
                assertEquals(previous.get("VOTHSG"), row.get("VOTHSG"), 0., "Event-side published output is held");
                assertEquals(row.get("VOTHSG"), previousAlgebraicOutput, 1.5e-5,
                        "Event-side sample retains the preceding reconstructed output");
            } else {
                assertEquals(expected, algebraicOutput, 1.5e-5,
                        root.getFileName() + " algebraic output at " + row.get("time_s"));
            }
            previousAlgebraicOutput = algebraicOutput;
            previous = row;
        }
    }

    private static double bypassedOutput(Ieee1992PSS2BStabilizer pss, Map<String, Double> row) {
        assertEquals(0., pss.t6); assertEquals(0., pss.t7);
        assertEquals(0., pss.t2); assertEquals(0., pss.t4); assertEquals(0., pss.t11);
        double second = pss.ks2 * row.get("PSS_STATE_5");
        double output = pss.ks1 * (row.get("PSS_STATE_2") + pss.ks3 * second - pss.ks4 * second);
        return Math.max(pss.vstmin, Math.min(pss.vstmax, output));
    }

    @Test void multiSectionRampReplayUsesPublishedStorageCoordinates() throws Exception {
        double[] coarse = replay(caseDirectory("pss2b-native-s5-i1-r0-fault-ramp22-h0p0005-a-20261030"));
        double[] fine = replay(caseDirectory("pss2b-native-s5-i1-r0-fault-ramp22-h0p00025-a-20261030"));
        for (int channel = 0; channel < fine.length; channel++)
            assertTrue(fine[channel] <= .75 * coarse[channel] + 1e-7, "Multi-section step-halving convergence, channel=" + channel);
    }

    @Test void sampledDerivativeAndPowerPathsHaveDistinctIndependentEventHistories() throws Exception {
        for (String suffix : new String[]{"0p0005", "0p00025"}) {
            Path root = caseDirectory("pss2b-native-s6-i1-r0-fault-standard-h" + suffix + "-a-20261030");
            var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            double step = manifest.get("step_s").asDouble();
            var constants = manifest.get("components").get("STAB").get("actual_constants");
            var byTick = new java.util.TreeMap<Integer, List<Map<String, Double>>>();
            for (var row : rows(root.resolve("native.csv"))) byTick.computeIfAbsent((int) Math.round(row.get("time_s") / step),
                    ignored -> new ArrayList<>()).add(row);
            for (double event : new double[]{.05, .1}) {
                int tick = (int) Math.round(event / step);
                var previous = byTick.get(tick - 1).getLast();
                var right = byTick.get(tick).getLast();
                var next = byTick.get(tick + 1).getFirst();
                double powerPrevious = (previous.get("P_PU") - previous.get("PSS_STATE_3")) / constants.get(3).asDouble();
                double powerRight = (right.get("P_PU") - right.get("PSS_STATE_3")) / constants.get(3).asDouble();
                double averaged = right.get("PSS_STATE_3") + step * (.75 * powerRight + .25 * powerPrevious);
                double ordinary = right.get("PSS_STATE_3") + step * (1.5 * powerRight - .5 * powerPrevious);
                double averageError = Math.abs(next.get("PSS_STATE_3") - averaged);
                double ordinaryError = Math.abs(next.get("PSS_STATE_3") - ordinary);
                assertTrue(averageError < 3e-7, "Power-path event treatment, including independent binary32 state rounding");
                assertTrue(ordinaryError > 10 * averageError + 1e-7, "The power-path discriminator must reject unaveraged history");
                if (event == .1) {
                    double derivativePrevious = (previous.get("PSS_DVDT_1") - previous.get("PSS_STATE_0")) / constants.get(0).asDouble();
                    double derivativeRight = (right.get("PSS_DVDT_1") - right.get("PSS_STATE_0")) / constants.get(0).asDouble();
                    double kept = right.get("PSS_STATE_0") + step * (1.5 * derivativeRight - .5 * derivativePrevious);
                    double mixed = right.get("PSS_STATE_0") + step * (.75 * derivativeRight + .25 * derivativePrevious);
                    double keptError = Math.abs(next.get("PSS_STATE_0") - kept);
                    assertTrue(keptError < 1e-8, "Sampled derivative retains its two-step history across clearing");
                    assertTrue(Math.abs(next.get("PSS_STATE_0") - mixed) > 10 * keptError + 1e-9,
                            "The sampled derivative discriminator must reject whole-vector event averaging");
                }
            }
        }
    }

    private Path referenceArchive() throws Exception {
        if (references == null) references = new Pss2bReferenceFixture(staged);
        return references.archive();
    }

    private Path caseDirectory(String profile) throws Exception {
        referenceArchive();
        return references.stage(profile);
    }

    private static double selected(int code, Machine machine, Map<String, Double> row, int slot, int bus, double max, double min) {
        double input = switch (code) {
            case 1 -> machine.getSpeed() - 1.;
            case 2 -> row.get("FREQ" + bus);
            case 3 -> machine.getPe();
            case 4 -> machine.getPm() - machine.getPe();
            case 5 -> row.get("V_BUS" + bus);
            case 6 -> row.get("PSS_DVDT_" + slot);
            default -> throw new IllegalArgumentException("Unsupported input selector");
        };
        return Math.max(min, Math.min(max, input));
    }

    private static void injectPublishedStates(Ieee1992PSS2BStabilizer pss, Map<String, Double> row) {
        var children = List.of(pss.inputPath1, pss.inputPath2);
        for (int i = 0; i < 2; i++) {
            var child = children.get(i);
            block(child, "firstWashout", WashoutControlBlock.class).setStateX(row.get("PSS_STATE_" + (3 * i)) - child.getRefPoint());
            block(child, "secondWashout", WashoutControlBlock.class).setStateX(row.get("PSS_STATE_" + (3 * i + 1)));
            var delay = block(child, "delayBlock", DelayControlBlock.class);
            delay.setStateX(delay.getK() * row.get("PSS_STATE_" + (3 * i + 2)));
        }
        var inner = block(pss, "rampFilter", FilterNthOrderBlock.class).getIntegrationBlocks();
        for (int i = 0; i < inner.size(); i++) {
            var state = inner.get(i); double value = row.get("PSS_STATE_" + (6 + i));
            state.setStateX(state instanceof FilterControlBlock filter ? value / filter.getT2() : value);
        }
        for (int i = 0; i < 3; i++) {
            var state = block(pss, "leadLag" + (i + 1), FilterControlBlock.class);
            state.setStateX(state.getT2() == 0. ? 0. : row.get("PSS_STATE_" + (14 + i)) / state.getT2());
        }
    }

    static double[] replay(Path root) throws Exception {
        var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(root.resolve("manifest.json").toFile());
        double step = manifest.get("step_s").asDouble();
        var icons = manifest.get("components").get("STAB").get("actual_icons");
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(), root.resolve("dynamic.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().setTolerance(1e-10); assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(step);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        var machine = context.getDStabilityNet().getMachine("Bus1-mach1");
        var pss = (Ieee1992PSS2BStabilizer) machine.getStabilizer();
        useCapturedCoefficients(pss, manifest);
        assertTrue(algorithm.initialization());
        pss.compileRuntimePlan(); assertTrue(pss.hasCompiledStageEvaluator());
        var rows = rows(root.resolve("native.csv"));
        checkReportedPredictor(rows, manifest, step);
        var previous = rows.getFirst(); double previousTime = 0;
        StringBuilder csv = new StringBuilder("time_s,memory,derivative,output");
        for (int i = 0; i < 17; i++) csv.append(",state_").append(i);
        csv.append('\n');
        double[] maxima = new double[18];
        var coordinates = coordinates(pss);
        for (var row : rows) {
            double time = row.get("time_s"), dt = time > previousTime ? step : 0;
            impose(machine, previous);
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            impose(machine, row);
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            if (dt > 0) pss.acceptVoltageDerivativeEndpoint(time, dt);
            else if (time == previous.get("time_s")) pss.synchronizeVoltageDerivativeMemory();
            if (icons.get(0).asInt() == 6) {
                assertEquals(row.get("PSS_MEMORY_1"), pss.getInput1VoltageMemory(), 1e-10);
                assertEquals(row.get("PSS_DVDT_1"), pss.getInput1VoltageDerivative(), 2e-4);
            }
            if (icons.get(2).asInt() == 6) {
                assertEquals(row.get("PSS_MEMORY_2"), pss.getInput2VoltageMemory(), 1e-10);
                assertEquals(row.get("PSS_DVDT_2"), pss.getInput2VoltageDerivative(), 2e-4);
            }
            double output = pss.getOutput(machine);
            double[] states = java.util.Arrays.stream(coordinates).mapToDouble(java.util.function.DoubleSupplier::getAsDouble).toArray();
            maxima[17] = Math.max(maxima[17], Math.abs(output - row.get("VOTHSG")));
            csv.append(time).append(',').append(pss.getInput1VoltageMemory()).append(',')
                    .append(pss.getInput1VoltageDerivative()).append(',').append(output);
            for (int i = 0; i < states.length; i++) {
                assertTrue(Double.isFinite(states[i]));
                maxima[i] = Math.max(maxima[i], Math.abs(states[i] - row.get("PSS_STATE_" + i)));
                csv.append(',').append(states[i]);
            }
            csv.append('\n');
            previous = row; previousTime = Math.max(previousTime, time);
        }
        Path actual = Path.of("target/" + root.getFileName() + "-replay.csv"); Files.writeString(actual, csv);
        System.out.println("PSS2B " + root.getFileName() + " replay state/output maximum errors " + java.util.Arrays.toString(maxima));
        return maxima;
    }

    /** Compare equations using exactly the coefficients executed by the independent engine. */
    static void useCapturedCoefficients(Ieee1992PSS2BStabilizer pss,
            com.fasterxml.jackson.databind.JsonNode manifest) {
        var values = manifest.get("components").get("STAB").get("actual_constants");
        double[] c = new double[23]; for (int i = 0; i < c.length; i++) c[i] = values.get(i).asDouble();
        var d = pss.getData();
        d.setTw1(c[0]);d.setTw2(c[1]);d.setT6(c[2]);d.setTw3(c[3]);d.setTw4(c[4]);d.setT7(c[5]);
        d.setKs2(c[6]);d.setKs3(c[7]);d.setT8(c[8]);d.setT9(c[9]);d.setKs1(c[10]);
        d.setT1(c[11]);d.setT2(c[12]);d.setT3(c[13]);d.setT4(c[14]);d.setT10(c[15]);d.setT11(c[16]);
        d.setVsi1max(c[17]);d.setVsi1min(c[18]);d.setVsi2max(c[19]);d.setVsi2min(c[20]);
        d.setVstmax(c[21]);d.setVstmin(c[22]);
    }

    /** A recurrence check identifies the exported state stage without shifting any samples. */
    private static void checkReportedPredictor(List<Map<String, Double>> rows,
            com.fasterxml.jackson.databind.JsonNode manifest, double step) {
        var spec = manifest.get("components").get("STAB");
        for (int slot : new int[]{1, 2}) {
            if (spec.get("actual_icons").get(2 * (slot - 1)).asInt() != 6) continue;
            int state = 3 * (slot - 1), limit = 17 + 2 * (slot - 1);
            double tau = spec.get("actual_constants").get(state).asDouble();
            double max = spec.get("actual_constants").get(limit).asDouble();
            double min = spec.get("actual_constants").get(limit + 1).asDouble();
            for (int i = 2; i < rows.size(); i++) {
                var a = rows.get(i - 2); var b = rows.get(i - 1); var c = rows.get(i);
                // Duplicate event rows interrupt the recurrence; their memory is checked in replay.
                if (!(a.get("time_s") >= 0 && a.get("time_s") < b.get("time_s") && b.get("time_s") < c.get("time_s"))) continue;
                double fa = (Math.max(min, Math.min(max, a.get("PSS_DVDT_" + slot))) - a.get("PSS_STATE_" + state)) / tau;
                double fb = (Math.max(min, Math.min(max, b.get("PSS_DVDT_" + slot))) - b.get("PSS_STATE_" + state)) / tau;
                assertEquals(b.get("PSS_STATE_" + state) + step * (1.5 * fb - .5 * fa),
                        c.get("PSS_STATE_" + state), 2e-8, "Reported predictor at " + c.get("time_s"));
            }
        }
    }

    private static void impose(Machine machine, Map<String, Double> row) {
        machine.setSpeed(1 + row.get("MACH_SPEED")); machine.setPe(row.get("P_PU")); machine.setPm(row.get("PMECH"));
        for (int id : new int[]{1, 2}) {
            var bus = (BaseDStabBus<?, ?>) machine.getDStabBus().getNetwork().getBus("Bus" + id);
            bus.setVoltage(new Complex(row.get("V_BUS" + id), 0)); bus.setFreq(1 + row.get("FREQ" + id));
        }
    }

    static java.util.function.DoubleSupplier[] coordinates(Ieee1992PSS2BStabilizer pss) {
        java.util.function.DoubleSupplier[] result = new java.util.function.DoubleSupplier[17];
        java.util.function.DoubleSupplier unused = () -> 0.;
        java.util.Arrays.fill(result, unused);
        var first = pss.inputPath1; var second = pss.inputPath2;
        var w1 = block(first, "firstWashout", WashoutControlBlock.class);
        var w2 = block(first, "secondWashout", WashoutControlBlock.class);
        var w3 = block(second, "firstWashout", WashoutControlBlock.class);
        var w4 = block(second, "secondWashout", WashoutControlBlock.class);
        var d1 = block(first, "delayBlock", DelayControlBlock.class);
        var d2 = block(second, "delayBlock", DelayControlBlock.class);
        result[0] = () -> w1.getStateX() + first.getRefPoint();
        result[1] = () -> w2.getT() == 0 ? w2.getY() : w2.getStateX();
        result[2] = d1::getY;
        result[3] = () -> w3.getStateX() + second.getRefPoint();
        result[4] = () -> w4.getT() == 0 ? w4.getY() : w4.getStateX();
        // The published transducer coordinate precedes KS2; CML stores its gained output.
        assertNotEquals(0., d2.getK(), "Zero gain requires a separately observable transducer coordinate");
        result[5] = () -> d2.getY() / d2.getK();
        var inner = block(pss, "rampFilter", FilterNthOrderBlock.class).getIntegrationBlocks();
        assertTrue(inner.size() <= 8);
        for (int i = 0; i < inner.size(); i++) {
            var state = inner.get(i);
            result[6 + i] = state instanceof FilterControlBlock filter
                    ? () -> filter.getT2() * filter.getStateX() : state::getStateX;
        }
        for (int i = 0; i < 3; i++) {
            var state = block(pss, "leadLag" + (i + 1), FilterControlBlock.class);
            result[14 + i] = () -> state.getT2() * state.getStateX();
        }
        return result;
    }

    private static <T> T block(AbstractAnnotateController controller, String name, Class<T> type) {
        return type.cast(controller.getFieldWrapperList().stream().filter(w -> w.getFieldName().equals(name))
                .findFirst().orElseThrow().getField());
    }

    static List<Map<String, Double>> rows(Path path) throws Exception {
        var lines = Files.readAllLines(path); String[] names = lines.getFirst().split(",");
        List<Map<String, Double>> result = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.split(","); assertEquals(names.length, values.length);
            Map<String, Double> row = new LinkedHashMap<>();
            for (int i = 0; i < names.length; i++) { double value = Double.parseDouble(values[i]); assertTrue(Double.isFinite(value)); row.put(names[i], value); }
            result.add(row);
        }
        return result;
    }
}
