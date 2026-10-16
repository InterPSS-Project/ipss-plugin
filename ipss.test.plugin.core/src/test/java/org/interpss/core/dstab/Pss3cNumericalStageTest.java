package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizer;
import org.interpss.dstab.control.pss.psse.ieeest.SecondOrderLeadLagBlock;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Independent state-continuity and simultaneous-Heun checks. */
class Pss3cNumericalStageTest {
    @Test
    void zeroDurationRefreshPreservesStorageAndSamplesNewVoltage() throws Exception {
        Machine machine = initializedMachine();
        var pss = (Ieee2016PSS3CStabilizer) machine.getStabilizer();
        var before = pss.getNamedStates();
        machine.getDStabBus().setVoltage(machine.getDStabBus().getVoltage().multiply(0.9));
        double signal = compensatedSignal(machine, before.get("compensatedFrequencyWashout"));
        double measuredPower = machine.getPe();
        assertTrue(Math.abs(signal) > 1e-6, "The voltage change must excite the washout");
        for (int flag : new int[] {0, 1, 0, 1}) {
            assertTrue(pss.nextStep(0.0, DynamicSimuMethod.MODIFIED_EULER, machine, flag));
            assertEquals(before, pss.getNamedStates(), "Zero elapsed time preserves every state");
            assertEquals(signal, pss.getCompensatedFrequencySignal(), 1e-12);
        }
        double h = 0.0005;
        double initialPower = before.get("generatorPowerFilter");
        pss.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        pss.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
        double ratio = h / 0.05;
        assertEquals(measuredPower + (initialPower - measuredPower) * (1 - ratio + ratio * ratio / 2),
                pss.getFilteredPgen(), 1e-12);
    }

    @Test
    void compensatedFrequencyAndTransducerUseTheSamePredictedEndpoint() throws Exception {
        Machine machine = initializedMachine();
        var pss = (Ieee2016PSS3CStabilizer) machine.getStabilizer();
        double oldState = pss.getNamedStates().get("compensatedFrequencyWashout");
        machine.getDStabBus().setVoltage(machine.getDStabBus().getVoltage().multiply(0.9));
        double f0 = compensatedSignal(machine, oldState);
        double h = 0.0005, tcomp = 0.02, t1 = 0.02, k1 = 20.0;
        // With fixed terminal measurements, f*=f0(1-h/Tcomp). The
        // transducer starts at zero in deviation coordinates.
        double predicted = h * k1 * f0 / t1;
        double expected = 0.5 * h * (k1 * f0 / t1
                + (k1 * f0 * (1 - h / tcomp) - predicted) / t1);
        pss.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        pss.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
        assertEquals(expected, pss.getNamedStates().get("input1Transducer") + k1, 1e-12);
    }

    @Test
    void secondOrderCorrectorUsesBothPredictedCoordinates() {
        var block = new SecondOrderLeadLagBlock(2.0, 4.0, 0.8, 0.4);
        assertTrue(block.initStateU0(0.2));
        // x'= (u-y-2x)/4, y'=x. With h=.1, Euler predicts
        // (x,y)=(.02,.2); Heun gives (.0195,.201).
        block.eulerStep1(1.0, 0.1);
        block.eulerStep2(1.0, 0.1);
        assertEquals(0.0195, block.getFirstState(), 1e-14);
        assertEquals(0.201, block.getSecondState(), 1e-14);
    }

    private static double compensatedSignal(Machine machine, double state) {
        Complex voltage = machine.getDStabBus().getVoltage();
        Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        Complex power = voltage.multiply(current.conjugate()).divide(machine.getIMultiFactor());
        double magnitude = voltage.abs();
        double angle = Math.atan2(0.2 * power.getReal() / magnitude,
                magnitude + 0.2 * power.getImaginary() / magnitude);
        return (angle / 0.02 - state)
                / (2 * Math.PI * machine.getDStabBus().getNetwork().getFrequency());
    }

    private static Machine initializedMachine() throws Exception {
        IpssCorePlugin.init();
        String root = "testData/adpter/psse/v33/SMIB/";
        var context = new PSSEMultiFileLoader().loadDStab(root + "SMIB_v33.raw",
                root + "SMIB_v33_genrou_esst1a_pss3c.dyr");
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(0.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return context.getDStabilityNet().getMachine("Bus1-mach1");
    }
}
