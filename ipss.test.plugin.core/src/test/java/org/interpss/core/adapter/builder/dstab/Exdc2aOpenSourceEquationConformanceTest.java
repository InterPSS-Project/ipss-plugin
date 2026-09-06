package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.exdc2a.Exdc2aExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Equation-level checks against the open-source Dynawo IEEEX2 realization. */
public class Exdc2aOpenSourceEquationConformanceTest extends CorePluginTestSetup {
    private static final int VR = 0;
    private static final int EFD = 1;
    private static final int TF2_LAG = 2;
    private static final int TF1_LAG = 3;

    @Test
    void nonzeroTf2MatchesDynawoTwoPoleRateFeedbackEquations() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Exdc2aExciter exciter = builder.addExcExdc2a("Bus1", "1",
                0.0, 1.0, 0.1, 0.0, 0.0,
                10.0, -10.0, 1.0, 0.2,
                0.1, 0.3, 0.4, 3.0, 0.0, 4.0, 0.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        exciter.setVuel(0.1);
        double[] x = {1.2, 1.2, 1.2, 1.2};
        double dt = 0.0005;
        double maxFieldError = 0.0;
        double maxFeedbackError = 0.0;
        for (int step = 0; step < 1000; step++) {
            double[] d0 = derivatives(x);
            double[] trial = add(x, d0, dt);
            double[] d1 = derivatives(trial);
            for (int i = 0; i < x.length; i++) {
                x[i] += 0.5 * (d0[i] + d1[i]) * dt;
            }

            assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            double expectedFeedback = 0.1 * (x[TF2_LAG] - x[TF1_LAG]) / 0.3;
            maxFieldError = Math.max(maxFieldError,
                    Math.abs(exciter.getOutput(machine) - x[EFD]));
            maxFeedbackError = Math.max(maxFeedbackError,
                    Math.abs(exciter.getRateFeedbackOutput() - expectedFeedback));
        }

        assertTrue(maxFieldError < 1.0e-11, "EXDC2A field max error=" + maxFieldError);
        assertTrue(maxFeedbackError < 1.0e-11,
                "EXDC2A feedback max error=" + maxFeedbackError);
    }

    @Test
    void tf2ZeroBypassesOnlyTheAdditionalLag() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Exdc2aExciter exciter = builder.addExcExdc2a("Bus1", "1",
                0.0, 1.0, 0.1, 0.0, 0.0,
                10.0, -10.0, 1.0, 0.2,
                0.1, 0.3, 0.0, 3.0, 0.0, 4.0, 0.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        exciter.setVuel(0.1);
        exciter.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
        exciter.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, machine, 1);

        assertEquals(exciter.getRegulatorOutput(), exciter.getRateFeedbackLagOutput(), 1.0e-12);
    }

    private static double[] derivatives(double[] x) {
        double feedback = 0.1 * (x[TF2_LAG] - x[TF1_LAG]) / 0.3;
        double error = 1.2 + 0.1 - feedback;
        return new double[] {
                (error - x[VR]) / 0.1,
                (x[VR] - x[EFD]) / 0.2,
                (x[VR] - x[TF2_LAG]) / 0.4,
                (x[TF2_LAG] - x[TF1_LAG]) / 0.3
        };
    }

    private static double[] add(double[] x, double[] dx, double dt) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) result[i] = x[i] + dx[i] * dt;
        return result;
    }
}
