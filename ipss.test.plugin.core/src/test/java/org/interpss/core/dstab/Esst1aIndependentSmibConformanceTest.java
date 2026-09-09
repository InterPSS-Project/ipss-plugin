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
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver GENROU + ESST1A/EXST1 comparisons against Independent references. */
public class Esst1aIndependentSmibConformanceTest {
    private static final double POWER_WORLD_STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void threeCycleFaultMatchesIndependentBoundaryMachineAndExciterStates() throws Exception {
        compareIndependent("SMIB_v33_genrou_esst1a.dyr", "smib-genrou-esst1a",
                "ESST1A", false, POWER_WORLD_STEP);
    }

    @Test
    void exst1ThreeCycleFaultMatchesIndependentBoundaryMachineAndExciterStates() throws Exception {
        compareIndependent("SMIB_v33_genrou_exst1.dyr", "smib-genrou-exst1",
                "EXST1", true, POWER_WORLD_STEP / 2.0);
    }

    private static void compareIndependent(String dyrName, String referenceName,
            String modelName, boolean fourthStateIsRateFeedback, double interpssStep)
            throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve(dyrName).toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(),
                "GENROU + " + modelName + " SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(interpssStep);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(),
                "GENROU + " + modelName + " SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        IEEE1981ST1Exciter exciter = (IEEE1981ST1Exciter) machine.getExciter();
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, exciter,
                initialRelativeAngle, fourthStateIsRateFeedback);
        while (algorithm.getSimuTime() < 1.0 - interpssStep / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "GENROU + " + modelName + " solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, exciter,
                    initialRelativeAngle, fourthStateIsRateFeedback);
        }

        EmbeddedTrajectoryReference reference = EmbeddedTrajectoryReference.embedded(referenceName);
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
                reference.fieldIndex("Generator", "1 1", "TSExciterState:1"),
                reference.fieldIndex("Generator", "1 1", "TSExciterState:2"),
                reference.fieldIndex("Generator", "1 1", "TSExciterState:3"),
                reference.fieldIndex("Generator", "1 1", "TSExciterState:4")
        };
        int referenceAngle = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int referenceSpeed = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        var initial = reference.postEventSamples().get(0);
        double initialReferenceAngle = initial.value(field[4]) - initial.value(referenceAngle);
        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < interpssStep
                    || Math.abs(expected.time() - 0.10) < interpssStep) continue;
            double[] row = interpolate(actual, expected.time());
            double[] referenceValues = new double[field.length];
            for (int index = 0; index < field.length; index++) {
                referenceValues[index] = expected.value(field[index]);
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
        System.out.printf(Locale.ROOT,
                modelName + " Independent max errors: v1=%.9g v2=%.9g pMW=%.9g qMvar=%.9g "
                + "angleDeg=%.9g speed=%.9g eqp=%.9g psiDp=%.9g psiQpp=%.9g "
                + "edp=%.9g va=%.9g sensedV=%.9g ll=%.9g "
                + (fourthStateIsRateFeedback ? "feedback" : "ll1") + "=%.9g%n",
                Arrays.stream(maximum).boxed().toArray());
        System.out.println(modelName + " Independent max-error times: "
                + Arrays.toString(maximumTime));
        double[] tolerances = fourthStateIsRateFeedback
                ? new double[] {4.0e-4, 1.5e-4, 0.10, 0.30, 0.018, 8.0e-6,
                        1.3e-4, 2.0e-4, 2.0e-4, 1.3e-4,
                        5.5e-3, 5.0e-4, 4.5e-4, 1.1e-3}
                : new double[] {4.0e-4, 1.5e-4, 0.10, 0.30, 0.018, 8.0e-6,
                        1.2e-4, 2.0e-4, 2.0e-4, 1.3e-4,
                        6.0e-3, 5.0e-4, 1.5e-4, 5.0e-5};
        String[] labels = {
                "Bus1 V", "Bus2 V", "P MW", "Q Mvar", "relative angle",
                "relative speed", "Eqp", "PsiDp", "PsiQpp", "Edp",
                "VA", "sensed Vt", "LL", fourthStateIsRateFeedback ? "rate feedback" : "LL1"
        };
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(maximum[index] <= tolerances[index], String.format(Locale.ROOT,
                    "%s max error %.9g at %.9g exceeds %.9g",
                    labels[index], maximum[index], maximumTime[index], tolerances[index]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine, IEEE1981ST1Exciter exciter,
            double initialRelativeAngle, boolean fourthStateIsRateFeedback) {
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
                exciter.getRegulatorOutput(), exciter.getSensedVoltage(),
                exciter.getLeadLagOutput(), fourthStateIsRateFeedback
                        ? exciter.getRateFeedback() : exciter.getLeadLag1Output()
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
