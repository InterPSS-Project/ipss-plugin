package org.interpss.dstab.renewable;

import java.util.Arrays;

/**
 * Explicit two-step derivative history for the opt-in renewable integration path.
 * Startup uses a zero previous derivative, as observed in the commercial
 * three-state lag probe. A zero-duration algebraic refresh must not call begin.
 */
final class TwoStepHistory {
    private final double[] previous;
    private double previousStep;
    private double step;
    private double currentWeight;
    private double previousWeight;

    TwoStepHistory(int states) { previous = new double[states]; }

    void reset() {
        Arrays.fill(previous, 0.0);
        previousStep = 0.0;
    }

    void begin(double dt) {
        if (!Double.isFinite(dt) || dt <= 0.0)
            throw new IllegalArgumentException("Two-step integration requires a positive finite step");
        step = dt;
        previousWeight = previousStep == 0.0 ? .5 : .5 * dt / previousStep;
        currentWeight = 1.0 + previousWeight;
        previousStep = dt;
    }

    double advance(int index, double state, double rate) {
        double result = state + step * (currentWeight * rate - previousWeight * previous[index]);
        previous[index] = rate;
        return result;
    }

    /** A model freeze holds the state and discards its pre-freeze derivative. */
    double hold(int index, double state) {
        previous[index] = 0.0;
        return state;
    }
}
