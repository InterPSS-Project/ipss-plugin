package org.interpss.core.dstab;

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
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Native PSS/E 36.7 full-solver and recorded-input contract for PSS3B. */
public class Pss3bPsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-genrou-esst1a-pss3b", "psse.csv");
    private static final List<String> STATE_NAMES = List.of(
            "input1Transducer", "input2Transducer", "input1Washout",
            "input2Washout", "mainWashout", "notch1State1",
            "notch1State2", "notch2State1", "notch2State2");

    @Test
    void threeCycleFaultMatchesNativePsseBoundaryOutputAndNineStates() throws Exception {
        IpssCorePlugin.init();
        assertManifestHash();
        var context = load();
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "PSS3B SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "PSS3B SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        Ieee2005PSS3BStabilizer pss =
                (Ieee2005PSS3BStabilizer) machine.getStabilizer();
        assertEquals(Set.copyOf(STATE_NAMES), pss.getNamedStates().keySet());
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                pss, initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "PSS3B solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    pss, initialRelativeAngle);
        }

        Csv reference = read(REFERENCE);
        assertEquals(2005, reference.rows().size(), "PSS/E raw samples");
        double[] initial = reference.rows().stream().filter(row -> row[0] >= -1.0e-9)
                .findFirst().orElseThrow();
        double initialPsseAngle = value(initial, reference, "MACH_ANGLE")
                - value(initial, reference, "REF_ANGLE");
        double[] maximum = new double[17];
        double[] maximumTime = new double[17];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > 1.0 + 1.0e-8
                    || Math.abs(time - 0.05) < STEP
                    || Math.abs(time - 0.10) < STEP) continue;
            double[] row = interpolate(actual, time);
            double[] psse = new double[17];
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
            for (int state = 0; state < 9; state++) {
                psse[8 + state] = value(expected, reference,
                        String.format(Locale.ROOT, "PSS_STATE_%02d", state + 1));
            }
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("PSS3B PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("PSS3B PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {1.9e-3, 4.1e-3, 2.6e-2, 1.0e-2,
                0.20, 9.0e-5, 9.0e-3, 1.4e-3,
                4.0e-3, 3.0e-4, 1.3e-4, 1.7e-5, 4.5e-4,
                6.8e-4, 7.2e-3, 1.7e-4, 1.7e-3};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "relative angle",
                "relative speed", "EFD", "stabilizer output",
                "input 1 transducer", "input 2 transducer", "input 1 washout",
                "input 2 washout", "main washout", "notch 1 integrated",
                "notch 1 derivative", "notch 2 integrated", "notch 2 derivative"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    @Test
    void recordedPssePowerAndSpeedSignalsMatchPhysicalOutput() throws Exception {
        IpssCorePlugin.init();
        var context = load();
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "PSS3B play-in load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization(), "PSS3B play-in initialization");

        Machine machine = network.getMachine("Bus1-mach1");
        Ieee2005PSS3BStabilizer pss =
                (Ieee2005PSS3BStabilizer) machine.getStabilizer();
        Csv reference = read(REFERENCE);
        List<double[]> samples = reference.rows().stream()
                .filter(row -> row[0] >= -1.0e-9 && row[0] <= 1.0 + STEP)
                .toList();
        double[] initial = samples.get(0);
        machine.setPe(value(initial, reference, "P_PU"));
        machine.setSpeed(1.0 + value(initial, reference, "MACH_SPEED"));
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        double maximum = Math.abs(pss.getOutput(machine)
                - value(initial, reference, "VOTHSG"));
        double maximumTime = initial[0];
        double[] stateMaximum = new double[9];
        double[] stateMaximumTime = new double[9];
        double previousTime = initial[0];
        double[] previousSample = initial;
        for (double[] sample : samples.subList(1, samples.size())) {
            double time = sample[0];
            double dt = (time - previousTime) / 2.0;
            for (int substep = 1; substep <= 2; substep++) {
                double fraction = substep / 2.0;
                machine.setPe(interpolateSignal(previousSample, sample, reference,
                        "P_PU", fraction));
                machine.setSpeed(1.0 + interpolateSignal(previousSample, sample, reference,
                        "MACH_SPEED", fraction));
                assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
                assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            }
            if (Math.abs(time - 0.05) >= STEP && Math.abs(time - 0.10) >= STEP) {
                double error = Math.abs(pss.getOutput(machine)
                        - value(sample, reference, "VOTHSG"));
                if (error > maximum) {
                    maximum = error;
                    maximumTime = time;
                }
                Map<String, Double> states = pss.getNamedStates();
                for (int state = 0; state < STATE_NAMES.size(); state++) {
                    double stateError = Math.abs(states.get(STATE_NAMES.get(state))
                            - value(sample, reference, String.format(Locale.ROOT,
                                    "PSS_STATE_%02d", state + 1)));
                    if (stateError > stateMaximum[state]) {
                        stateMaximum[state] = stateError;
                        stateMaximumTime[state] = time;
                    }
                }
            }
            previousTime = time;
            previousSample = sample;
        }
        System.out.printf(Locale.ROOT,
                "PSS3B PSS/E recorded-input output max error %.9g at %.9g s%n",
                maximum, maximumTime);
        System.out.println("PSS3B PSS/E recorded-input state max errors: "
                + Arrays.toString(stateMaximum));
        System.out.println("PSS3B PSS/E recorded-input state max-error times: "
                + Arrays.toString(stateMaximumTime));
        assertTrue(maximum <= 8.0e-5, String.format(Locale.ROOT,
                "PSS3B recorded-input error %.9g at %.9g exceeds %.9g",
                maximum, maximumTime, 8.0e-5));
        double[] stateTolerances = {2.0e-4, 4.0e-6, 4.0e-6, 4.0e-7,
                1.1e-5, 1.6e-5, 1.8e-4, 4.0e-6, 4.2e-5};
        for (int state = 0; state < STATE_NAMES.size(); state++) {
            assertTrue(stateMaximum[state] <= stateTolerances[state],
                    String.format(Locale.ROOT,
                            "%s recorded-input max error %.9g at %.9g exceeds %.9g",
                            STATE_NAMES.get(state), stateMaximum[state],
                            stateMaximumTime[state], stateTolerances[state]));
        }
    }

    private static com.interpss.simu.SimuContext load() throws Exception {
        return new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_pss3b.dyr").toString());
    }

    private static void assertManifestHash() throws Exception {
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(REFERENCE)));
        assertTrue(Files.readString(manifest).contains(hash),
                "PSS/E reference CSV hash is absent from its manifest");
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine,
            Ieee2005PSS3BStabilizer pss, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        double[] row = new double[18];
        row[0] = time;
        row[1] = network.getBus("Bus1").getVoltageMag();
        row[2] = network.getBus("Bus2").getVoltageMag();
        row[3] = power.getReal();
        row[4] = power.getImaginary();
        row[5] = Math.toDegrees(machine.getAngle() - referenceMachine.getAngle()
                - initialRelativeAngle);
        row[6] = machine.getSpeed() - referenceMachine.getSpeed();
        row[7] = machine.getEfd();
        row[8] = pss.getOutput(machine);
        Map<String, Double> states = pss.getNamedStates();
        for (int state = 0; state < STATE_NAMES.size(); state++) {
            row[9 + state] = states.get(STATE_NAMES.get(state));
        }
        rows.add(row);
    }

    private static double interpolateSignal(double[] lower, double[] upper,
            Csv csv, String name, double fraction) {
        return value(lower, csv, name)
                + fraction * (value(upper, csv, name) - value(lower, csv, name));
    }

    private static Csv read(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path);
        String[] headings = lines.get(0).split(",");
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int index = 0; index < headings.length; index++) columns.put(headings[index], index);
        List<double[]> rows = lines.stream().skip(1).map(line -> Arrays.stream(line.split(","))
                .mapToDouble(Double::parseDouble).toArray()).toList();
        return new Csv(columns, rows);
    }

    private static double value(double[] row, Csv csv, String name) {
        return row[csv.columns().get(name)];
    }

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int index = 0; index < rows.size(); index++) {
            double[] lower = rows.get(index);
            if (Math.abs(lower[0] - target) < 1.0e-9) return lower;
            if (index + 1 < rows.size() && rows.get(index + 1)[0] > target) {
                double[] upper = rows.get(index + 1);
                double fraction = (target - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length];
                result[0] = target;
                for (int column = 1; column < result.length; column++) {
                    result[column] = lower[column]
                            + fraction * (upper[column] - lower[column]);
                }
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }

    private record Csv(Map<String, Integer> columns, List<double[]> rows) { }
}
