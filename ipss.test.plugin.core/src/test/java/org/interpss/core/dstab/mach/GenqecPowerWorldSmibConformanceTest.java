package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.interpss.core.dstab.reference.PowerWorldCsvReference.Sample;
import org.interpss.dstab.mach.GenqecMachine;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Full-solver GENQEC comparison against the immutable PowerWorld reference. */
public class GenqecPowerWorldSmibConformanceTest {
    private static final double STEP = 0.00025;
    private static final double POWERWORLD_STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "powerworld", "smib-genqec", "powerworld.csv");

    private static final double XQ = 1.87;
    private static final double XQP = 0.52;
    private static final double XDPP = 0.28;
    private static final double XQPP = 0.20;
    private static final double XL = 0.19;
    private static final double TQOP = 0.10;
    private static final double TQOPP = 0.02;
    private static final double S1 = 0.233;
    private static final double S12 = 0.797;

    @Test
    void threeCycleFaultMatchesPowerWorldBoundaryAndAllMachineStates() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genqec_powerworld.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENQEC SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "GENQEC SMIB initialization");

        GenqecMachine machine = (GenqecMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        assertEquals(0.0, machine.getGenqecData().accel(), 1.0e-12,
                "PowerWorld documents Accel as an unused numerical tuning field");
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "GENQEC solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    initialRelativeAngle);
        }

        PowerWorldCsvReference reference = PowerWorldCsvReference.read(REFERENCE);
        assertTrue(!reference.samples().isEmpty());
        assertTrue(!reference.postEventSamples().isEmpty());
        int[] field = {
                reference.fieldIndex("Bus", "1", "TSVpu"),
                reference.fieldIndex("Bus", "2", "TSVpu"),
                reference.fieldIndex("Generator", "1 1", "TSMW"),
                reference.fieldIndex("Generator", "1 1", "TSMvar"),
                reference.fieldIndex("Generator", "1 1", "TSRotorAngle"),
                reference.fieldIndex("Generator", "1 1", "TSSpeed"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:3"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:4"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:5"),
                reference.fieldIndex("Generator", "1 1", "TSMachineState:6")
        };
        int referenceAngle = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int referenceSpeed = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        var initial = reference.postEventSamples().get(0);
        double initialPowerWorldAngle = initial.value(field[4]) - initial.value(referenceAngle);

        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        double[] maximumDelta = new double[field.length];
        double[] initialActual = Arrays.copyOfRange(actual.get(0), 1, actual.get(0).length);
        double[] initialPowerWorld = {
                initial.value(field[0]), initial.value(field[1]),
                initial.value(field[2]), initial.value(field[3]), 0.0,
                initial.value(field[5]) - initial.value(referenceSpeed),
                initial.value(field[6]), initial.value(field[7]),
                initial.value(field[8]), initial.value(field[9])
        };
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            double[] powerWorld = {
                    expected.value(field[0]), expected.value(field[1]),
                    expected.value(field[2]), expected.value(field[3]),
                    expected.value(field[4]) - expected.value(referenceAngle)
                            - initialPowerWorldAngle,
                    expected.value(field[5]) - expected.value(referenceSpeed),
                    expected.value(field[6]), expected.value(field[7]),
                    expected.value(field[8]), expected.value(field[9])
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - powerWorld[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = expected.time();
                }
                maximumDelta[column] = Math.max(maximumDelta[column], Math.abs(
                        (row[column + 1] - initialActual[column])
                                - (powerWorld[column] - initialPowerWorld[column])));
            }
        }
        System.out.printf(Locale.ROOT,
                "GENQEC PowerWorld max errors: v1=%.9g v2=%.9g pMW=%.9g qMvar=%.9g "
                + "angleDeg=%.9g speed=%.9g eqp=%.9g psiDp=%.9g edp=%.9g psiQp=%.9g%n",
                Arrays.stream(maximum).boxed().toArray());
        System.out.println("GENQEC PowerWorld max-error times: " + Arrays.toString(maximumTime));
        System.out.println("GENQEC PowerWorld max delta errors: " + Arrays.toString(maximumDelta));

        double[] tolerances = {
                1.6e-3, 6.0e-4, 1.35, 1.10, 0.25,
                9.0e-5, 2.1e-3, 2.3e-3, 1.8e-2, 8.0e-3
        };
        String[] labels = {"Bus1 V", "Bus2 V", "P MW", "Q Mvar", "relative angle",
                "relative speed", "Eqp", "PsiDp", "Edp", "PsiQp"};
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(maximum[index] <= tolerances[index], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g",
                    labels[index], maximum[index], maximumTime[index], tolerances[index]));
        }
    }

    @Test
    void powerWorld24EdpExportIsAnXdppLegacyCoordinate() throws Exception {
        PowerWorldCsvReference reference = PowerWorldCsvReference.read(REFERENCE);
        List<Sample> samples = reference.postEventSamples();
        int voltage = reference.fieldIndex("Bus", "1", "TSVpu");
        int power = reference.fieldIndex("Generator", "1 1", "TSMW");
        int reactivePower = reference.fieldIndex("Generator", "1 1", "TSMvar");
        int speed = reference.fieldIndex("Generator", "1 1", "TSSpeed");
        int edp = reference.fieldIndex("Generator", "1 1", "TSMachineState:5");
        int psiQp = reference.fieldIndex("Generator", "1 1", "TSMachineState:6");

        // Simulator 24.2025.3.25 labels state 5 Edp, but its derivative uses
        // Xd'' in the q-axis correction coefficient. State 6 independently
        // reconstructs Iq, so this is neither initialization nor curve fitting.
        for (double time : new double[] {0.0505, 0.0550, 0.1005, 0.1050}) {
            Sample lower = sampleAt(samples, time - POWERWORLD_STEP);
            Sample sample = sampleAt(samples, time);
            Sample upper = sampleAt(samples, time + POWERWORLD_STEP);
            double psiQDot = (upper.value(psiQp) - lower.value(psiQp))
                    / (2.0 * POWERWORLD_STEP);
            double edDot = (upper.value(edp) - lower.value(edp))
                    / (2.0 * POWERWORLD_STEP);
            double v = sample.value(voltage);
            double p = sample.value(power) / 100.0;
            double q = sample.value(reactivePower) / 100.0;
            double omega = sample.value(speed);
            double airGapFlux = Math.hypot(v + XL * q / v, XL * p / v) / omega;
            double exponent = Math.log(S12 / S1) / Math.log(1.2);
            double sat = 1.0 + S1 * Math.pow(airGapFlux, exponent);
            double qError = psiQDot * TQOPP / sat;
            double iq = (qError + sample.value(psiQp) - sample.value(edp))
                    * sat / (XQP - XL);

            double installedCoordinate = edDot(sample.value(edp), iq, qError, sat, XDPP);
            double publishedCoordinate = edDot(sample.value(edp), iq, qError, sat, XQPP);
            assertEquals(installedCoordinate, edDot, 1.0e-2,
                    "PowerWorld 24 legacy Edp derivative at t=" + time);
            assertTrue(Math.abs(publishedCoordinate - edDot) > 1.0,
                    "fixture must discriminate published Xq'' from legacy Xd'' at t=" + time);
        }
    }

    private static double edDot(double edp, double iq, double qError,
            double sat, double correctionSubtransientX) {
        double correction = (XQP - correctionSubtransientX)
                / Math.pow(XQP - XL, 2.0) * qError;
        return sat * (-edp + (XQ - XQP) * (iq / sat - correction)) / TQOP;
    }

    private static Sample sampleAt(List<Sample> samples, double time) {
        return samples.stream()
                .filter(sample -> Math.abs(sample.time() - time) < 1.0e-9)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing sample at " + time));
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            GenqecMachine machine, Machine referenceMachine, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {
                time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal() * 100.0, power.getImaginary() * 100.0,
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(),
                machine.getEq1(), machine.getPsikd(), machine.getEd1(), machine.getPsikq()
        });
    }

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int index = 0; index < rows.size(); index++) {
            double[] lower = rows.get(index);
            if (Math.abs(lower[0] - target) < 1.0e-9) return lower;
            if (index + 1 < rows.size() && rows.get(index + 1)[0] > target) {
                double[] upper = rows.get(index + 1);
                double fraction = (target - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length];
                result[0] = target;
                for (int column = 1; column < result.length; column++) {
                    result[column] = lower[column] + fraction * (upper[column] - lower[column]);
                }
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
}
