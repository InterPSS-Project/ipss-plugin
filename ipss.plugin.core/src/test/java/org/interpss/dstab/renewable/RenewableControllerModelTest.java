package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.apache.commons.math3.complex.Complex;

class RenewableControllerModelTest {
    private static final double TOL = 1.0e-9;

    @Test
    void reecb1InitializesWithoutAControlTransient() {
        Reecb1Model model = new Reecb1Model(reecb1Data(1.11));

        model.initialize(0.8, 0.2, 1.0);
        model.step(1.0 / 120.0, 0.8, 0.2, 1.0, 1.0);

        assertEquals(0.8, model.getIpcmd(), TOL);
        assertEquals(-0.2, model.getIqcmd(), TOL);
    }

    @Test
    void reecb1HonorsQPriorityAndCurrentMagnitude() {
        Reecb1Model model = new Reecb1Model(reecb1Data(1.0));

        model.initialize(0.9, 0.9, 1.0);
        model.step(1.0 / 120.0, 0.9, 0.9, 1.0, 1.0);

        assertEquals(-0.9, model.getIqcmd(), TOL);
        assertEquals(Math.sqrt(1.0 - 0.9 * 0.9), model.getIpcmd(), TOL);
    }

    @Test
    void reecb1DoesNotPermitNegativeActiveCurrent() {
        Reecb1Model model = new Reecb1Model(reecb1Data(1.0));

        model.initialize(-0.2, 0.0, 1.0);
        model.step(1.0 / 120.0, -0.2, 0.0, 1.0, 1.0);

        assertEquals(0.0, model.getIpcmd(), TOL);
    }

    @Test
    void repca1InitializesIncrementalOutputsAtZero() {
        Repca1Model model = new Repca1Model(repca1Data());

        model.initialize(0.75, 0.1, 1.02);
        model.step(1.0 / 120.0, 0.75, 0.1, 1.02, 1.0);

        assertEquals(0.0, model.getPref(), TOL);
        assertEquals(0.0, model.getQref(), TOL);
    }

    @Test
    void repca1AntiWindupStopsOnlyTheIntegrator() {
        assertEquals(0.4, Repca1Model.integrateWithAntiWindup(
                0.4, 5.0, 0.2, 0.01, 4.0, -1.0, 1.0, false), TOL);
        assertEquals(0.2, Repca1Model.integrateWithAntiWindup(
                0.2, 5.0, 0.2, 0.01, 1.0, -1.0, 1.0, true), TOL);
        assertEquals(0.21, Repca1Model.integrateWithAntiWindup(
                0.2, 5.0, 0.2, 0.01, 1.0, -1.0, 1.0, false), TOL);
    }

    @Test
    void repca1ConvertsSystemBaseInputsAndPreservesModelBaseInputs() {
        assertEquals(1.0, Repca1Model.toModelBase(new Complex(0.5, 0.0), 0, 100.0, 50.0)
                .getReal(), TOL);
        assertEquals(0.5, Repca1Model.toModelBase(new Complex(0.5, 0.0), 1, 100.0, 50.0)
                .getReal(), TOL);
    }

    @Test
    void nonPositiveReecb1CurrentLimitDisablesCircularLimiting() {
        Reecb1Model model = new Reecb1Model(reecb1Data(0.0));
        model.initialize(2.0, 1.0, 1.0);
        model.step(1.0 / 120.0, 2.0, 1.0, 1.0, 1.0);
        assertEquals(2.0, model.getIpcmd(), TOL);
        assertEquals(-1.0, model.getIqcmd(), TOL);
    }

    @Test
    void regca1AppliesLowVoltageGainAtTheNetworkBoundary() {
        assertEquals(0.0, Regca1Model.lowVoltageActiveGain(0.4, 0.4, 0.8), TOL);
        assertEquals(0.5, Regca1Model.lowVoltageActiveGain(0.6, 0.4, 0.8), TOL);
        assertEquals(1.0, Regca1Model.lowVoltageActiveGain(0.8, 0.4, 0.8), TOL);
    }

    @Test
    void regca1HighVoltageLogicIsAlgebraicAndLowerLimited() {
        assertEquals(0.93,
                Regca1Model.highVoltageReactiveOutput(1.0, 1.3, 1.2, 0.7, -1.3), TOL);
        assertEquals(-1.3,
                Regca1Model.highVoltageReactiveOutput(-1.2, 1.5, 1.2, 0.7, -1.3), TOL);
    }

    @Test
    void regfma1VoltagePiUsesConditionalIntegrationAtBothLimits() {
        assertEquals(0.0, Regfma1Model.limitedIntegralRate(
                1.2, 6.0, .1, 0.0, 0.0, 1.2), TOL);
        assertEquals(0.6, Regfma1Model.limitedIntegralRate(
                1.1, 6.0, .1, 0.0, 0.0, 1.2), TOL);
        assertEquals(0.0, Regfma1Model.limitedIntegralRate(
                0.0, 6.0, -.1, 0.0, 0.0, 1.2), TOL);
    }

    private static Reecb1Data reecb1Data(double imax) {
        return new Reecb1Data(
                0, 0, 1, 0, 0,
                -99, 99, 0.02, 0, 0,
                0, 1.1, -1.1, 0, 0.05,
                99, -99, 1.05, 0.9, 0,
                0.01, 10, 60, 0.02, 99,
                -99, 1, 0, imax, 0.02);
    }

    private static Repca1Data repca1Data() {
        return new Repca1Data(
                0, 0, 0, "0", 0, 0, 1,
                0.02, 18, 5, 0, 0.05,
                0, 0, 0, 0.02, 0.1, -0.1,
                -1, 1, 0.43, -0.43, 1, 0.05,
                0.25, -1, 1, 99, -99, 1,
                0, 0.1, 0, 0, 0);
    }
}
