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
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.exc.psse.exst2.Exst2Exciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E 36.7 full-solver trajectory contract for EXST2. */
public class Exst2PsseSmibConformanceTest {
    private static final double STEP = 0.00025;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-genrou-exst2", "psse.csv");

    @Test
    void voltageReferencePulseMatchesPsseBoundaryAndFourExciterStates() throws Exception {
        IpssCorePlugin.init();
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(EmbeddedNativeTrajectoryValues.bytes(REFERENCE)));
        assertTrue(EmbeddedNativeTrajectoryValues.manifest(manifest).contains(hash),
                "PSS/E reference CSV hash is absent from its manifest");

        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_exst2_psse36.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "EXST2 SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization(), "EXST2 SMIB initialization");

        Machine machine = network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        Exst2Exciter exciter = (Exst2Exciter) machine.getExciter();
        assertEquals(Set.of("Sensed VT", "Regulator output, VR",
                "Exciter output, EFD", "Rate feedback integral"),
                exciter.getNamedStates().keySet());
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                exciter, initialRelativeAngle);
        boolean raised = false;
        boolean restored = false;
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            if (!raised && algorithm.getSimuTime() >= 0.05 - STEP / 2.0) {
                exciter.setRefPoint(exciter.getRefPoint() + 0.01);
                raised = true;
            }
            if (!restored && algorithm.getSimuTime() >= 0.10 - STEP / 2.0) {
                exciter.setRefPoint(exciter.getRefPoint() - 0.01);
                restored = true;
            }
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    exciter, initialRelativeAngle);
        }

        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        double[] initial = reference.rows().stream().filter(row -> row[0] >= -1.0e-9)
                .findFirst().orElseThrow();
        double initialPsseAngle = value(initial, reference, "MACH_ANGLE")
                - value(initial, reference, "REF_ANGLE");
        double[] maximum = new double[11];
        double[] maximumTime = new double[11];
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
                    value(expected, reference, "SENSED_VT"),
                    value(expected, reference, "REGULATOR_VR"),
                    value(expected, reference, "EXCITER_EFD"),
                    value(expected, reference, "RATE_FEEDBACK_INTEGRAL")
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("EXST2 PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("EXST2 PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {3.3e-6, 2.5e-6, 1.7e-5, 1.6e-5, 4.95e-4,
                9.2e-8, 1.6e-5, 9.5e-6, 3.3e-5, 1.6e-5, 6.7e-7};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "relative angle",
                "relative speed", "EFD", "sensed VT", "regulator VR",
                "exciter EFD", "rate-feedback integral"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Machine machine,
            Machine referenceMachine, Exst2Exciter exciter, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal(), power.getImaginary(),
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle()
                        - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEfd(),
                exciter.getSensedVoltage(), exciter.getRegulatorOutput(),
                exciter.getOutput(machine), exciter.getRateFeedbackIntegratorState()});
    }

    private static Csv read(Path path) throws Exception {
        List<String> lines = EmbeddedNativeTrajectoryValues.lines(path);
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
            if (Math.abs(lower[0] - target) < 1.0e-8) return lower;
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
