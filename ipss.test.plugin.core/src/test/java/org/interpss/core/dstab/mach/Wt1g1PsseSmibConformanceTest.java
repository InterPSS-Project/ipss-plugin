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
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt1g1Machine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Full-solver trajectory contract against native PSS/E 36.7 WT1G1. */
public class Wt1g1PsseSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of("testData", "reference", "psse",
            "smib-wt1g1", "psse.csv");
    private static final Path WT12_REFERENCE = Path.of("testData", "reference", "psse",
            "smib-wt12t1", "psse.csv");
    private static final Path WT12A_REFERENCE = Path.of("testData", "reference", "psse",
            "smib-wt12a1", "psse.csv");

    @Test
    void pseudoGovernorMatchesNativePsseFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt12a1_psse36.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", network,
                SimpleFaultCode.GROUND_3P, new Complex(0, .1), null, .05, .05), "WindFault");
        assertTrue(algorithm.initialization());
        Wt1g1Machine machine = (Wt1g1Machine) network.getMachine("Bus1-mach1");
        List<double[]> actual = new ArrayList<>();
        recordWt12a(actual, algorithm.getSimuTime(), network, machine);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2) {
            assertTrue(algorithm.solveDEqnStep(true));
            recordWt12a(actual, algorithm.getSimuTime(), network, machine);
        }
        Csv reference = read(WT12A_REFERENCE);
        String[] names = {"POWER_FILTER", "PI_INTEGRATOR", "OUTPUT_FILTER_1",
                "OUTPUT_FILTER_2", "SHAFT_ANGLE", "TURBINE_SPEED_DEV",
                "GENERATOR_SPEED_DEV", "GENERATOR_ANGLE_DEV", "PAERO", "TELEC",
                "V_BUS1", "V_BUS2", "P_PU", "Q_PU", "SPEED_DEV"};
        double[] maximum = new double[names.length];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1e-9 || Math.abs(time-.05) < STEP || Math.abs(time-.10) < STEP) continue;
            double[] row = interpolate(actual, time);
            for (int i=0;i<names.length;i++) maximum[i]=Math.max(maximum[i],
                    Math.abs(row[i+1]-value(expected, reference, names[i])));
        }
        System.out.println("WT12A1 PSS/E max errors: " + Arrays.toString(maximum));
        double[] tolerance = {.0092, .0007, .0006, .0005, .0052, .00013, .00036,
                .0052, .0005, .027, .0040, .0083, .027, .0031, .00036};
        for (int i=0;i<maximum.length;i++) assertTrue(maximum[i] <= tolerance[i],
                names[i] + " max error " + maximum[i] + " exceeds " + tolerance[i]);
    }

    @Test
    void twoMassDriveTrainMatchesNativePsseFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt12t1_psse36.dyr").toString());
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
        Wt1g1Machine machine = (Wt1g1Machine) network.getMachine("Bus1-mach1");
        List<double[]> actual = new ArrayList<>();
        recordWt12(actual, algorithm.getSimuTime(), network, machine);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            recordWt12(actual, algorithm.getSimuTime(), network, machine);
        }
        Csv reference = read(WT12_REFERENCE);
        String[] names = {"SHAFT_ANGLE", "TURBINE_SPEED_DEV", "GENERATOR_SPEED_DEV",
                "GENERATOR_ANGLE_DEV", "PAERO", "TELEC", "V_BUS1", "V_BUS2",
                "P_PU", "Q_PU", "SPEED_DEV"};
        double[] maximum = new double[names.length];
        for (double[] expected : reference.rows()) {
            double time = expected[0];
            if (time < -1.0e-9 || Math.abs(time - .05) < STEP
                    || Math.abs(time - .10) < STEP) continue;
            double[] row = interpolate(actual, time);
            for (int i = 0; i < names.length; i++) {
                maximum[i] = Math.max(maximum[i],
                        Math.abs(row[i + 1] - value(expected, reference, names[i])));
            }
        }
        System.out.println("WT12T1 PSS/E max errors: " + Arrays.toString(maximum));
        double[] tolerance = {0.0051, 1.2e-4, 3.6e-4, 0.0052, 0.0011, 0.027,
                0.0040, 0.0083, 0.027, 0.0031, 3.6e-4};
        for (int i = 0; i < maximum.length; i++) {
            assertTrue(maximum[i] <= tolerance[i], names[i] + " max error "
                    + maximum[i] + " exceeds " + tolerance[i]);
        }
    }

    @Test
    void twoCageGeneratorMatchesNativePsseFaultTrajectory() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt1g1_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_wt1g1_psse36.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "WT1G1 load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.1), null, 0.05, 0.05), "WindFault");
        assertTrue(algorithm.initialization(), "WT1G1 initialization");

        Wt1g1Machine machine = (Wt1g1Machine) network.getMachine("Bus1-mach1");
        assertEquals(-0.00356328, machine.getSlip(), 2.0e-5);
        assertEquals(4, machine.getNamedStates().size());
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine);
        }

        Csv reference = read(REFERENCE);
        assertTrue(!reference.rows().isEmpty());
        System.out.println("WT1G1 initial InterPSS: " + Arrays.toString(actual.get(0)));
        System.out.println("WT1G1 initial PSS/E: " + Arrays.toString(reference.rows().stream()
                .filter(row -> Math.abs(row[0]) < 1.0e-9).findFirst().orElseThrow()));
        double[] maximum = new double[12];
        double[] maximumTime = new double[12];
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
                    value(expected, reference, "SPEED_DEV"),
                    value(expected, reference, "MACHINE_Q"),
                    value(expected, reference, "EP_R"),
                    value(expected, reference, "EP_I"),
                    value(expected, reference, "EPP_R"),
                    value(expected, reference, "EPP_I"),
                    value(expected, reference, "Q_COMP_ADMITTANCE"),
                    value(expected, reference, "TELEC")
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - psse[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = time;
                }
            }
        }
        System.out.println("WT1G1 PSS/E max errors: " + Arrays.toString(maximum));
        System.out.println("WT1G1 PSS/E max-error times: " + Arrays.toString(maximumTime));
        double[] tolerance = {0.0040, 0.0083, 0.027, 0.0031, 5.0e-7, 0.0046,
                0.0070, 0.0028, 0.0074, 0.0034, 0.0012, 0.027};
        String[] labels = {"Bus1 V", "Bus2 V", "P", "Q", "speed", "machine Q",
                "Epr", "Epi", "Eppr", "Eppi", "Q compensation", "Telec"};
        for (int column = 0; column < maximum.length; column++) {
            assertTrue(maximum[column] <= tolerance[column], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", labels[column],
                    maximum[column], maximumTime[column], tolerance[column]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Wt1g1Machine machine) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        Map<String, Double> state = machine.getNamedStates();
        Map<String, Object> diagnostic = machine.getStates(null);
        rows.add(new double[] {time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal(), power.getImaginary(), machine.getSpeed() - 1.0,
                ((Number) diagnostic.get("WT1G1 Machine Q")).doubleValue(),
                state.get("E'd"), state.get("E'q"),
                state.get("E''d"), state.get("E''q"),
                ((Number) diagnostic.get("WT1G1 Q Compensation")).doubleValue(),
                ((Number) diagnostic.get("WT1G1 Telec")).doubleValue()});
    }

    private static void recordWt12(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Wt1g1Machine machine) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        var drive = machine.getDriveTrain();
        rows.add(new double[] {time, drive.getShaftAngle(), drive.getTurbineSpeed() - 1.0,
                drive.getGeneratorSpeed() - 1.0, drive.getGeneratorAngleDeviation(),
                drive.getAerodynamicPower(), machine.getElectricalTorque(),
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal(), power.getImaginary(), machine.getSpeed() - 1.0});
    }

    private static void recordWt12a(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, Wt1g1Machine machine) {
        Complex voltage=network.getBus("Bus1").getVoltage();
        Complex current=machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power=voltage.multiply(current.conjugate());
        var drive=machine.getDriveTrain();
        var aero=drive.getAerodynamicController();
        Map<String,Double> s=aero.getNamedStates();
        rows.add(new double[]{time,s.get("Power filter"),s.get("PI integrator"),
                s.get("Output filter 1"),s.get("Output filter 2"),drive.getShaftAngle(),
                drive.getTurbineSpeed()-1,drive.getGeneratorSpeed()-1,
                drive.getGeneratorAngleDeviation(),drive.getAerodynamicPower(),
                machine.getElectricalTorque(),network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(),power.getReal(),power.getImaginary(),
                machine.getSpeed()-1});
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
