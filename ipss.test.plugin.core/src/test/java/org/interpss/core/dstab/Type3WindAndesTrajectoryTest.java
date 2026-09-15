package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.EmbeddedCsvTrajectoryValues;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.WindControlStack;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Public full-stack Type-3 trajectory comparison fixture shared with ANDES. */
public class Type3WindAndesTrajectoryTest extends CorePluginTestSetup {
    private static final double STEP = 1.0 / 240.0;

    @Test
    void publicType3StackInitializesAndRunsMatchedThreeCycleFault() throws Exception {
        Path directory = Path.of("testData", "adpter", "psse", "v33", "renewable");
        var context = new PSSEMultiFileLoader().loadDStab(
                directory.resolve("regca_reeca_repca_bus1062.raw").toString(),
                directory.resolve("type3_wind_bus1062.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        network.setBypassDataCheck(true);
        network.setAllowGenWithoutMach(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "Type-3 public load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        double simulationStep = Double.parseDouble(System.getProperty(
                "type3.wind.step", Double.toString(STEP)));
        algorithm.setSimuStepSec(simulationStep);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setOutPutPerSteps(1);
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(new String[] {"Bus1", "Bus2"});
        algorithm.setSimuOutputHandler(monitor);
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus2", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 1.0), null, .05, .05), "ThreeCycleFault@Poi");
        assertTrue(algorithm.initialization(), "Type-3 public initialization");

        Regca1Model converter = (Regca1Model) ((DStabGen) network.getBus("Bus1")
                .getContributeGen("1")).getDynamicGenDevice();
        var reeca = converter.getReeca1Controller();
        WindControlStack stack = reeca.getWindControlStack();
        assertNotNull(stack);
        assertNotNull(stack.getDriveTrain());
        assertNotNull(stack.getAerodynamics());
        assertNotNull(stack.getPitchController());
        assertNotNull(stack.getTorqueController());

        List<double[]> trace = new ArrayList<>();
        record(trace, algorithm.getSimuTime(), network.getBus("Bus1").getVoltageMag(),
                network.getBus("Bus2").getVoltageMag(), reeca.getActivePowerOrder(), stack);
        while (algorithm.getSimuTime() < algorithm.getTotalSimuTimeSec() - .5 * simulationStep) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "Type-3 public solve failed at t=" + algorithm.getSimuTime());
            record(trace, algorithm.getSimuTime(), network.getBus("Bus1").getVoltageMag(),
                    network.getBus("Bus2").getVoltageMag(), reeca.getActivePowerOrder(), stack);
        }
        Path output = Path.of("target", "andes-benchmarks", "type3-wind", "interpss-internal.csv");
        Files.createDirectories(output.getParent());
        write(output, trace);
        assertTrue(trace.stream().allMatch(row -> java.util.Arrays.stream(row).allMatch(Double::isFinite)));
        assertTrue(trace.stream().mapToDouble(row -> row[2]).min().orElseThrow() > .85,
                "finite-impedance benchmark must stay outside voltage-dip freeze logic");
        compareWithAndesReference(trace);
    }

    private static void record(List<double[]> trace, double time, double plantVoltage,
            double poiVoltage, double pOrder, WindControlStack stack) {
        var drive = stack.getDriveTrain();
        var aero = stack.getAerodynamics();
        var pitch = stack.getPitchController();
        var torque = stack.getTorqueController();
        double powerError = pOrder - stack.getPref();
        double speedError = pitch.getData().kcc() * powerError
                + stack.getTurbineSpeed() - pitch.getSpeedReference();
        double speedPi = limit(pitch.getData().kpw() * speedError + pitch.getSpeedIntegral(),
                pitch.getData().thetaMin(), pitch.getData().thetaMax());
        double compensationPi = limit(pitch.getData().kpc() * powerError
                        + pitch.getCompensationIntegral(),
                pitch.getData().thetaMin(), pitch.getData().thetaMax());
        trace.add(new double[] {
                time, plantVoltage, poiVoltage,
                drive.getTurbineSpeed(), drive.getGeneratorSpeed(), drive.getShaftTorque(),
                aero.getMechanicalPower(), pitch.getPitch(), speedPi, compensationPi,
                torque.getFilteredPower(), torque.getSpeedReference(), torque.getTorque(),
                torque.getPref()
        });
    }

    private static double limit(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static void compareWithAndesReference(List<double[]> actual) throws Exception {
        List<String> lines = EmbeddedCsvTrajectoryValues.lines(
                "type3-wind-bus1062-fault.csv");
        String[] headings = lines.get(0).split(",");
        // ANDES 2.0 implements VFLAG=1 as PIQ_y -> PIV, with a zero-based PIQ
        // state. PowerWorld/WECC instead use an absolute PIQ voltage reference
        // and subtract Vt_filt before PIV. This is therefore a bounded
        // cross-tool comparison, not a REECA1 conformance oracle. Unaffected
        // Type-3 mechanical controls retain much tighter regression limits.
        // The revised bounds account for the corrected native-PSS/E REPCA1
        // generator-power fallback and same-endpoint staged coupling, which
        // this older single-stage ANDES trace did not use; native PSS/E state
        // trajectories are enforced separately.
        double[] tolerances = {
                0.0, 9.0e-3, 3.5e-3,
                5.0e-6, 7.0e-5, 5.0e-5, 1.0e-7,
                2.3e-3, 3.0e-4, 9.0e-3,
                1.5e-3, 7.0e-7, 2.7e-3, 1.7e-3
        };
        assertTrue(headings.length == tolerances.length,
                "reference/tolerance column mismatch");
        double[] maximumError = new double[headings.length];
        double[] maximumErrorTime = new double[headings.length];
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            double[] expected = Arrays.stream(line.split(","))
                    .mapToDouble(Double::parseDouble).toArray();
            double[] interpolated = interpolate(actual, expected[0]);
            for (int column = 1; column < expected.length; column++) {
                double error = Math.abs(interpolated[column] - expected[column]);
                if (error > maximumError[column]) {
                    maximumError[column] = error;
                    maximumErrorTime[column] = expected[0];
                }
            }
        }
        StringBuilder failures = new StringBuilder();
        for (int column = 1; column < headings.length; column++) {
            System.out.printf(Locale.ROOT, "Type-3 ANDES %-20s maxAbsError=%.9g%n",
                    headings[column], maximumError[column]);
            if (maximumError[column] > tolerances[column]) {
                failures.append(String.format(Locale.ROOT,
                        "%s maximum error %.9g at %.9g s exceeds tolerance %.9g%n",
                        headings[column], maximumError[column], maximumErrorTime[column],
                        tolerances[column]));
            }
        }
        assertTrue(maximumError[1] > 5.0e-3,
                "ANDES 2.0's documented VFLAG=1 equation difference unexpectedly vanished");
        assertTrue(failures.isEmpty(), failures.toString());
    }

    private static double[] interpolate(List<double[]> rows, double time) {
        double[] last = rows.get(rows.size() - 1);
        if (Math.abs(last[0] - time) <= 1.0e-9) {
            double[] result = last.clone();
            result[0] = time;
            return result;
        }
        for (int index = 0; index < rows.size() - 1; index++) {
            double[] lower = rows.get(index);
            double[] upper = rows.get(index + 1);
            if (lower[0] <= time && upper[0] >= time) {
                double fraction = (time - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length];
                result[0] = time;
                for (int column = 1; column < result.length; column++) {
                    result[column] = lower[column]
                            + fraction * (upper[column] - lower[column]);
                }
                return result;
            }
        }
        throw new IllegalArgumentException("reference time outside InterPSS trace: " + time);
    }

    private static void write(Path path, List<double[]> rows) throws Exception {
        String[] headings = {
                "time_s", "Bus1", "Bus2", "WTDTA_WT", "WTDTA_WG",
                "WTDTA_SHAFT_TORQUE", "WTARA_PM", "WTPTA_PITCH",
                "WTPTA_SPEED_PI", "WTPTA_COMP_PI", "WTTQA_PE_FILTER",
                "WTTQA_WREF", "WTTQA_TORQUE", "WTTQA_PREF"
        };
        StringBuilder csv = new StringBuilder(String.join(",", headings)).append('\n');
        for (double[] row : rows) {
            for (int index = 0; index < row.length; index++) {
                if (index > 0) csv.append(',');
                csv.append(String.format(Locale.ROOT, "%.17g", row[index]));
            }
            csv.append('\n');
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }
}
