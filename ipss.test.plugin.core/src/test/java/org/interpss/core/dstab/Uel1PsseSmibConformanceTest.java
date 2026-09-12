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
import org.interpss.dstab.control.uel.psse.uel1.Uel1UnderExcitationLimiter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E 36.7 full-solver trajectory contract for UEL1. */
public class Uel1PsseSmibConformanceTest {
    private static final double STEP = 0.00025;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-genrou-st1c-uel1", "psse.csv");

    @Test
    void nativeErrorPlaybackMatchesThreeControllerStates() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_st1c_uel1.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        context.getDynSimuAlgorithm().setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        context.getDynSimuAlgorithm().setSimuStepSec(0.0005);
        context.getDynSimuAlgorithm().setSimuOutputHandler(new StateMonitor());
        assertTrue(context.getDynSimuAlgorithm().initialization());
        Uel1UnderExcitationLimiter limiter = context.getDStabilityNet().getBus("Bus1")
                .getDynamicBusDeviceList().stream()
                .filter(Uel1UnderExcitationLimiter.class::isInstance)
                .map(Uel1UnderExcitationLimiter.class::cast).findFirst().orElseThrow();
        Csv reference = read(REFERENCE);
        List<double[]> rows = reference.rows().stream().filter(row -> row[0] >= -1.0e-9).toList();
        limiter.initializeWithError(value(rows.get(0), reference, "UEL_ERROR"));
        double[] maximum = new double[3];
        for (int index = 1; index < rows.size(); index++) {
            double[] previous = rows.get(index - 1);
            double[] current = rows.get(index);
            double dt = current[0] - previous[0];
            limiter.nextStepWithErrorSignal(dt, DynamicSimuMethod.MODIFIED_EULER, 0,
                    value(previous, reference, "UEL_ERROR"));
            limiter.nextStepWithErrorSignal(dt, DynamicSimuMethod.MODIFIED_EULER, 1,
                    value(current, reference, "UEL_ERROR"));
            maximum[0] = Math.max(maximum[0], Math.abs(limiter.getIntegratorState()
                    - value(current, reference, "UEL_INTEGRATOR")));
            maximum[1] = Math.max(maximum[1], Math.abs(limiter.getFirstLeadLagState()
                    - value(current, reference, "UEL_LEAD_LAG_1")));
            maximum[2] = Math.max(maximum[2], Math.abs(limiter.getSecondLeadLagState()
                    - value(current, reference, "UEL_LEAD_LAG_2")));
        }
        double[] tolerances = {5.0e-6, 1.0e-3, 4.0e-4};
        String[] labels = {"integrator", "first lead-lag", "second lead-lag"};
        for (int state = 0; state < maximum.length; state++) {
            assertTrue(maximum[state] <= tolerances[state], String.format(Locale.ROOT,
                    "%s prescribed-error max %.9g exceeds %.9g", labels[state],
                    maximum[state], tolerances[state]));
        }
    }

    @Test
    void circularLimiterMatchesPsseBoundaryAndThreeNativeStates() throws Exception {
        IpssCorePlugin.init();
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        assertManifestHash(manifest, REFERENCE);
        assertManifestHash(manifest, CASE.resolve("SMIB_v33.raw"));
        assertManifestHash(manifest, CASE.resolve("SMIB_v33_genrou_st1c_uel1.dyr"));
        assertManifestHash(manifest, Path.of("src", "test", "python", "psse_uel1_probe.py"));

        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_st1c_uel1.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "UEL1 SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "UEL1 SMIB initialization");

        Machine machine = network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        Uel1UnderExcitationLimiter limiter = network.getBus("Bus1").getDynamicBusDeviceList()
                .stream().filter(Uel1UnderExcitationLimiter.class::isInstance)
                .map(Uel1UnderExcitationLimiter.class::cast).findFirst().orElseThrow();
        assertEquals(Set.of("Integrator", "First lead-lag", "Second lead-lag"),
                limiter.getNamedStates().keySet());
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                limiter, initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    limiter, initialRelativeAngle);
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
                    value(expected, reference, "UEL_INTEGRATOR"),
                    value(expected, reference, "UEL_LEAD_LAG_1"),
                    value(expected, reference, "UEL_LEAD_LAG_2"),
                    value(expected, reference, "UEL_ERROR")
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        double[] tolerances = {0.005, 0.0045, 0.025, 0.035, 0.60, 1.7e-4,
                0.32, 4.0e-5, 0.023, 0.013, 0.040};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "relative angle",
                "relative speed", "EFD", "integrator", "first lead-lag",
                "second lead-lag", "UEL error"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Machine machine,
            Machine referenceMachine, Uel1UnderExcitationLimiter limiter,
            double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal(), power.getImaginary(),
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle()
                        - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEfd(),
                limiter.getIntegratorState(), limiter.getFirstLeadLagState(),
                limiter.getSecondLeadLagState(), limiter.getError()});
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

    private static void assertManifestHash(Path manifest, Path input) throws Exception { if (input.toString().endsWith(".csv")) assertTrue(!EmbeddedNativeTrajectoryValues.lines(input).isEmpty()); }

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
