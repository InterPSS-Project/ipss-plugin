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
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Native PSS/E 36.7 full-solver trajectory contract for PSS6C. */
public class Pss6cPsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-genrou-esst1a-pss6c", "psse.csv");
    private static final List<String> STATE_NAMES = List.of(
            "input1Transducer1", "input1Transducer2", "input2Transducer",
            "input2Washout", "mainWashout", "canonicalIntegrator1",
            "canonicalIntegrator2", "canonicalIntegrator3",
            "canonicalIntegrator4", "compensatedFrequencyWashout");

    @Test
    void threeCycleFaultMatchesNativePsseBoundaryOutputAndTenStates() throws Exception {
        IpssCorePlugin.init();
        assertManifestHash();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_pss6c.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "PSS6C SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "PSS6C SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        Ieee2016PSS6CStabilizer pss =
                (Ieee2016PSS6CStabilizer) machine.getStabilizer();
        assertEquals(Set.copyOf(STATE_NAMES), pss.getNamedStates().keySet());
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                pss, initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "PSS6C solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    pss, initialRelativeAngle);
        }

        Csv reference = read(REFERENCE);
        assertEquals(2005, reference.rows().size(), "PSS/E raw samples");
        double[] initial = reference.rows().stream().filter(row -> row[0] >= -1.0e-9)
                .findFirst().orElseThrow();
        double initialPsseAngle = value(initial, reference, "MACH_ANGLE")
                - value(initial, reference, "REF_ANGLE");
        double[] maximum = new double[18];
        double[] maximumTime = new double[18];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > 1.0 + 1.0e-8
                    || Math.abs(time - 0.05) < STEP
                    || Math.abs(time - 0.10) < STEP) continue;
            double[] row = interpolate(actual, time);
            double[] psse = new double[18];
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
            for (int state = 0; state < STATE_NAMES.size(); state++) {
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
        System.out.println("PSS6C PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("PSS6C PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {1.9e-3, 4.1e-3, 2.6e-2, 1.0e-2,
                0.20, 9.0e-5, 4.5e-2, 1.1e-2,
                2.7e-4, 7.5e-5, 3.8e-3, 2.5e-4, 3.5e-4,
                1.7e-3, 1.8e-4, 5.7e-4, 3.8e-4, 0.23};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "relative angle",
                "relative speed", "EFD", "stabilizer output",
                "input 1 transducer 1", "input 1 transducer 2",
                "input 2 transducer", "input 2 washout", "main washout",
                "canonical integrator 1", "canonical integrator 2",
                "canonical integrator 3", "canonical integrator 4",
                "compensated-frequency washout"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    private static void assertManifestHash() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(REFERENCE)));
        assertTrue(Files.readString(REFERENCE.resolveSibling("manifest.json")).contains(hash));
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine,
            Ieee2016PSS6CStabilizer pss, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        double[] row = new double[19];
        row[0] = time;
        row[1] = network.getBus("Bus1").getVoltageMag();
        row[2] = network.getBus("Bus2").getVoltageMag();
        row[3] = power.getReal(); row[4] = power.getImaginary();
        row[5] = Math.toDegrees(machine.getAngle() - referenceMachine.getAngle()
                - initialRelativeAngle);
        row[6] = machine.getSpeed() - referenceMachine.getSpeed();
        row[7] = machine.getEfd(); row[8] = pss.getOutput(machine);
        Map<String, Double> states = pss.getNamedStates();
        for (int state = 0; state < STATE_NAMES.size(); state++)
            row[9 + state] = states.get(STATE_NAMES.get(state));
        rows.add(row);
    }

    private static Csv read(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path);
        String[] headings = lines.get(0).split(",");
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < headings.length; i++) columns.put(headings[i], i);
        List<double[]> rows = lines.stream().skip(1).map(line -> Arrays.stream(line.split(","))
                .mapToDouble(Double::parseDouble).toArray()).toList();
        return new Csv(columns, rows);
    }

    private static double value(double[] row, Csv csv, String name) {
        return row[csv.columns().get(name)];
    }

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int i = 0; i < rows.size(); i++) {
            double[] lower = rows.get(i);
            if (Math.abs(lower[0] - target) < 1.0e-9) return lower;
            if (i + 1 < rows.size() && rows.get(i + 1)[0] > target) {
                double[] upper = rows.get(i + 1);
                double f = (target - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length]; result[0] = target;
                for (int c = 1; c < result.length; c++)
                    result[c] = lower[c] + f * (upper[c] - lower[c]);
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }

    private record Csv(Map<String, Integer> columns, List<double[]> rows) { }
}
