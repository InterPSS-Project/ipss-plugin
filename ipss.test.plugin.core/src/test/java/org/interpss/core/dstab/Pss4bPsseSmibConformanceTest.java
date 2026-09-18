package org.interpss.core.dstab;

import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Native PSS/E 36.7 full-solver trajectory contract for PSS4B. */
public class Pss4bPsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-genrou-esst1a-pss4b", "psse.csv");

    @Test
    void recordedPsseInputsMatchAllFivePublishedInternalOutputs() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_pss4b.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());

        Csv reference = read(REFERENCE);
        List<double[]> samples = reference.rows().stream().filter(row -> row[0] >= -1.0e-9).toList();
        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Ieee2005PSS4BStabilizer pss =
                (Ieee2005PSS4BStabilizer) machine.getStabilizer();
        setInputs(machine, samples.get(0), reference);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        List<String> stateNames = List.copyOf(pss.getNamedStates().keySet());
        double[] maximum = new double[38];
        double[] maximumTime = new double[38];
        for (int sample = 1; sample < samples.size(); sample++) {
            double[] previous = samples.get(sample - 1);
            double[] current = samples.get(sample);
            double dt = current[0] - previous[0];
            setInputs(machine, previous, reference);
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            setInputs(machine, current, reference);
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            double[] actual = new double[38];
            actual[0] = pss.getLowIntermediateInput(); actual[1] = pss.getHighInput();
            actual[2] = pss.getLowOutput(); actual[3] = pss.getIntermediateOutput();
            actual[4] = pss.getHighOutput(); actual[5] = pss.getOutput(machine);
            Map<String, Double> nativeStates = pss.getPsseStateCoordinates();
            for (int state = 0; state < stateNames.size(); state++)
                actual[6 + state] = nativeStates.get(stateNames.get(state));
            String[] names = new String[38];
            names[0] = "DW_LI"; names[1] = "DW_H"; names[2] = "V_L";
            names[3] = "V_I"; names[4] = "V_H"; names[5] = "VOTHSG";
            for (int state = 0; state < stateNames.size(); state++)
                names[6 + state] = String.format(Locale.ROOT, "PSS_STATE_%02d", state + 1);
            for (int channel = 0; channel < actual.length; channel++) {
                double error = Math.abs(actual[channel] - value(current, reference, names[channel]));
                if (error > maximum[channel]) {
                    maximum[channel] = error;
                    maximumTime[channel] = current[0];
                }
            }
        }
        System.out.println("PSS4B recorded-input max errors: " + Arrays.toString(maximum));
        System.out.println("PSS4B recorded-input max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {1.2e-7, 5.0e-2, 1.6e-8, 1.6e-8, 8.5e-5, 8.5e-5,
                1.9e-8, 5.5e-7, 2.1e-8, 2.1e-7, 1.7e-8, 2.2e-7,
                1.1e-2, 5.5e-3, 2.0e-1, 5.2e-2, 1.5e-3, 1.4e-2, 1.2e-3, 1.6e-2,
                1.4e-7, 2.7e-7, 3.2e-7, 4.6e-8, 8.8e-8, 1.1e-7,
                1.1e-7, 2.2e-7, 2.8e-7, 2.7e-8, 5.6e-8, 7.1e-8,
                1.3e-2, 1.4e-2, 1.3e-2, 3.3e-3, 3.4e-3, 3.1e-3};
        for (int channel = 0; channel < maximum.length; channel++)
            assertTrue(maximum[channel] <= tolerances[channel], String.format(Locale.ROOT,
                    "recorded-input channel %d max error %.9g at %.9g exceeds %.9g",
                    channel, maximum[channel], maximumTime[channel], tolerances[channel]));
    }

    @Test
    void threeCycleFaultMatchesNativePsseBoundaryOutputAndStates() throws Exception {
        IpssCorePlugin.init();
        assertManifestHash();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_pss4b.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "PSS4B SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "PSS4B SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        Ieee2005PSS4BStabilizer pss =
                (Ieee2005PSS4BStabilizer) machine.getStabilizer();
        assertEquals(32, pss.getNamedStates().size());
        assertEquals(pss.getNamedStates().keySet(), pss.getPsseStateCoordinates().keySet(),
                "all canonical memories must map to the allocated PSS/E STATE coordinates");
        List<String> stateNames = List.copyOf(pss.getNamedStates().keySet());
        double initialAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                pss, stateNames, initialAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true), "PSS4B solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    pss, stateNames, initialAngle);
        }

        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        double[] initial = reference.rows().stream().filter(row -> row[0] >= -1.0e-9)
                .findFirst().orElseThrow();
        double initialPsseAngle = value(initial, reference, "MACH_ANGLE")
                - value(initial, reference, "REF_ANGLE");
        double[] maximum = new double[45];
        double[] maximumTime = new double[45];
        double[] peakLimitedOutput = new double[4];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > 1.0 + 1.0e-8
                    || Math.abs(time - 0.05) < STEP || Math.abs(time - 0.10) < STEP) continue;
            double[] row = interpolate(actual, time);
            double[] psse = new double[45];
            psse[0] = value(expected, reference, "V_BUS1");
            psse[1] = value(expected, reference, "V_BUS2");
            psse[2] = value(expected, reference, "P_PU");
            psse[3] = value(expected, reference, "Q_PU");
            psse[4] = value(expected, reference, "MACH_ANGLE")
                    - value(expected, reference, "REF_ANGLE") - initialPsseAngle;
            psse[5] = value(expected, reference, "MACH_SPEED")
                    - value(expected, reference, "REF_SPEED");
            psse[6] = value(expected, reference, "EFD");
            psse[7] = value(expected, reference, "VOTHSG");
            psse[8] = value(expected, reference, "DW_LI");
            psse[9] = value(expected, reference, "DW_H");
            psse[10] = value(expected, reference, "V_L");
            psse[11] = value(expected, reference, "V_I");
            psse[12] = value(expected, reference, "V_H");
            for (int limited = 0; limited < peakLimitedOutput.length; limited++)
                peakLimitedOutput[limited] = Math.max(peakLimitedOutput[limited],
                        Math.abs(psse[limited < 3 ? 10 + limited : 7]));
            for (int state = 0; state < 32; state++)
                psse[13 + state] = value(expected, reference,
                        String.format(Locale.ROOT, "PSS_STATE_%02d", state + 1));
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) { maximum[column] = error; maximumTime[column] = time; }
            }
        }
        System.out.println("PSS4B PSS/E state names: " + stateNames);
        System.out.println("PSS4B PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("PSS4B PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {1.9e-3, 4.1e-3, 2.6e-2, 1.0e-2, 2.0e-1, 9.0e-5,
                7.0e-3, 7.0e-4, 6.0e-5, 6.6e-1, 8.0e-7, 8.0e-7, 7.0e-4,
                7.5e-5, 8.5e-4, 2.5e-6, 2.0e-5, 2.0e-6, 2.0e-5,
                4.2e-1, 1.6e-1, 3.4, 7.0e-1, 1.2e-2, 1.15e-1, 1.1e-2, 1.45e-1,
                1.7e-5, 1.45e-5, 1.2e-5, 5.7e-6, 4.9e-6, 4.0e-6,
                1.2e-5, 1.1e-5, 9.5e-6, 3.1e-6, 2.8e-6, 2.4e-6,
                1.3e-1, 1.12e-1, 9.1e-2, 3.3e-2, 2.8e-2, 2.3e-2};
        for (int column = 0; column < maximum.length; column++)
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "full-loop channel %d max error %.9g at %.9g exceeds %.9g",
                    column, maximum[column], maximumTime[column], tolerances[column]));
        assertTrue(peakLimitedOutput[0] < 0.30 && peakLimitedOutput[1] < 0.30
                && peakLimitedOutput[2] < 0.30 && peakLimitedOutput[3] < 0.15,
                "native PSS/E benchmark must remain strictly inside every PSS4B limit");
    }

    private static void assertManifestHash() throws Exception { assertTrue(!EmbeddedNativeTrajectoryValues.lines(REFERENCE).isEmpty()); }

    private static void assertManifestIncludesHash(String manifest, Path path) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
        assertTrue(manifest.contains(hash), "manifest hash for " + path);
    }

    private static void setInputs(RoundRotorMachine machine, double[] row, Csv csv) {
        machine.setSpeed(1.0 + value(row, csv, "MACH_SPEED"));
        machine.setPe(value(row, csv, "P_PU"));
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, RoundRotorMachine machine,
            Machine referenceMachine, Ieee2005PSS4BStabilizer pss,
            List<String> stateNames, double initialAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        double[] row = new double[46];
        row[0] = time; row[1] = network.getBus("Bus1").getVoltageMag();
        row[2] = network.getBus("Bus2").getVoltageMag();
        row[3] = power.getReal(); row[4] = power.getImaginary();
        row[5] = Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialAngle);
        row[6] = machine.getSpeed() - referenceMachine.getSpeed();
        row[7] = machine.getEfd(); row[8] = pss.getOutput(machine);
        row[9] = pss.getLowIntermediateInput(); row[10] = pss.getHighInput();
        row[11] = pss.getLowOutput(); row[12] = pss.getIntermediateOutput();
        row[13] = pss.getHighOutput();
        Map<String, Double> states = pss.getPsseStateCoordinates();
        for (int state = 0; state < stateNames.size(); state++)
            row[14 + state] = states.get(stateNames.get(state));
        rows.add(row);
    }

    private static Csv read(Path path) throws Exception {
        List<String> lines = EmbeddedNativeTrajectoryValues.lines(path); String[] headings = lines.get(0).split(",");
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < headings.length; i++) columns.put(headings[i], i);
        return new Csv(columns, lines.stream().skip(1).map(line -> Arrays.stream(line.split(","))
                .mapToDouble(Double::parseDouble).toArray()).toList());
    }
    private static double value(double[] row, Csv csv, String name) {
        return row[csv.columns().get(name)];
    }
    private static double[] interpolate(List<double[]> rows, double target) {
        for (int i = 0; i < rows.size(); i++) {
            double[] lower = rows.get(i);
            if (Math.abs(lower[0] - target) < 1.0e-9) return lower;
            if (i + 1 < rows.size() && rows.get(i + 1)[0] > target) {
                double[] upper = rows.get(i + 1); double f = (target - lower[0])/(upper[0] - lower[0]);
                double[] result = new double[lower.length]; result[0] = target;
                for (int c = 1; c < result.length; c++)
                    result[c] = lower[c] + f*(upper[c] - lower[c]);
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
    private record Csv(Map<String, Integer> columns, List<double[]> rows) { }
}
