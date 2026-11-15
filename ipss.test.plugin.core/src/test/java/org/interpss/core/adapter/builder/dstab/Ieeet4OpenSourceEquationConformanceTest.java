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
    void rheostatMotionInsideTheFastContactBandMatchesIndependentEquations() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                0.005, 0.5, 0.05, 10.0, -10.0,
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
                0.02, 20.0, 0.05, -0.1, 0.1,
                1.0, 1.0, 3.0, 0.0, 4.0, 0.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        assertEquals(1.2, exciter.vrmax, 1.0e-12);
        assertEquals(-0.1, exciter.vrmin, 1.0e-12);
        assertEquals(0.0, exciter.rateCommand(1.01, 1.0), 0.0);
        assertEquals(0.0, exciter.rateCommand(0.99, 1.0), 0.0);
        assertEquals(0.0, exciter.rateCommand(0.02, 0.0), 0.0);
        assertEquals(1.0, exciter.rateCommand(1.025, 1.0), 0.0);
        assertEquals(-1.0, exciter.rateCommand(0.975, 1.0), 0.0);
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

    @Test
    void solvesAnEffectiveGainAtEachSaturatedOperatingPoint() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                0.02, 20.0, 0.05, 5.0, -2.0,
                1.0, 0.0, 1.0, 0.1, 2.0, 0.2);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        for (double field : new double[] {1.2, 0.8}) {
            machine.setEfd(field);
            assertTrue(exciter.initStates(machine.getDStabBus(), machine));
            // These saturation points give A = 0 and SE(E) = 0.1 E.
            assertEquals(0.5 / field - 0.1 * field, exciter.ke, 1.0e-12);
            assertEquals(0.5, exciter.getRheostatOutput(), 1.0e-12);
            assertTrue(exciter.nextStep(0.003, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(0.003, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            assertEquals(field, exciter.getExciterFieldState(), 1.0e-12);
            assertEquals(0.0, exciter.getData().getKe(), 0.0);
        }
    }

@Test
    void automaticGainIsUsedByBothIntegrationStagesAfterVoltageDrop() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                0.02, 20.0, 0.05, 5.0, -2.0,
                1.0, 0.0, 1.0, 0.1, 2.0, 0.2);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.getDStabBus().setVoltage(Complex.ONE);
        machine.setEfd(1.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        // A 0.10 pu drop closes the fast raise contact and applies VRMAX = 5.
        machine.getDStabBus().setVoltage(new Complex(0.9, 0.0));
        double dt = 0.01;
        double effectiveGain = 0.4; // VRH(0) / EFD(0) - SE(1) = 0.5 / 1 - 0.1
        double initialFieldRate = 5.0 - (effectiveGain + 0.1);
        double predictedField = 1.0 + dt * initialFieldRate;
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertEquals(predictedField, exciter.getOutput(machine), 1.0e-12);

        // The fitted saturation curve has SE(EFD) = 0.1 * EFD.
        double predictedFieldRate = 5.0 - predictedField * (effectiveGain + 0.1 * predictedField);
        double correctedField = 1.0 + 0.5 * dt * (initialFieldRate + predictedFieldRate);
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        assertEquals(correctedField, exciter.getOutput(machine), 1.0e-12);
        assertEquals(0.0, exciter.getData().getKe(), 0.0);
    }

    @Test
    void zeroInitialFieldDoesNotReuseTheGainFromAnEarlierInitialization() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                0.02, 20.0, 0.05, 5.0, -2.0,
                1.0, 0.0, 1.0, 0.0, 2.0, 0.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.getDStabBus().setVoltage(Complex.ONE);
        machine.setEfd(1.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(0.5, exciter.ke, 1.0e-12);

        machine.setEfd(0.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(0.0, exciter.ke, 0.0);
        assertEquals(0.0, exciter.getData().getKe(), 0.0);
        assertEquals(0.0, exciter.getRheostatOutput(), 0.0);
        double dt = 0.01;
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        assertEquals(0.0, exciter.getOutput(machine), 0.0);

        // The fast contact applies 5 pu. Without gain or saturation, both stages give dEFD/dt = 5.
        machine.getDStabBus().setVoltage(new Complex(0.9, 0.0));
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        assertEquals(0.05, exciter.getOutput(machine), 1.0e-12);
        assertTrue(exciter.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        assertEquals(0.05, exciter.getOutput(machine), 1.0e-12);
    }

    @Test
    void derivesZeroGainFromDefaultCeilingAndKeepsTheFieldAtEquilibrium() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                0.02, 20.0, 0.05, 0.0, -1.0,
                1.0, 0.0, 1.0, 0.1, 2.0, 0.2);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.0);

        assertTrue(exciter.initStates(machine.getDStabBus(), machine));

        // The upper saturation point supplies a 0.4 pu ceiling. The 10% initial
        // rheostat and SE(1.0) = 0.1 give KE = 0.04 / 1.0 - 0.1.
        assertEquals(0.4, exciter.vrmax, 1.0e-12);
        assertEquals(-0.4, exciter.vrmin, 1.0e-12);
        assertEquals(0.04, exciter.getRheostatOutput(), 1.0e-12);
        assertEquals(-0.06, exciter.ke, 1.0e-12);
        assertEquals(0.0, exciter.getData().getKe(), 0.0);
        assertEquals(0.0, exciter.getData().getVrmax(), 0.0);

        for (int step = 0; step < 10; step++) {
            assertTrue(exciter.nextStep(0.0033333, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(exciter.nextStep(0.0033333, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertEquals(1.0, exciter.getOutput(machine), 1.0e-12);
    }

    @Test
    void derivesSymmetricLimitsFromUpperSaturationPointAndEnteredGain() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                0.02, 20.0, 0.05, 0.0, -1.0,
                1.0, 0.2, 1.0, 0.1, 2.0, 0.2);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(1.2);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(0.8, exciter.vrmax, 1.0e-12);
        assertEquals(-0.8, exciter.vrmin, 1.0e-12);
        assertEquals(0.2, exciter.ke, 0.0);
        assertEquals(0.384, exciter.getRheostatOutput(), 1.0e-12);
        assertEquals(0.0, exciter.getData().getVrmax(), 0.0);
        assertEquals(-1.0, exciter.getData().getVrmin(), 0.0);
    }

    @Test
    void usesMagnitudeForSaturationCutoffAndSignedInterceptForFeedback() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieeet4Exciter exciter = builder.addExcIeeet4("Bus1", "1", "IEEET4",
                0.02, 20.0, 0.05, 5.0, -2.0,
                1.0, 0.2, 2.0, 0.12, 4.0, 0.12);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setEfd(2.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(0.4, exciter.getRheostatOutput(), 1.0e-12);

        machine.setEfd(3.0);
        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        double intercept = -2.0 * Math.sqrt(2.0);
        double saturationProduct = 0.24 * Math.pow((3.0 - intercept) / (2.0 - intercept), 2.0);
        assertEquals(0.6 + saturationProduct, exciter.getRheostatOutput(), 1.0e-12);
    }

    private static double[] derivatives(double[] x, double reference, double voltage) {
        double error = reference - voltage;
        double command = error > 0.005 ? 1.0 : error < -0.005 ? -1.0 : 0.0;
        double control = error >= 0.05 ? 10.0 : error <= -0.05 ? -10.0 : x[0];
        return new double[] {command / 0.5, (control - x[1]) / 0.4};
    }
}
