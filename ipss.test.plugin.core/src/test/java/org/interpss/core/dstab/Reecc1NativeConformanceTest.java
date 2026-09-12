package org.interpss.core.dstab;

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
import org.interpss.dstab.renewable.Reecc1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-solver comparison against the independent native REECC1 trajectory. */
public class Reecc1NativeConformanceTest {
    private static final double STEP = .0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33",
            "renewable");
    private static final Path REFERENCE = Path.of("testData", "reference", "psse",
            "reecc1-bus1062", "psse.csv");

    @Test
    void allSevenStatesCommandsAndNetworkBoundaryFollowTheNativeFault() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("regca_reeca_repca_bus1062_weak.raw").toString(),
                CASE.resolve("regca_reecc1_bus1062.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setOutPutPerSteps(1);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus2", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 10.0), null, .05, .05), "Reecc1Fault");
        assertTrue(algorithm.initialization());

        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Regca1Model converter = (Regca1Model) gen.getDynamicGenDevice();
        Reecc1Model model = (Reecc1Model) converter.getActiveElectricalController();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, converter, model);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, converter, model);
        }

        Csv reference = read(REFERENCE);
        assertEquals(2005, reference.rows().size());
        String[] names = {"V_MEAS", "P_MEAS", "Q_PI", "V_PI", "IQ_LAG",
                "P_ORDER", "ENERGY_OUT", "V_BUS1", "V_BUS2", "V_BUS3",
                "P_PU", "Q_PU", "IP_CMD", "IQ_CMD", "RESIDUAL_ENERGY"};
        double[] maximum = new double[names.length];
        double[] maximumTime = new double[names.length];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > 1.0 + 1.0e-8
                    || Math.abs(time - .05) < STEP || Math.abs(time - .10) < STEP) continue;
            double[] row = interpolate(actual, time);
            for (int column = 0; column < names.length; column++) {
                double error = Math.abs(row[column + 1]
                        - expected[reference.columns().get(names[column])]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("REECC1 native max errors: " + Arrays.toString(maximum));
        System.out.println("REECC1 native max-error times: " + Arrays.toString(maximumTime));
        double[] ceilings = {.00105, .00045, 1.5e-5, .00030, 1.0e-12,
                1.1e-5, 1.6e-8, .0016, .0015, 1.2e-5,
                .00060, .00013, .00033, .0021, 7.0e-8};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= ceilings[column],
                    names[column] + "=" + maximum[column]);
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Regca1Model converter, Reecc1Model model) {
        Map<String, Double> state = model.getNamedStates();
        var output = converter.getStates(null);
        rows.add(new double[] {time,
                state.get("Voltage Measurement Filter"), state.get("Real Power Filter"),
                state.get("Reactive Power PI"), state.get("Voltage Error PI"),
                state.get("Reactive Current Lag"), state.get("Power Order Lag"),
                state.get("Battery Energy Output"),
                network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(),
                network.getBus("Bus3").getVoltageMag(),
                ((Number) output.get("REGCA1_P")).doubleValue(),
                ((Number) output.get("REGCA1_Q")).doubleValue(),
                model.getIpcmd(), -model.getIqcmd(), model.getStateOfCharge()});
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
