package org.interpss.dstab.renewable;

/**
 * Explicit controller boundary for converter bundles using two-step integration.
 * Refresh may update algebraic outputs but never advances continuous states,
 * derivative history, timers or accepted limiter memory. Positive-step advance
 * samples the old boundary before advancing upstream controls. Published commands
 * remain the ones owned by that accepted integration stage until the next advance.
 * Implementations reset derivative history when initialized again.
 */
public interface TwoStepElectricalController extends RenewableElectricalController {
    void refreshTwoStep(double p, double q, double voltage, double frequency);
    void stepTwoStep(double step, double p, double q, double voltage, double frequency);
    void rebaseNextTwoStep();
    double getTwoStepIpcmd();
    double getTwoStepIqcmd();
}
