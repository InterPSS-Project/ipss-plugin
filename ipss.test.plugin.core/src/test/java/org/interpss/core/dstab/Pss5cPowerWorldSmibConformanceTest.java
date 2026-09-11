package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver GENROU + ESST1A + PSS5C comparison against PowerWorld. */
public class Pss5cPowerWorldSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final String[] STATE_NAMES = {
            "lowIntermediatePosition", "lowIntermediateRate",
            "highPosition", "highRate", "highAcceleration",
            "veryLowUpperLag", "veryLowLowerLag", "lowUpperLag", "lowLowerLag",
            "intermediateUpperLag", "intermediateLowerLag", "highUpperLag", "highLowerLag"
    };
    private static final String[] NATIVE_BAND_NAMES = {
            "k3FVL", "k2FVL", "k3FL", "k2FL",
            "k3FI", "k2FI", "k3FH", "k2FH"
    };

    @Test
    void threeCycleFaultMatchesPowerWorldBoundaryAndPublishedPssResponse() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_pss5c.dyr").toString());
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
        Ieee2016PSS5CStabilizer pss = (Ieee2016PSS5CStabilizer) machine.getStabilizer();
        double initialAngle = machine.getAngle() - referenceMachine.getAngle();
        List<double[]> actual = new ArrayList<>();
        record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                exciter, pss, initialAngle);
        while (algorithm.getSimuTime() < 1.0 - STEP / 2.0) {
            assertTrue(algorithm.solveDEqnStep(true));
            record(actual, algorithm.getSimuTime(), network, machine, referenceMachine,
                    exciter, pss, initialAngle);
        }

        PowerWorldCsvReference reference = PowerWorldCsvReference.read(Path.of(
                "testData", "reference", "powerworld", "smib-genrou-esst1a-pss5c",
                "powerworld.csv"));
        assertEquals(2003, reference.samples().size(), "PowerWorld raw samples");
        assertEquals(2001, reference.postEventSamples().size(), "PowerWorld post-event samples");
        int[] pw = new int[13];
        for (int i = 0; i < pw.length; i++) {
            pw[i] = reference.fieldIndex("Generator", "1 1", "TSStabilizerState:" + (i + 1));
        }
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
        var initial = reference.postEventSamples().get(0);
        double initialPwAngle = initial.value(boundary[4]) - initial.value(refAngle);
        double frequencyBase = 60.0;
        double[] gain = {0.5, 3.0, 20.0, 80.0};
        double[] maximum = new double[25];
        double[] maximumTime = new double[maximum.length];
        for (var expected : reference.postEventSamples()) {
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            double[] target = new double[maximum.length];
            for (int i = 0; i < boundary.length; i++) {
                double value = expected.value(boundary[i]);
                if (i == 4) value -= expected.value(refAngle) + initialPwAngle;
                if (i == 5) value -= expected.value(refSpeed);
                target[i] = value;
            }
            target[14] = expected.value(pw[2]);
            target[15] = expected.value(pw[4]);
            for (int i = 0; i < NATIVE_BAND_NAMES.length; i++) {
                target[16 + i] = expected.value(pw[5 + i]);
            }
            for (int band = 0; band < 4; band++) {
                target[24] += gain[band] * (expected.value(pw[5 + band * 2])
                        - expected.value(pw[6 + band * 2])) / frequencyBase;
            }
            target[24] = Math.max(-0.15, Math.min(0.15, target[24]));
            double[] value = new double[maximum.length];
            System.arraycopy(row, 1, value, 0, boundary.length);
            value[14] = row[19];
            value[15] = row[15] + 1.759e-3 * row[16];
            System.arraycopy(row, 33, value, 16, NATIVE_BAND_NAMES.length);
            value[24] = row[28];
            for (int i = 0; i < maximum.length; i++) {
                double error = Math.abs(value[i] - target[i]);
                if (error > maximum[i]) {
                    maximum[i] = error;
                    maximumTime[i] = expected.time();
                }
            }
        }
        System.out.println("PSS5C PowerWorld max errors: " + Arrays.toString(maximum));
        System.out.println("PSS5C PowerWorld max-error times: " + Arrays.toString(maximumTime));
        double[] tolerance = {
                1.0e-3, 3.3e-4, 0.36, 0.68, 0.099, 2.9e-5,
                1.8e-3, 1.4e-3, 8.0e-4, 5.8e-4,
                0.087, 1.0e-3, 4.5e-3, 1.3e-3,
                3.0e-5, 3.0e-5,
                3.3e-3, 3.3e-3, 3.3e-3, 3.3e-3,
                3.5e-3, 3.5e-3, 3.6e-3, 3.6e-3, 3.4e-5
        };
        String[] label = {
                "Bus1 V", "Bus2 V", "P MW", "Q Mvar", "relative angle",
                "relative speed", "Eqp", "PsiDp", "PsiQpp", "Edp",
                "VA", "sensed Vt", "exciter LL", "exciter LL1",
                "high-frequency transducer", "low/intermediate transducer",
                "k3FVL", "k2FVL", "k3FL", "k2FL",
                "k3FI", "k2FI", "k3FH", "k2FH", "PSS output"
        };
        for (int i = 0; i < maximum.length; i++) {
            assertTrue(maximum[i] < tolerance[i], String.format(Locale.ROOT,
                    "%s max error %.9g exceeded %.9g at %.9g s",
                    label[i], maximum[i], tolerance[i], maximumTime[i]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine,
            IEEE1981ST1Exciter exciter, Ieee2016PSS5CStabilizer pss,
            double initialAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        double[] row = new double[41];
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
        Map<String, Double> states = pss.getNamedStates();
        for (int i = 0; i < STATE_NAMES.length; i++) row[15 + i] = states.get(STATE_NAMES[i]);
        row[28] = pss.getOutput(machine);
        row[29] = pss.getVeryLowOutput(); row[30] = pss.getLowOutput();
        row[31] = pss.getIntermediateOutput(); row[32] = pss.getHighOutput();
        for (int i = 0; i < NATIVE_BAND_NAMES.length; i++) {
            row[33 + i] = states.get(NATIVE_BAND_NAMES[i]);
        }
        rows.add(row);
    }

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int i = 0; i + 1 < rows.size(); i++) {
            double[] lower = rows.get(i), upper = rows.get(i + 1);
            if (Math.abs(lower[0] - target) < 1.0e-9) return lower;
            if (upper[0] > target) {
                double f = (target - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length];
                for (int j = 0; j < result.length; j++) {
                    result[j] = lower[j] + f * (upper[j] - lower[j]);
                }
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
}
