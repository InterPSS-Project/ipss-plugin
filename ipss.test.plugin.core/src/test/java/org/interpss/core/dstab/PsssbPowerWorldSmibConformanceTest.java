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
import org.interpss.core.dstab.reference.PowerWorldCsvReference;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.pss.psse.psssb.PsssbStabilizer;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Full-solver GENROU + ESST1A + WECC PSSSB comparison against PowerWorld. */
public class PsssbPowerWorldSmibConformanceTest {
    private static final double STEP = 0.0005;
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void localizedFaultMatchesPowerWorldBoundaryAndAllActivePsssbStates()
            throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33.raw").toString(),
                CASE.resolve("SMIB_v33_genrou_esst1a_psssb.dyr").toString());
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
        PsssbStabilizer pss = (PsssbStabilizer) machine.getStabilizer();
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
                "testData", "reference", "powerworld", "smib-genrou-esst1a-psssb",
                "powerworld.csv"));
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
                reference.fieldIndex("Generator", "1 1", "TSExciterState:4"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:1"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:2"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:3"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:4"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:5"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:6"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:7"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:8"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:13"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:19"),
                reference.fieldIndex("Generator", "1 1", "TSStabilizerState:20"),
                reference.fieldIndex("Generator", "1 1", "TSVstab")
        };
        int refAngle = reference.fieldIndex("Generator", "2 1", "TSRotorAngle");
        int refSpeed = reference.fieldIndex("Generator", "2 1", "TSSpeed");
        var initial = reference.postEventSamples().get(0);
        double initialPwAngle = initial.value(field[4]) - initial.value(refAngle);
        double[] maximum = new double[field.length];
        double[] maximumTime = new double[field.length];
        for (var expected : reference.postEventSamples()) {
            if (expected.time() > 0.20 + STEP / 2.0) continue;
            if (Math.abs(expected.time() - 0.05) < STEP
                    || Math.abs(expected.time() - 0.10) < STEP) continue;
            double[] row = interpolate(actual, expected.time());
            for (int i = 0; i < field.length; i++) {
                double target = expected.value(field[i]);
                if (i == 4) target -= expected.value(refAngle) + initialPwAngle;
                if (i == 5) target -= expected.value(refSpeed);
                double error = Math.abs(row[i + 1] - target);
                if (error > maximum[i]) {
                    maximum[i] = error;
                    maximumTime[i] = expected.time();
                }
            }
        }
        System.out.println("PSSSB PowerWorld max errors: " + Arrays.toString(maximum));
        double[] tolerance = {
                4.0e-4, 1.5e-4, 0.10, 0.30, 0.018, 8.0e-6,
                8.0e-4, 6.0e-4, 5.0e-5, 3.0e-5,
                4.0e-2, 4.0e-4, 1.4e-3, 6.0e-4,
                8.0e-6, 8.0e-6, 8.0e-6, 7.0e-4, 7.0e-4,
                2.0e-5, 1.2e-4, 8.0e-4, 1.0e-5,
                2.0e-9, 2.0e-9, 8.0e-4
        };
        for (int i = 0; i < maximum.length; i++) {
            assertTrue(maximum[i] < tolerance[i], String.format(Locale.ROOT,
                    "channel %d max error %.9g exceeded %.9g at %.9g s",
                    i, maximum[i], tolerance[i], maximumTime[i]));
        }
    }

    private static void record(List<double[]> rows, double time,
            com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            RoundRotorMachine machine, Machine referenceMachine,
            IEEE1981ST1Exciter exciter, PsssbStabilizer pss, double initialAngle) {
        Complex voltage = network.getBus("Bus1").getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate());
        rows.add(new double[] {
                time, network.getBus("Bus1").getVoltageMag(), network.getBus("Bus2").getVoltageMag(),
                power.getReal() * 100.0, power.getImaginary() * 100.0,
                Math.toDegrees(machine.getAngle() - referenceMachine.getAngle() - initialAngle),
                machine.getSpeed() - referenceMachine.getSpeed(), machine.getEq1(),
                machine.getPsikd(), machine.getPsikq(), machine.getEd1(),
                exciter.getRegulatorOutput(), exciter.getSensedVoltage(),
                exciter.getLeadLagOutput(), exciter.getLeadLag1Output(),
                pss.getInput1Washout1Output(), pss.getInput1Washout2Output(),
                pss.getInput1TransducerOutput(), pss.getInput2Washout1Output(),
                pss.getInput2Washout2Output(), pss.getInput2TransducerOutput(),
                pss.getLeadLag1Output(), pss.getLeadLag2Output(),
                pss.getRampTrackingOutput(), pss.getTransientBoostLagState(),
                pss.getTransientBoostWashoutOutput(), pss.getOutput(machine)
        });
    }

    private static double[] interpolate(List<double[]> rows, double target) {
        for (int i = 0; i + 1 < rows.size(); i++) {
            double[] lower = rows.get(i), upper = rows.get(i + 1);
            if (Math.abs(lower[0] - target) < 1.0e-9) return lower;
            if (upper[0] > target) {
                double fraction = (target - lower[0]) / (upper[0] - lower[0]);
                double[] result = new double[lower.length];
                for (int j = 0; j < result.length; j++) {
                    result[j] = lower[j] + fraction * (upper[j] - lower[j]);
                }
                return result;
            }
        }
        return rows.get(rows.size() - 1);
    }
}
