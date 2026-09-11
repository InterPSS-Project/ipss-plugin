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
import org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter;
import org.interpss.dstab.mach.IeeeVoltageCompensatedMachine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E 36.7 full-solver trajectory contract for IEEEVC. */
public class IeeeVcPsseSmibConformanceTest {
    private static final double STEP = 0.00025;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "psse", "smib-ieeevc", "psse.csv");

    @Test
    void compensatedExciterBoundaryMatchesNativePsseFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        Path manifest = REFERENCE.resolveSibling("manifest.json");
        assertManifestHash(manifest, REFERENCE);
        assertManifestHash(manifest, CASE.resolve("SMIB_v33.raw"));
        assertManifestHash(manifest, CASE.resolve("SMIB_v33_genrou_ieeevc_ieeex1.dyr"));
        assertManifestHash(manifest, Path.of("src", "test", "python", "psse_ieeevc_probe.py"));
        assertEquals(2, DynamicModelCatalog.find("IEEEVC").orElseThrow().parameterCount());

        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_ieeevc_ieeex1.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "IEEEVC SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "MachineFault");
        assertTrue(algorithm.initialization(), "IEEEVC initialization");

        IeeeVoltageCompensatedMachine machine = (IeeeVoltageCompensatedMachine)
                network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        Ieeex1Exciter exciter = (Ieeex1Exciter) machine.getExciter();
        assertEquals(0.015, machine.getIeeeVcData().rc(), 0.0);
        assertEquals(0.080, machine.getIeeeVcData().xc(), 0.0);
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, exciter);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, exciter);
        }

        Csv reference = read(REFERENCE);
        assertEquals(2005, reference.rows().size());
        double leadLagCoordinateGain = 1.0
                - exciter.getData().getTc() / exciter.getData().getTb();
        double feedbackCoordinateGain = exciter.getData().getKf()
                / exciter.getData().getTf();
        double[] maximum = new double[13];
        double[] maximumTime = new double[13];
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
                            - value(expected, reference, "REF_ANGLE"),
                    value(expected, reference, "MACH_SPEED")
                            - value(expected, reference, "REF_SPEED"),
                    value(expected, reference, "EFD"),
                    psseCompensatedVoltage(expected, reference),
                    value(expected, reference, "SENSED_VT"),
                    value(expected, reference, "LEAD_LAG") / leadLagCoordinateGain,
                    value(expected, reference, "REGULATOR_VR"),
                    value(expected, reference, "EXCITER_EFD"),
                    value(expected, reference, "RATE_FEEDBACK_INTEGRATOR")
                            / feedbackCoordinateGain
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("IEEEVC PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("IEEEVC PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerances = {0.0018, 0.0039, 0.024, 0.0095, 0.19, 9.0e-5,
                0.0048, 7.0e-4, 3.5e-4, 9.0e-5, 0.018, 0.0048, 0.0012};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "relative angle",
                "relative speed", "EFD", "VCOMP", "sensed V", "lead-lag",
                "regulator VR", "exciter EFD", "rate-feedback integrator"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerances[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerances[column]));
        }
    }

    private static double psseCompensatedVoltage(double[] row, Csv csv) {
        double magnitude = value(row, csv, "V_BUS1");
        double angle = Math.toRadians(value(row, csv, "A_BUS1"));
        Complex voltage = new Complex(magnitude * Math.cos(angle), magnitude * Math.sin(angle));
        Complex power = new Complex(value(row, csv, "P_PU"), value(row, csv, "Q_PU"));
        Complex generatorCurrent = power.divide(voltage).conjugate();
        return voltage.add(new Complex(0.015, 0.080).multiply(generatorCurrent)).abs();
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            IeeeVoltageCompensatedMachine machine, Machine referenceMachine,
            Ieeex1Exciter exciter) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        rows.add(new double[] {time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal(), power.getImaginary(),
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle()),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEfd(),
                machine.getIeeeVcVoltage(), exciter.getSensedVoltage(),
                exciter.getLeadLagState(), exciter.getRegulatorOutput(),
                exciter.getExciterFieldState(), exciter.getRateFeedbackIntegratorState()});
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
