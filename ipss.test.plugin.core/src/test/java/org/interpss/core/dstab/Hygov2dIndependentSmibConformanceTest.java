package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.interpss.dstab.control.gov.psse.hygov2.PsseHygov2dGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/**
 * Full-solver GENROU + native PSS/E HYGOV2D comparison against Independent.
 *
 * <p>The cross-tool fixture uses {@code Kp=0} because Independent 24 initializes
 * both HYGOV2 and HYGOV2D state 1 near {@code -Kp} when it is nonzero, creating
 * an artificial pre-fault gate ramp. {@code Ki} remains nonzero and a zero
 * deadband makes all six published states dynamic; the independent equation
 * test retains nonzero-{@code Kp} coverage.</p>
 */
public class Hygov2dIndependentSmibConformanceTest {
    private static final double STEP = 0.00025;
    private static final double REFERENCE_STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void threeCycleFaultMatchesBoundaryMachineAndAllSixGovernorStates() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_hygov2d_independent.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENROU + HYGOV2D load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "GENROU + HYGOV2D initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        PsseHygov2dGovernor governor = (PsseHygov2dGovernor) machine.getGovernor();
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "GENROU + HYGOV2D solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                    initialRelativeAngle);
        }

        EmbeddedTrajectoryReference reference = EmbeddedTrajectoryReference.embedded("smib-genrou-hygov2d");
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
                reference.fieldIndex("Generator", "1 1", "TSMachineState:6"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:1"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:2"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:3"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:4"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:5"),
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:6")
        };
        int referenceAngle = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int referenceSpeed = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        var initial = reference.postEventSamples().get(0);
        double initialReferenceAngle = initial.value(field[4]) - initial.value(referenceAngle);
        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        double[] referenceMinimum = new double[field.length];
        double[] referenceMaximum = new double[field.length];
        Arrays.fill(referenceMinimum, Double.POSITIVE_INFINITY);
        Arrays.fill(referenceMaximum, Double.NEGATIVE_INFINITY);
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < REFERENCE_STEP
                    || Math.abs(expected.time() - 0.10) < REFERENCE_STEP) continue;
            double[] row = interpolate(actual, expected.time());
            double[] referenceValues = new double[field.length];
            for (int index = 0; index < field.length; index++) {
                referenceValues[index] = expected.value(field[index]);
                referenceMinimum[index] = Math.min(referenceMinimum[index], referenceValues[index]);
                referenceMaximum[index] = Math.max(referenceMaximum[index], referenceValues[index]);
            }
            referenceValues[4] -= expected.value(referenceAngle) + initialReferenceAngle;
            referenceValues[5] -= expected.value(referenceSpeed);
            for (int column = 0; column < maximum.length; column++) {
                double error = Math.abs(row[column + 1] - referenceValues[column]);
                if (error > maximum[column]) {
                    maximum[column] = error;
                    maximumTime[column] = expected.time();
                }
            }
        }
        System.out.println("HYGOV2D Independent max errors: " + Arrays.toString(maximum));
        System.out.println("HYGOV2D Independent max-error times: " + Arrays.toString(maximumTime));
        String[] label = {
                "Bus1 V", "Bus2 V", "P MW", "Q Mvar", "relative angle", "relative speed",
                "Eqp", "PsiDp", "PsiQpp", "Edp", "filter output", "governor",
                "governor speed", "droop", "gate", "penstock"
        };
        double[] tolerance = {
                3.0e-4, 1.0e-4, 8.0e-2, 2.0e-1, 1.5e-2, 6.0e-6,
                3.5e-5, 1.4e-4, 1.1e-4, 7.0e-5,
                8.0e-7, 5.0e-7, 3.0e-7, 2.0e-8, 1.5e-7, 1.0e-7
        };
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(maximum[index] < tolerance[index], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g", label[index], maximum[index],
                    maximumTime[index], tolerance[index]));
        }
        double[] minimumGovernorMovement = {
                5.0e-5, 4.0e-5, 2.0e-5, 1.0e-6, 1.0e-5, 2.0e-6
        };
        for (int index = 0; index < minimumGovernorMovement.length; index++) {
            int column = index + 10;
            double movement = referenceMaximum[column] - referenceMinimum[column];
            assertTrue(movement > minimumGovernorMovement[index], String.format(Locale.ROOT,
                    "%s reference movement %.9g does not exercise the state",
                    label[column], movement));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine, PsseHygov2dGovernor governor,
            double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {
                time,
                network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal() * 100.0, power.getImaginary() * 100.0,
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEq1(),
                machine.getPsikd(), machine.getPsikq(), machine.getEd1(),
                governor.getPiOutput(), governor.getGovernorOutput(), governor.getGovernorSpeed(),
                governor.getTemporaryDroopOutput(), governor.getGatePosition(),
                governor.getPenstockOutput()
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
