package org.interpss.core.dstab.mach;

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
import org.interpss.dstab.mach.Cimtr4Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-solver trajectory contract against native PSS/E 36.7 CIMTR4. */
@org.junit.jupiter.api.Tag("private-reference")
public class Cimtr4PsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-cimtr4", "psse.csv");

    @Test
    void twoCageMotorMatchesNativePsseFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(REFERENCE)));
        assertTrue(Files.readString(manifest).contains(hash));
        assertManifestHash(manifest, CASE.resolve("SMIB_v33_cimtr4_psse36.raw"));
        assertManifestHash(manifest, CASE.resolve("SMIB_v33_cimtr4_psse36.dyr"));
        assertManifestHash(manifest, Path.of("src", "test", "python", "psse_cimtr4_probe.py"));

        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_cimtr4_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_cimtr4_psse36.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "CIMTR4 load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.1), null, 0.05, 0.05), "MotorFault");
        assertTrue(algorithm.initialization(), "CIMTR4 initialization");

        Cimtr4Machine machine = (Cimtr4Machine) network.getMachine("Bus1-mach1");
        assertEquals(-0.0070947, machine.getSpeed() - 1.0, 2.0e-6);
        assertEquals(6, machine.getNamedStates().size());
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine);
        }

        Csv reference = read(REFERENCE);
        assertEquals(2005, reference.rows().size());
        double[] maximum = new double[10];
        double[] maximumTime = new double[10];
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
                    value(expected, reference, "SPEED_DEV"),
                    value(expected, reference, "MOTOR_Q"),
                    Math.hypot(value(expected, reference, "EP_Q"),
                            value(expected, reference, "EP_D")),
                    Math.hypot(value(expected, reference, "EPP_Q"),
                            value(expected, reference, "EPP_D")),
                    value(expected, reference, "Q_COMP_ADMITTANCE"),
                    value(expected, reference, "TELEC")
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("CIMTR4 PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("CIMTR4 PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {5.0e-4, 2.0e-4, 2.0e-3, 3.0e-3, 7.0e-5,
                3.0e-3, 1.2e-3, 3.0e-2, 5.0e-6, 4.0e-3};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "speed", "motor Q",
                "|E'|", "|E''|", "Q compensation", "Telec"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Cimtr4Machine machine) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        Map<String, Double> state = machine.getNamedStates();
        Map<String, Object> diagnostic = machine.getStates(null);
        double motorQ = ((Number) diagnostic.get("CIMTR4 Motor Q")).doubleValue();
        rows.add(new double[] {time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal(), power.getImaginary(), machine.getSpeed() - 1.0, motorQ,
                Math.hypot(state.get("E'q"), state.get("E'd")),
                Math.hypot(state.get("E''q"), state.get("E''d")),
                ((Number) diagnostic.get("CIMTR4 Q Compensation")).doubleValue(),
                ((Number) diagnostic.get("CIMTR4 Telec")).doubleValue()});
    }

    private static void assertManifestHash(Path manifest, Path input) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(input)));
        assertTrue(Files.readString(manifest).contains(hash), input + " hash missing from manifest");
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
