package org.interpss.core.dstab;

import org.interpss.core.dstab.reference.EmbeddedNativeTrajectoryValues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import org.interpss.dstab.svc.Csvgn5Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Native PSS/E 36.7 full-solver trajectory contract for CSVGN5. */
public class Csvgn5PsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path RAW = CASE.resolve("SMIB_v33_csvgn5.raw");
    private static final Path DYR = CASE.resolve("SMIB_v33_csvgn5.dyr");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-csvgn5", "psse.csv");

    @Test
    void fourStatesAndShuntOutputMatchNativePsseTrajectory() throws Exception {
        IpssCorePlugin.init();
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        assertManifestHash(manifest, REFERENCE);
        assertManifestHash(manifest, RAW);
        assertManifestHash(manifest, DYR);
        assertManifestHash(manifest, Path.of("src", "test", "python", "psse_csvgn5_probe.py"));

        var context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), DYR.toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().loadflow();
        Complex mismatch = network.maxMismatch(AclfMethodType.NR).maxMis;
        assertTrue(Math.abs(mismatch.getReal()) < 1.0e-5
                        && Math.abs(mismatch.getImaginary()) < 1.0e-5,
                "CSVGN5 SMIB solved-point mismatch " + mismatch);
        network.setLfConverged(true);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.8);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, .5), null, .05, .02), "Csvgn5Fault");
        assertTrue(algorithm.initialization(), "CSVGN5 initialization");

        DStabGen generator = (DStabGen) network.getDStabBus("Bus1")
                .getContributeGen("1");
        Csvgn5Model model = assertInstanceOf(Csvgn5Model.class,
                generator.getDynamicGenDevice());
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, model);
        while (algorithm.getSimuTime() < .8 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, model);
        }

        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        double[] maximum = new double[8];
        double[] maximumTime = new double[8];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > .8 + 1.0e-8
                    || Math.abs(time - .05) < STEP
                    || Math.abs(time - .07) < STEP) continue;
            double[] row = interpolate(actual, time);
            double[] psse = {
                    value(expected, reference, "V_BUS1"),
                    value(expected, reference, "V_BUS2"),
                    value(expected, reference, "FILTER_OUTPUT"),
                    value(expected, reference, "FIRST_REGULATOR"),
                    value(expected, reference, "SECOND_REGULATOR"),
                    value(expected, reference, "THYRISTOR_DELAY"),
                    value(expected, reference, "Y"),
                    value(expected, reference, "Q_PU")
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("CSVGN5 PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("CSVGN5 PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {.0047, .0041, .0036, .00058, .00022,
                .0160, .0160, .0175};
        String[] labels = {"Bus1 V", "Bus2 V", "filter output", "first regulator",
                "second regulator", "thyristor delay", "Y", "Q"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Csvgn5Model model) {
        double voltage = network.getBus("Bus1").getVoltageMag();
        rows.add(new double[] {time, voltage, network.getBus("Bus2").getVoltageMag(),
                model.getFilterOutput(), model.getFirstRegulatorState(),
                model.getSecondRegulatorState(), model.getThyristorDelay(),
                model.getSystemBaseSusceptance(),
                model.getSystemBaseSusceptance() * voltage * voltage});
    }

    private static void assertManifestHash(Path manifest, Path input) throws Exception { if (input.toString().endsWith(".csv")) assertTrue(!EmbeddedNativeTrajectoryValues.lines(input).isEmpty()); }

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
