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
import org.interpss.dstab.mach.Wt3t1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-loop WT3T1 trajectory contract against the independent native run. */
public class Wt3t1PsseSmibConformanceTest {
    private static final double STEP = .0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of("testData", "reference", "psse",
            "smib-wt3t1", "psse.csv");

    @Test
    void twoMassCoordinatesMatchIndependentFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                CASE.resolve("SMIB_v33_wt3t1.dyr").toString());
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
        Wt3g2Model generator = (Wt3g2Model) gen.getDynamicGenDevice();
        Wt3t1Model model = generator.getDriveTrain();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, generator, model);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, generator, model);
        }

        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        String[] names = {"V_BUS1", "V_BUS2", "P_PU", "Q_PU", "SHAFT_ANGLE",
                "TURBINE_SPEED_DEV", "GENERATOR_SPEED_DEV", "GENERATOR_ANGLE_DEV",
                "PAERO"};
        double[] maximum = new double[names.length];
        for (double[] expected : reference.rows) {
            double time = expected[0];
            if (time < -1.0e-9 || time > 1.0 + 1.0e-8
                    || Math.abs(time - .05) < STEP || Math.abs(time - .10) < STEP) continue;
            double[] row = interpolate(actual, time);
            for (int column = 0; column < names.length; column++) {
                maximum[column] = Math.max(maximum[column], Math.abs(row[column + 1]
                        - expected[reference.columns.get(names[column]) ]));
            }
        }
        System.out.println("WT3T1 native max errors: " + Arrays.toString(maximum));
        double[] ceilings = {.0055, .011, .0085, .0014, .009, .00014,
                .00030, .017, 1.0e-7};
        for (int column = 0; column < names.length; column++) {
            assertTrue(maximum[column] <= ceilings[column], names[column]);
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Wt3g2Model generator,
            Wt3t1Model model) {
        rows.add(new double[] {time, network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(), generator.getP(), generator.getQ(),
                model.getShaftAngle(), model.getTurbineSpeed() - 1.0,
                model.getGeneratorSpeed() - 1.0, model.getGeneratorAngleDeviation(),
                model.getAerodynamicPower()});
    }

    private static Csv read(Path path) throws Exception {
        List<String> lines = EmbeddedNativeTrajectoryValues.lines(path);
        String[] headings = lines.get(0).split(",");
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < headings.length; i++) columns.put(headings[i], i);
        return new Csv(columns, lines.stream().skip(1).map(line -> Arrays.stream(line.split(","))
                .mapToDouble(Double::parseDouble).toArray()).toList());
    }

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int i = 0; i < rows.size(); i++) {
            double[] lower = rows.get(i);
            if (Math.abs(lower[0] - target) < 1.0e-8) return lower;
            if (i + 1 < rows.size() && rows.get(i + 1)[0] > target) {
                double[] upper = rows.get(i + 1);
                double f = (target - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length];
                result[0] = target;
                for (int c = 1; c < result.length; c++) {
                    result[c] = lower[c] + f * (upper[c] - lower[c]);
                }
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }

    private record Csv(Map<String, Integer> columns, List<double[]> rows) { }
}
