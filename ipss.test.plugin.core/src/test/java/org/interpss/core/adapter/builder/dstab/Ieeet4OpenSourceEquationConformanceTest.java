package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.ieeet4.Ieeet4Exciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

/** Equation checks against the published PowerWorld/WECC IEEET4 diagram. */
public class Ieeet4OpenSourceEquationConformanceTest extends CorePluginTestSetup {

    @Test
    void saturatedNoEventInitializationRemainsAtEquilibrium() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                99.0, 20.0, 0.0005, 5.0, 0.0,
                1.83, 1.0, 2.6, 0.1, 3.45, 0.35);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(3.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        for (int step = 0; step < 500; step++) {
            assertTrue(exciter.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertEquals(3.0, exciter.getOutput(machine), 1.0e-12);
    }

    @Test
    void deadbandTrajectoryMatchesIndependentType4Equations() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                20.0, 0.5, 0.05, 10.0, -10.0,
                0.4, 1.0, 3.0, 0.0, 4.0, 0.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        machine.getDStabBus().setVoltage(new Complex(1.03, 0.0));
        double reference = 1.04;
        double[] x = {1.2, 1.2}; // VRH, EFD
        double dt = 0.0005;
        double maxRheostatError = 0.0;
        double maxFieldError = 0.0;
        for (int step = 0; step < 1000; step++) {
            double[] d0 = derivatives(x, reference, 1.03);
            double[] trial = {x[0] + d0[0] * dt, x[1] + d0[1] * dt};
            double[] d1 = derivatives(trial, reference, 1.03);
            x[0] += 0.5 * (d0[0] + d1[0]) * dt;
            x[1] += 0.5 * (d0[1] + d1[1]) * dt;

            assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maxRheostatError = Math.max(maxRheostatError,
                    Math.abs(exciter.getRheostatOutput() - x[0]));
            maxFieldError = Math.max(maxFieldError,
                    Math.abs(exciter.getOutput(machine) - x[1]));
        }
        assertTrue(maxRheostatError < 1.0e-11, "IEEET4 VRH max error=" + maxRheostatError);
        assertTrue(maxFieldError < 1.0e-11, "IEEET4 EFD max error=" + maxFieldError);
    }

    @Test
    void appliesRateLimitFastContactsAndInitializationLimitExpansion() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                200.0, 10.0, 0.05, -0.1, 0.1,
                1.0, 1.0, 3.0, 0.0, 4.0, 0.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        assertEquals(1.2, exciter.vrmax, 1.0e-12);
        assertEquals(-0.1, exciter.vrmin, 1.0e-12);
        assertEquals(1.0, exciter.rateCommand(1.04, 1.03), 1.0e-12);
        assertEquals(-1.0, exciter.rateCommand(1.03, 1.04), 1.0e-12);
        assertEquals(exciter.vrmax,
                exciter.selectControl(1.10, 1.04, 0.3), 1.0e-12);
        assertEquals(exciter.vrmin,
                exciter.selectControl(0.98, 1.04, 0.3), 1.0e-12);
        assertEquals(0.3,
                exciter.selectControl(1.04, 1.04, 0.3), 1.0e-12);
    }

    @Test
    void appliesPowerWorldMinimumTimeConstantsWithoutMutatingSourceData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                20.0, 0.003, 0.05, 10.0, -10.0,
                0.004, 1.0, 3.0, 0.0, 4.0, 0.0);
        exciter.configureIntegrationStep(0.01, 2.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        assertEquals(0.02, exciter.trh, 1.0e-12);
        assertEquals(0.02, exciter.te, 1.0e-12);
        assertEquals(0.003, exciter.getData().getTrh(), 1.0e-12);
        assertEquals(0.004, exciter.getData().getTe(), 1.0e-12);
    }

    private static double[] derivatives(double[] x, double reference, double voltage) {
        double error = reference - voltage;
        double command = Math.max(-1.0, Math.min(1.0, 20.0 * error));
        double control = error >= 0.05 ? 10.0 : error <= -0.05 ? -10.0 : x[0];
        return new double[] {command / 0.5, (control - x[1]) / 0.4};
    }
}
