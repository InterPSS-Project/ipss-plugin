package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver GENROU + ESST1A + PSS4C comparison against Independent. */
public class Pss4cIndependentSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void threeCycleFaultMatchesBoundaryOutputAndSharedBandStates() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_pss4c.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(STEP);
        algorithm.setTotalSimuTimeSec(1.0);
        algorithm.setSimuOutputHandler(new StateMonitor());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Bus1", network, SimpleFaultCode.GROUND_3P,
                new Complex(0.0, 0.2), null, 0.05, 0.05), "SmibFault");
        assertTrue(algorithm.initialization());

        RoundRotorMachine machine = (RoundRotorMachine) network.getMachine("Bus1-mach1");
        Machine referenceMachine = network.getMachine("Bus2-mach1");
        IEEE1981ST1Exciter exciter = (IEEE1981ST1Exciter) machine.getExciter();
        Ieee2016PSS4CStabilizer pss = (Ieee2016PSS4CStabilizer) machine.getStabilizer();
        assertEquals(38, pss.getNamedStates().size());
        List<String> stateNames = List.of(
                "Low band upper lead-lag 1", "Low band upper lead-lag 2",
                "Low band upper lead-lag 3", "Low band lower lead-lag 1",
                "Low band lower lead-lag 2", "Low band lower lead-lag 3",
                "Intermediate band upper lead-lag 1", "Intermediate band upper lead-lag 2",
                "Intermediate band upper lead-lag 3", "Intermediate band lower lead-lag 1",
                "Intermediate band lower lead-lag 2", "Intermediate band lower lead-lag 3",
                "Very low band upper lead-lag 1", "Very low band upper lead-lag 2",
                "Very low band upper lead-lag 3", "Very low band lower lead-lag 1",
                "Very low band lower lead-lag 2", "Very low band lower lead-lag 3");
        assertTrue(pss.getNamedStates().keySet().containsAll(stateNames));
        double initialAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                exciter, pss, stateNames, initialAngle);
        assertLimitsInactive(pss);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    exciter, pss, stateNames, initialAngle);
            assertLimitsInactive(pss);
        }

        EmbeddedTrajectoryReference reference = EmbeddedTrajectoryReference.embedded("smib-genrou-esst1a-pss4c");
        assertTrue(!reference.samples().isEmpty());
        assertTrue(!reference.postEventSamples().isEmpty());
        int[] boundary = {
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
        int refAngle = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int refSpeed = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        int vstab = reference.fieldIndex("Generator", "1 1", "TSVstab");
        int[] slots = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
                33, 34, 35, 36, 37, 38};
        int[] pwState = new int[slots.length];
        for (int i = 0; i < pwState.length; i++)
            pwState[i] = reference.fieldIndex(
                    "Generator", "1 1", "TSStabilizerState:" + slots[i]);
        var initial = reference.postEventSamples().get(0);
        double initialPwAngle = initial.value(boundary[4]) - initial.value(refAngle);
        double initialPwVstab = initial.value(vstab);
        double[] initialPwState = new double[pwState.length];
        for (int i = 0; i < pwState.length; i++)
            initialPwState[i] = initial.value(pwState[i]);
        double initialActualVstab = actual.get(0)[15];
        double[] initialActualState = Arrays.copyOfRange(actual.get(0), 16, 34);
        double[] maximum = new double[15 + pwState.length];
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            for (int i = 0; i < boundary.length; i++) {
                double target = expected.value(boundary[i]);
                if (i == 4) target -= expected.value(refAngle) + initialPwAngle;
                if (i == 5) target -= expected.value(refSpeed);
                maximum[i] = Math.max(maximum[i], Math.abs(row[i + 1] - target));
            }
            maximum[14] = Math.max(maximum[14], Math.abs(
                    (row[15] - initialActualVstab)
                            - (expected.value(vstab) - initialPwVstab)));
            for (int i = 0; i < pwState.length; i++) {
                double target = expected.value(pwState[i]) - initialPwState[i];
                double value = row[16 + i] - initialActualState[i];
                maximum[15 + i] = Math.max(maximum[15 + i], Math.abs(value - target));
            }
        }
        double[] tolerance = {
                5.0e-4, 1.7e-4, 0.085, 0.34, 0.030, 7.0e-6,
                4.0e-4, 4.2e-4, 2.5e-4, 1.9e-4,
                0.011, 5.0e-4, 2.5e-4, 1.0e-4, 0.0013,
                7.5e-5, 6.5e-5, 6.0e-5, 2.6e-5, 2.3e-5, 2.0e-5,
                5.5e-5, 5.0e-5, 4.5e-5, 1.4e-5, 1.3e-5, 1.2e-5,
                7.0e-5, 5.0e-5, 3.0e-5, 1.4e-4, 1.0e-4, 6.0e-5
        };
        List<String> channels = new ArrayList<>(List.of(
                "generator voltage", "infinite-bus voltage", "generator MW",
                "generator Mvar", "relative rotor angle", "relative speed",
                "GENROU Eqp", "GENROU PsiDp", "GENROU PsiQpp", "GENROU Edp",
                "ESST1A VA", "ESST1A sensed Vt", "ESST1A LL", "ESST1A LL1",
                "PSS4C Vstab"));
        channels.addAll(stateNames);
        assertEquals(tolerance.length, maximum.length);
        for (int i = 0; i < maximum.length; i++) {
            assertTrue(Double.isFinite(maximum[i]) && maximum[i] <= tolerance[i],
                    channels.get(i) + " maximum error " + maximum[i]
                            + " exceeds " + tolerance[i]);
        }
    }

    private static void assertLimitsInactive(Ieee2016PSS4CStabilizer pss) {
        assertTrue(Math.abs(pss.outputSignal) < 0.15 - 1.0e-8);
        assertTrue(Math.abs(pss.getLowOutput()) < 0.30 - 1.0e-8);
        assertTrue(Math.abs(pss.getIntermediateOutput()) < 0.30 - 1.0e-8);
        assertTrue(Math.abs(pss.getHighOutput()) < 0.30 - 1.0e-8);
        assertTrue(Math.abs(pss.getVeryLowOutput()) < 0.01 - 1.0e-8);
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine,
            IEEE1981ST1Exciter exciter, Ieee2016PSS4CStabilizer pss,
            List<String> stateNames, double initialAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        double[] row = new double[34];
        row[0] = time;
        row[1] = network.getBus("Bus1").getVoltageMag();
        row[2] = network.getBus("Bus2").getVoltageMag();
        row[3] = power.getReal() * 100.0;
        row[4] = power.getImaginary() * 100.0;
        row[5] = Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialAngle);
        row[6] = machine.getSpeed() - referenceMachine.getSpeed();
        row[7] = machine.getEq1(); row[8] = machine.getPsikd();
        row[9] = machine.getPsikq(); row[10] = machine.getEd1();
        row[11] = exciter.getRegulatorOutput(); row[12] = exciter.getSensedVoltage();
        row[13] = exciter.getLeadLagOutput(); row[14] = exciter.getLeadLag1Output();
        row[15] = pss.getOutput(machine);
        Map<String, Double> states = pss.getNamedStates();
        for (int i = 0; i < stateNames.size(); i++) row[16 + i] = states.get(stateNames.get(i));
        rows.add(row);
    }

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int i = 0; i + 1 < rows.size(); i++) {
            double[] lower = rows.get(i), upper = rows.get(i + 1);
            if (Math.abs(lower[0] - target) < 1.0e-9) return lower;
            if (upper[0] > target) {
                double f = (target - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length];
                for (int j = 0; j < result.length; j++)
                    result[j] = lower[j] + f * (upper[j] - lower[j]);
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
}
