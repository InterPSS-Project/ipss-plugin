package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Full-solver GENSAL comparison against the checked-in PowerWorld reference. */
public class GensalPowerWorldSmibConformanceTest {
    private static final double STEP = 1.0 / 240.0;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void threeCycleFaultMatchesPowerWorldBoundaryAndInternalStates() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_gensal.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENSAL SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "GENSAL SMIB initialization");

        SalientPoleMachine machine = (SalientPoleMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "GENSAL solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    initialRelativeAngle);
        }

        PowerWorldCsvReference reference = PowerWorldCsvReference.read(Path.of(
                "testData", "reference", "powerworld", "smib-gensal", "powerworld.csv"));
        int v1 = reference.fieldIndex("Bus", "1", "TSVpu");
        int v2 = reference.fieldIndex("Bus", "2", "TSVpu");
        int p = reference.fieldIndex("Generator", "1 1", "TSMW");
        int q = reference.fieldIndex("Generator", "1 1", "TSMvar");
        int eqp = reference.fieldIndex("Generator", "1 1", "TSMachineState:3");
        int psiDp = reference.fieldIndex("Generator", "1 1", "TSMachineState:4");
        int psiQpp = reference.fieldIndex("Generator", "1 1", "TSMachineState:5");
        int angle1 = reference.fieldIndex("Generator", "1 1", "TSRotorAngle");
        int speed1 = reference.fieldIndex("Generator", "1 1", "TSSpeed");
        int angle2 = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int speed2 = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        var initial = reference.postEventSamples().get(0);
        double initialPowerWorldAngle = initial.value(angle1) - initial.value(angle2);

        double[] maximum = new double[9];
        double[] maximumTime = new double[9];
        for (var expected : reference.postEventSamples()) {
            // PowerWorld emits an algebraic post-event sample at the exact event time,
            // while InterPSS applies the event inside the step ending at that time.
            // Compare the first integrated sample on either side instead of conflating
            // those two output conventions.
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            double[] powerWorld = {
                    expected.value(v1), expected.value(v2), expected.value(p), expected.value(q),
                    expected.value(angle1) - expected.value(angle2) - initialPowerWorldAngle,
                    expected.value(speed1) - expected.value(speed2), expected.value(eqp),
                    expected.value(psiDp), expected.value(psiQpp)
            };
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - powerWorld[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = expected.time();
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "GENSAL PowerWorld max errors: v1=%.9g v2=%.9g pMW=%.9g qMvar=%.9g "
                + "angleDeg=%.9g speed=%.9g eqp=%.9g psiDp=%.9g psiQpp=%.9g%n",
                Arrays.stream(maximum).boxed().toArray());
        System.out.println("GENSAL PowerWorld max-error times: " + Arrays.toString(maximumTime));

        double[] tolerances = {
                5.0e-4, 2.0e-4, 0.25, 0.35, 0.07,
                3.0e-5, 6.0e-5, 2.5e-4, 8.0e-4
        };
        String[] labels = {"Bus1 V", "Bus2 V", "P MW", "Q Mvar", "relative angle",
                "relative speed", "Eqp", "PsiDp", "PsiQpp"};
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(maximum[index] <= tolerances[index], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g",
                    labels[index], maximum[index], maximumTime[index], tolerances[index]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            SalientPoleMachine machine, Machine referenceMachine, double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {
                time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal() * 100.0, power.getImaginary() * 100.0,
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEq1(),
                machine.getPsikd(), machine.getPsiq11()
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
