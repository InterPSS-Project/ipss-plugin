package org.interpss.core.dstab.mach;

import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt2e1Model;
import org.interpss.dstab.mach.Wt2g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-loop WT2E1 trajectory contract against the independent native tool. */
public class Wt2e1PsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of("testData", "reference", "psse",
            "smib-wt2e1", "psse.csv");

    @Test
    void rotorResistanceControllerMatchesIndependentFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(),
                CASE.resolve("SMIB_v33_wt2e1.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.1), null, 0.05, 0.05), "WindFault");
        assertTrue(algorithm.initialization());

        Wt2g1Machine machine = (Wt2g1Machine) network.getMachine("Bus1-mach1");
        Wt2e1Model controller = machine.getRotorResistanceController();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, controller);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine, controller);
        }

        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        System.out.println("WT2E1 initial InterPSS: " + Arrays.toString(actual.get(0)));
        System.out.println("WT2E1 initial independent: " + Arrays.toString(reference.rows().stream()
                .filter(row -> Math.abs(row[0]) < 1e-9).findFirst().orElseThrow()));
        String[] names = {"V_BUS1", "V_BUS2", "P_PU", "Q_PU", "SPEED_DEV",
                "SPEED_FILTER", "POWER_FILTER", "PI_INTEGRATOR"};
        double[] maximum = new double[names.length];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1e-9 || time > 1.0 + 1e-8
                    || Math.abs(time - 0.05) < STEP || Math.abs(time - 0.10) < STEP) continue;
            double[] row = interpolate(actual, time);
            for (int column = 0; column < names.length; column++) {
                maximum[column] = Math.max(maximum[column], Math.abs(row[column + 1]
                        - expected[reference.columns().get(names[column])]));
            }
        }
        System.out.println("WT2E1 independent max errors: " + Arrays.toString(maximum));
        double[] ceiling = {.0055, .0101, .0154, .0206, 2e-9, 2e-9, .0090, .0270};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= ceiling[column], names[column]);
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Wt2g1Machine machine,
            Wt2e1Model controller) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        Map<String, Double> state = controller.getNamedStates();
        rows.add(new double[] {time, network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(), power.getReal(), power.getImaginary(),
                machine.getSpeed() - 1.0, state.get("Rotor speed filter"),
                state.get("Power filter"), state.get("PI integrator")});
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

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int index = 0; index < rows.size(); index++) {
            double[] lower = rows.get(index);
            if (Math.abs(lower[0] - target) < 1e-8) return lower;
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
