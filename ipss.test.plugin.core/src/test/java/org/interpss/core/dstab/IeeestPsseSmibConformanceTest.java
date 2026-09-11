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
import org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Native PSS/E 36.7 full-solver and play-in contract for IEEEST/PSS1A. */
public class IeeestPsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-genrou-esst1a-ieeest", "psse.csv");

    @Test
    void threeCycleFaultMatchesNativePsseBoundaryAndPhysicalOutput() throws Exception {
        IpssCorePlugin.init();
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(REFERENCE)));
        assertTrue(Files.readString(manifest).contains(hash),
                "PSS/E reference CSV hash is absent from its manifest");

        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_ieeest.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "IEEEST SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "IEEEST SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        IeeestStabilizer pss = (IeeestStabilizer) machine.getStabilizer();
        assertEquals(Set.of("secondOrderLag", "secondOrderLeadLag", "leadLag1",
                "leadLag2", "washout"), pss.getNamedStates().keySet());
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                pss, initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "IEEEST solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    pss, initialRelativeAngle);
        }

        Csv reference = read(REFERENCE);
        assertEquals(2005, reference.rows().size(), "PSS/E raw samples");
        double[] initial = reference.rows().stream().filter(row -> row[0] >= -1.0e-9)
                .findFirst().orElseThrow();
        double initialPsseAngle = value(initial, reference, "MACH_ANGLE")
                - value(initial, reference, "REF_ANGLE");
        double[] maximum = new double[8];
        double[] maximumTime = new double[8];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > 1.0 + 1.0e-8
                    || Math.abs(time - 0.05) < STEP
                    || Math.abs(time - 0.10) < STEP) continue;
            double[] row = interpolate(actual, time);
            double[] psse = {
                    value(expected, reference, "V_BUS1"),
                    value(expected, reference, "V_BUS2"),
                    value(expected, reference, "P_PU"),
                    value(expected, reference, "Q_PU"),
                    value(expected, reference, "MACH_ANGLE")
                            - value(expected, reference, "REF_ANGLE") - initialPsseAngle,
                    value(expected, reference, "MACH_SPEED")
                            - value(expected, reference, "REF_SPEED"),
                    value(expected, reference, "EFD"),
                    value(expected, reference, "VOTHSG")};
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("IEEEST PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("IEEEST PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {1.9e-3, 4.1e-3, 2.6e-2, 1.0e-2,
                0.20, 9.0e-5, 2.2e-2, 4.9e-3};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "relative angle",
                "relative speed", "EFD", "stabilizer output"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    @Test
    void recordedPsseSpeedSignalMatchesIeeestPhysicalOutput() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_ieeest.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "IEEEST play-in load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization(), "IEEEST play-in initialization");

        Machine machine = network.getMachine("Bus1-mach1");
        IeeestStabilizer pss = (IeeestStabilizer) machine.getStabilizer();
        Csv reference = read(REFERENCE);
        List<double[]> samples = reference.rows().stream()
                .filter(row -> row[0] >= -1.0e-9 && row[0] <= 1.0 + STEP)
                .toList();
        double[] initial = samples.get(0);
        machine.setSpeed(1.0 + value(initial, reference, "MACH_SPEED"));
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        double maximum = Math.abs(pss.getOutput(machine)
                - value(initial, reference, "VOTHSG"));
        double maximumTime = initial[0];
        double previousTime = initial[0];
        double[] previousSample = initial;
        for (double[] sample : samples.subList(1, samples.size())) {
            double time = sample[0];
            double dt = (time - previousTime) / 2.0;
            for (int substep = 1; substep <= 2; substep++) {
                double fraction = substep / 2.0;
                machine.setSpeed(1.0 + value(previousSample, reference, "MACH_SPEED")
                        + fraction * (value(sample, reference, "MACH_SPEED")
                                - value(previousSample, reference, "MACH_SPEED")));
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
            }
            previousTime = time;
            previousSample = sample;
        }
        System.out.printf(Locale.ROOT,
                "IEEEST PSS/E recorded-signal output max error %.9g at %.9g s%n",
                maximum, maximumTime);
        assertTrue(maximum <= 2.7e-4, String.format(Locale.ROOT,
                "IEEEST recorded-signal max error %.9g at %.9g exceeds %.9g",
                maximum, maximumTime, 2.7e-4));
    }

    @Test
    void recordedPsseSpeedSignalMatchesPublishedPss1aEquivalent() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_pss1a.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "PSS1A play-in load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization(), "PSS1A play-in initialization");

        Machine machine = network.getMachine("Bus1-mach1");
        Ieee1992PSS1AStabilizer pss =
                (Ieee1992PSS1AStabilizer) machine.getStabilizer();
        Csv reference = read(REFERENCE);
        List<double[]> samples = reference.rows().stream()
                .filter(row -> row[0] >= -1.0e-9 && row[0] <= 1.0 + STEP)
                .toList();
        double[] initial = samples.get(0);
        machine.setSpeed(1.0 + value(initial, reference, "MACH_SPEED"));
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        double maximum = Math.abs(pss.getOutput(machine)
                - value(initial, reference, "VOTHSG"));
        double maximumTime = initial[0];
        double previousTime = initial[0];
        double[] previousSample = initial;
        for (double[] sample : samples.subList(1, samples.size())) {
            double time = sample[0];
            double dt = (time - previousTime) / 2.0;
            for (int substep = 1; substep <= 2; substep++) {
                double fraction = substep / 2.0;
                machine.setSpeed(1.0 + value(previousSample, reference, "MACH_SPEED")
                        + fraction * (value(sample, reference, "MACH_SPEED")
                                - value(previousSample, reference, "MACH_SPEED")));
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
            }
            previousTime = time;
            previousSample = sample;
        }
        System.out.printf(Locale.ROOT,
                "PSS1A-equivalent PSS/E recorded-signal output max error %.9g at %.9g s%n",
                maximum, maximumTime);
        assertTrue(maximum <= 2.7e-4, String.format(Locale.ROOT,
                "PSS1A-equivalent recorded-signal max error %.9g at %.9g exceeds %.9g",
                maximum, maximumTime, 2.7e-4));
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine,
            IeeestStabilizer pss, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal(), power.getImaginary(),
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle()
                        - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEfd(),
                pss.getOutput(machine)});
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
