package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver GENROU + IEESGOD comparison against Independent. */
public class IeesgodIndependentSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void threeCycleFaultMatchesIndependentBoundaryMachineAndGovernorStates() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_ieesgod.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow(), "GENROU + IEESGOD SMIB load flow");
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization(), "GENROU + IEESGOD SMIB initialization");

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        PsseIEESGOSteamTurGovernor governor =
                (PsseIEESGOSteamTurGovernor) machine.getGovernor();
        assertEquals("IEESGOD", governor.getName());
        assertEquals(Set.of("filterBlock", "t3DelayBlock", "t4DelayBlock", "t5DelayBlock",
                "t6DelayBlock"), governor.getNamedStates().keySet());
        double initialRelativeAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                initialRelativeAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true),
                    "GENROU + IEESGOD solve at " + algorithm.getSimuTime());
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine, governor,
                    initialRelativeAngle);
        }

        EmbeddedTrajectoryReference reference = EmbeddedTrajectoryReference.embedded("smib-genrou-ieesgod");
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
                reference.fieldIndex("Generator", "1 1", "TSGovernorState:5")
        };
        int referenceAngle = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int referenceSpeed = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        var initial = reference.postEventSamples().get(0);
        double initialReferenceAngle = initial.value(field[4]) - initial.value(referenceAngle);
        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            double[] referenceValues = new double[field.length];
            for (int index = 0; index < field.length; index++)
                referenceValues[index] = expected.value(field[index]);
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
        System.out.println("IEESGOD Independent max errors: " + Arrays.toString(maximum));
        System.out.println("IEESGOD Independent max-error times: " + Arrays.toString(maximumTime));
        double[] tolerance = {
                2.7e-4, 9.0e-5, 6.5e-2, 1.8e-1, 1.2e-2, 5.0e-6,
                2.7e-5, 1.17e-4, 8.9e-5, 6.0e-5,
                6.5e-4, 3.4e-5, 1.8e-5, 2.0e-7, 1.0e-8
        };
        for (int index = 0; index < maximum.length; index++) {
            assertTrue(maximum[index] < tolerance[index], String.format(Locale.ROOT,
                    "channel %d max error %.9g at %.9g exceeded %.9g",
                    index, maximum[index], maximumTime[index], tolerance[index]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network, RoundRotorMachine machine,
            Machine referenceMachine, PsseIEESGOSteamTurGovernor governor,
            double initialRelativeAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex terminalCurrent = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(terminalCurrent.conjugate());
        rows.add(new double[] {
                time, network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal() * 100.0, power.getImaginary() * 100.0,
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialRelativeAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEq1(), machine.getPsikd(),
                machine.getPsikq(), machine.getEd1(),
                // Independent uses the canonical second-order realization of
                // K1(1+sT2)/((1+sT1)(1+sT3)); InterPSS stores the equivalent
                // cascaded lag states. This is the exact state-coordinate map.
                governor.k1 / governor.t3 * governor.getNamedState("filterBlock")
                        + governor.getNamedState("t3DelayBlock") / governor.t1,
                governor.getNamedState("t3DelayBlock"), governor.getNamedState("t4DelayBlock"),
                governor.getNamedState("t5DelayBlock"), governor.getNamedState("t6DelayBlock")
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
                for (int column = 1; column < result.length; column++)
                    result[column] = lower[column] + fraction * (upper[column] - lower[column]);
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
}
