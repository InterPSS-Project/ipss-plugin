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

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.svc.Svsmo1t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Native PSS/E 36.7 full-solver trajectory contract for SVSMO1T2. */
@org.junit.jupiter.api.Tag("private-reference")
public class Svsmo1t2PsseConformanceTest {
    private static final double STEP = .0005;
    private static final Path RAW = Path.of("testData", "psse", "v35",
            "PSSE_5Bus_svsmo1t2_synthetic.raw");
    private static final Path DYR = Path.of("testData", "psse", "v35",
            "PSSE_5Bus_svsmo1t2_synthetic.dyr");
    private static final Path REFERENCE = Path.of("testData", "reference", "psse",
            "fivebus-svsmo1t2", "psse.csv");

    @Test
    void fiveStatesBoundarySignalsAndMvarMatchNativePsse() throws Exception {
        IpssCorePlugin.init();
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        assertManifestHash(manifest, REFERENCE);
        assertManifestHash(manifest, RAW);
        assertManifestHash(manifest, DYR);
        assertManifestHash(manifest, Path.of("src", "test", "python",
                "psse_svsmo1t2_probe.py"));

        var context = new PSSEMultiFileLoader().loadDStab(RAW.toString(), DYR.toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        network.setLfConverged(true);
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(.8);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus4", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, .8), null, .05, .02), "Svsmo1Fault");
        assertTrue(algorithm.initialization());

        Svsmo1t2Model model = network.getDStabBus("Bus4").getDynamicBusDeviceList()
                .stream().filter(Svsmo1t2Model.class::isInstance)
                .map(Svsmo1t2Model.class::cast).findFirst().orElseThrow();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, model);
        while (algorithm.getSimuTime() < .8 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, model);
        }

        Csv reference = read(REFERENCE);
        assertEquals(1605, reference.rows().size());
        String[] signals = {"V_BUS1", "V_BUS4", "V_BUS5", "STATE_1", "STATE_2",
                "STATE_3", "STATE_4", "STATE_5", "VAR_2", "VAR_20", "VAR_24",
                "VAR_25", "VAR_27", "VAR_28", "VAR_29"};
        double[] maximum = new double[signals.length];
        double[] maximumTime = new double[signals.length];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || time > .8 + 1.0e-8
                    || Math.abs(time - .05) < STEP
                    || Math.abs(time - .07) < STEP) continue;
            double[] row = interpolate(actual, time);
            for (int column = 0; column < signals.length; column++) {
                double error = Math.abs(row[column + 1]
                        - value(expected, reference, signals[column]));
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("SVSMO1T2 PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("SVSMO1T2 PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] diagnosticBounds = {.12, .27, .12, .12, .05, .17, 1.0e-12, .14,
                1.05, .12, .004, .004, .14, .14, 90.0};
        for (int column = 0; column < signals.length; column++) {
            assertTrue(maximum[column] <= diagnosticBounds[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", signals[column],
                    maximum[column], maximumTime[column], diagnosticBounds[column]));
        }
    }

    @Test
    void nativeStatesSatisfyPublishedControllerEquations()
            throws Exception {
        Csv reference = read(REFERENCE);
        Map<Long, double[]> unique = new LinkedHashMap<>();
        for (double[] row : reference.rows()) {
            if (row[0] >= -1.0e-9) unique.put(Math.round(row[0] / STEP), row);
        }
        List<double[]> input = new ArrayList<>(unique.values());
        double sensorResidual = 0.0;
        double firingResidual = 0.0;
        for (int index = 1; index < input.size(); index++) {
            double[] lower = input.get(index - 1);
            double[] upper = input.get(index);
            double dt = upper[0] - lower[0];
            double time = upper[0];
            // Use smooth native segments. Event and limiter transitions have
            // unexported network-iteration endpoints, so their sampled channels
            // cannot form a self-contained finite-difference equation oracle.
            if (dt <= 0.0 || !((time >= .055 && time <= .068) || time >= .15)) continue;

            double x0 = value(lower, reference, "STATE_1");
            double x1 = value(upper, reference, "STATE_1");
            double u0 = value(lower, reference, "V_BUS4");
            double u1 = value(upper, reference, "V_BUS4");
            double sensorStep = .5 * dt * ((u0 - x0) / .012 + (u1 - x1) / .012);
            sensorResidual = Math.max(sensorResidual, Math.abs(x1 - x0 - sensorStep));

            double b0 = value(lower, reference, "STATE_3");
            double b1 = value(upper, reference, "STATE_3");
            double command0 = value(lower, reference, "VAR_2");
            double command1 = value(upper, reference, "VAR_2");
            if (Math.abs(command1 - command0) > .01) continue;
            double firingStep = .5 * dt
                    * ((command0 - b0) / .012 + (command1 - b1) / .012);
            firingResidual = Math.max(firingResidual, Math.abs(b1 - b0 - firingStep));
        }
        System.out.printf(Locale.ROOT,
                "SVSMO1T2 native trapezoidal residuals: sensor=%.9g firing=%.9g%n",
                sensorResidual, firingResidual);
        assertTrue(sensorResidual < 2.0e-5);
        assertTrue(firingResidual < 6.0e-5);
        for (double[] row : input) {
            assertEquals(value(row, reference, "STATE_1"),
                    value(row, reference, "VAR_20"), 2.0e-6);
            assertEquals(value(row, reference, "STATE_5"),
                    value(row, reference, "VAR_28"), 2.0e-6);
            assertEquals(0.0, value(row, reference, "STATE_4"), 1.0e-12);
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Svsmo1t2Model model) {
        rows.add(new double[] {time,
                network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus4").getVoltageMag(),
                network.getBus("Bus5").getVoltageMag(),
                model.getVoltageMeasurement(), model.getFastIntegrator(),
                model.getSusceptance(), model.getSlowIntegrator(),
                model.getGainLeadLagState(), model.getFastControllerOutput(),
                model.getVoltageMeasurement(), model.getVoltageReference(),
                model.getVoltageReference() + model.getSlowBias(),
                model.getGainLeadLagOutput(), model.getVoltageError(),
                model.getMvarOutput()});
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
