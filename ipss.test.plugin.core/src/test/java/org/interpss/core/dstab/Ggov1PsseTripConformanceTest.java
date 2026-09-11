package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E 36.7 lifecycle contract for a GGOV1 machine trip. */
public class Ggov1PsseTripConformanceTest {
    private static final double STEP = 0.00025;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-genrou-ggov1-trip", "psse.csv");
    private static final String[] PSS_E_STATES = {
            "PELEC_FILTER", "DERIVATIVE", "GOVERNOR_INTEGRAL", "VALVE_STROKE",
            "TURBINE_LEAD_LAG", "LOAD_MEASUREMENT", "LOAD_INTEGRAL",
            "SUPERVISORY_LOAD", "ACCELERATION", "TEMPERATURE_DETECTION"
    };

    @Test
    void machineTripZerosBoundaryOutputsAndFreezesTheGovernorLikePsse() throws Exception {
        IpssCorePlugin.init();
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(REFERENCE)));
        assertTrue(Files.readString(manifest).contains(hash),
                "PSS/E reference CSV hash is absent from its manifest");
        Csv reference = read(REFERENCE);
        assertEquals(2004, reference.rows().size(), "PSS/E raw samples");

        double[] lastOnline = reference.rows().stream()
                .filter(row -> row[0] >= 0.0 && value(row, reference, "P_PU") > 0.0)
                .reduce((left, right) -> right).orElseThrow();
        List<double[]> offline = reference.rows().stream()
                .filter(row -> row[0] >= 0.1 - 1.0e-6
                        && value(row, reference, "P_PU") == 0.0).toList();
        assertTrue(offline.size() > 1_700, "PSS/E post-trip interval was not recorded");
        for (double[] row : offline) {
            assertEquals(0.0, value(row, reference, "P_PU"), 0.0);
            assertEquals(0.0, value(row, reference, "Q_PU"), 0.0);
            assertEquals(0.0, value(row, reference, "PMECH"), 0.0);
            assertEquals(0.0, value(row, reference, "MACH_SPEED"), 0.0);
            for (String state : PSS_E_STATES) {
                assertEquals(value(lastOnline, reference, state), value(row, reference, state),
                        state.equals("ACCELERATION") ? 3.0e-9 : 1.0e-12,
                        "PSS/E GGOV1 state changed after its machine was tripped: " + state);
            }
        }

        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_ggov1.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GGOV1 SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent(
                "Bus1", "1", network, 0.1), "Generator1Trip");
        assertTrue(algorithm.initialization(), "GGOV1 trip initialization");

        Machine machine = network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        PsseGgov1Governor governor = (PsseGgov1Governor) machine.getGovernor();
        double initialReferenceAngle = referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        Map<String, Double> onlineStates = null;
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            if (algorithm.getSimuTime() < 0.1 - STEP / 2.0) {
                onlineStates = new LinkedHashMap<>(governor.getNamedStates());
            }
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, referenceMachine,
                    initialReferenceAngle);
        }
        assertFalse(machine.isActive(), "InterPSS machine must be offline after the trip");
        assertTrue(onlineStates != null);
        for (Map.Entry<String, Double> state : onlineStates.entrySet()) {
            assertEquals(state.getValue(), governor.getNamedState(state.getKey()), 1.0e-12,
                    "InterPSS GGOV1 state changed after its machine was tripped: " + state.getKey());
        }

        double[] initialPsse = reference.rows().stream().filter(row -> row[0] >= -1.0e-9)
                .findFirst().orElseThrow();
        double initialPsseReferenceAngle = value(initialPsse, reference, "REF_ANGLE");
        double[] maximum = new double[4];
        double[] maximumTime = new double[4];
        for (double[] expected : offline) {
            double time = expected[0];
            if (time < 0.1005 - 1.0e-8 || time > 1.0 + 5.0e-5) continue;
            double[] row = interpolate(actual, time);
            double[] psse = {
                    value(expected, reference, "V_BUS1"),
                    value(expected, reference, "V_BUS2"),
                    value(expected, reference, "REF_SPEED"),
                    value(expected, reference, "REF_ANGLE") - initialPsseReferenceAngle
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("GGOV1 trip PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("GGOV1 trip PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerance = {2.0e-5, 2.0e-5, 3.0e-9, 3.0e-5};
        String[] label = {"Bus1 V", "Bus2 V", "reference speed", "reference angle"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerance[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", label[column],
                    maximum[column], maximumTime[column], tolerance[column]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Machine referenceMachine,
            double initialReferenceAngle) {
        rows.add(new double[] {time, network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(), referenceMachine.getSpeed() - 1.0,
                Math.toDegrees(referenceMachine.getAngle() - initialReferenceAngle)});
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
