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
import org.interpss.dstab.mach.Wt3g2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-solver trajectory contract against the independent native WT3G2 run. */
public class Wt3g2PsseSmibConformanceTest {
    private static final double STEP = .0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of("testData", "reference", "psse",
            "smib-wt3g2", "psse.csv");

    @Test
    void converterLimitsAndPllMatchIndependentFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                CASE.resolve("SMIB_v33_wt3g2.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, .1), null, .05, .05), "WindFault");
        assertTrue(algorithm.initialization());

        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Wt3g2Model model = (Wt3g2Model) gen.getDynamicGenDevice();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, model);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, model);
        }

        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        String[] names = {"V_BUS1", "V_BUS2", "P_PU", "Q_PU", "IP_STATE",
                "IQ_STATE", "PLL_1", "PLL_2", "LVPL_V", "DELTA_Q",
                "A_BUS1", "A_BUS2"};
        double[] maximum = new double[names.length];
        double[] maximumTime = new double[names.length];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > 1.0 + 1.0e-8
                    || Math.abs(time - .05) < STEP
                    || Math.abs(time - .10) < STEP) continue;
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
        System.out.println("WT3G2 native max errors: " + Arrays.toString(maximum));
        System.out.println("WT3G2 native max-error times: " + Arrays.toString(maximumTime));
        double[] channelCeiling = {.0055, .011, .0085, .0014, .014, .075,
                1.2e-5, .008, .008, 1.0e-12, 1.5, 1.5};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= channelCeiling[column], names[column]);
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Wt3g2Model model) {
        Map<String, Double> state = model.getNamedStates();
        rows.add(new double[] {time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                model.getP(), model.getQ(), state.get("Converter lag for Ipcmd"),
                state.get("Converter lag for Iqcmd"), state.get("PLL first integrator"),
                state.get("PLL second integrator"), state.get("Voltage sensor for LVPL"),
                model.getDeltaQ(),
                Math.toDegrees(network.getBus("Bus1").getVoltage().getArgument()),
                Math.toDegrees(network.getBus("Bus2").getVoltage().getArgument())});
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
