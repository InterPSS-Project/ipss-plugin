package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/**
 * Common-profile trajectory oracle for the ANDES 2.0 IEEEX1 equations.
 *
 * <p>With sensing, lead-lag, regulator, feedback, and saturation bypassed,
 * both implementations reduce to {@code Te*dEfd/dt = Vr - Ke*Efd}.  The
 * closed-form response provides an independent oracle rather than duplicating
 * either implementation's integration code.</p>
 */
public class Ieeex1AndesEquationConformanceTest extends CorePluginTestSetup {

    @Test
    void unsaturatedFieldStepMatchesAndesEquationOracle() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeex1Exciter exciter = builder.addExcIeeex1("Bus1", "1",
                0.0, 1.0, 0.0, 0.0, 0.0,
                10.0, -10.0, 1.0, 0.2, 0.0, 0.0, 0.0,
                3.0, 0.0, 4.0, 0.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        exciter.setVuel(0.1);
        double dt = 0.0005;
        double maxError = 0.0;
        for (int step = 1; step <= 1000; step++) {
            assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            double time = step * dt;
            double expected = 1.3 - 0.1 * Math.exp(-time / 0.2);
            maxError = Math.max(maxError, Math.abs(exciter.getOutput(machine) - expected));
        }

        assertTrue(maxError < 1.0e-7, "IEEEX1 field trajectory max error=" + maxError);
        assertEquals(1.3 - 0.1 * Math.exp(-2.5), exciter.getOutput(machine), 1.0e-7);
    }
}
