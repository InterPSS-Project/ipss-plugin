package org.interpss.core.dstab.mach;

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
import org.interpss.dstab.mach.GenqejMachine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Full-solver contract against an independent native GENQEJU trajectory. */
public class GenqejPsseSmibConformanceTest {
    private static final double STEP = 0.00025;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-genqej", "psse.csv");

    @Test
    void threeCycleFaultMatchesBoundaryAndAllSixPublishedStates() throws Exception {
        IpssCorePlugin.init();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(EmbeddedNativeTrajectoryValues.bytes(REFERENCE)));
        assertTrue(EmbeddedNativeTrajectoryValues.manifest(REFERENCE.resolveSibling("manifest.json")).contains(hash));

        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genqej.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization());

        GenqejMachine machine = (GenqejMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        assertEquals(6, machine.getNamedStates().size());
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    initialRelativeAngle);
        }

        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        double[] initial = reference.rows().stream().filter(row -> row[0] >= -1.0e-9)
                .findFirst().orElseThrow();
        double initialNativeAngle = value(initial, reference, "MACH_ANGLE")
                - value(initial, reference, "REF_ANGLE");
        double[] maximum = new double[12];
        double[] maximumTime = new double[12];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > 1.0 + 1.0e-8
                    || Math.abs(time - 0.05) < STEP || Math.abs(time - 0.10) < STEP) continue;
            double[] row = interpolate(actual, time);
            double[] nativeRow = {value(expected, reference, "V_BUS1"),
                    value(expected, reference, "V_BUS2"), value(expected, reference, "P_PU"),
                    value(expected, reference, "Q_PU"),
                    value(expected, reference, "MACH_ANGLE")
                            - value(expected, reference, "REF_ANGLE") - initialNativeAngle,
                    value(expected, reference, "MACH_SPEED")
                            - value(expected, reference, "REF_SPEED"),
                    value(expected, reference, "EP_Q"), value(expected, reference, "EP_D"),
                    value(expected, reference, "PSIP_D"), value(expected, reference, "PSIP_Q"),
                    value(expected, reference, "SPEED_DEV"), value(expected, reference, "ANGLE")};
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - nativeRow[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("GENQEJ native max errors: " + Arrays.toString(maximum));
        System.out.println("GENQEJ native max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {0.0038, 0.0060, 0.0255, 0.0211, 0.196, 9.8e-5,
                2.7e-4, 7.6e-4, 0.0067, 0.0021, 9.8e-5, 0.0035};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "relative angle",
                "relative speed", "E'q", "E'd", "Psi'd", "Psi'q",
                "speed state", "angle state"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, GenqejMachine machine,
            Machine referenceMachine, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        Map<String, Double> state = machine.getNamedStates();
        rows.add(new double[] {time, network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(), power.getReal(), power.getImaginary(),
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle()
                        - initialRelativeAngle), machine.getSpeed() - referenceMachine.getSpeed(),
                state.get("E'q"), state.get("E'd"), state.get("Psi'd"), state.get("Psi'q"),
                state.get("Speed deviation"), state.get("Angle")});
    }

    private static Csv read(Path path) throws Exception {
        List<String> lines = EmbeddedNativeTrajectoryValues.lines(path);
        String[] headings = lines.get(0).split(",");
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int index = 0; index < headings.length; index++) columns.put(headings[index], index);
        return new Csv(columns, lines.stream().skip(1).map(line -> Arrays.stream(line.split(","))
                .mapToDouble(Double::parseDouble).toArray()).toList());
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
