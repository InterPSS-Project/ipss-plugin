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
    private final double[] store;
    private final double[] lastState;
    private final double[] offset;
    private final boolean[] initialized;
    private boolean singlePrecision;

    TwoStepHistory(int states) {
        previous = new double[states];store=new double[states];lastState=new double[states];
        offset=new double[states];initialized=new boolean[states];
    }

    void setSinglePrecision(boolean enabled) { singlePrecision=enabled;reset(); }
    void setOffset(int index,double value) { offset[index]=value;initialized[index]=false; }

    void reset() {
        Arrays.fill(previous, 0.0);
        previousStep = 0.0;
        Arrays.fill(initialized,false);
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
        if (singlePrecision) {
            if (!initialized[index] || state!=lastState[index]) {
                store[index]=(float)(state+offset[index]);
                initialized[index]=true;
            }
            // The native integration base is retained separately from STATE.
            // Rounding STATE alone does not reproduce the STORE recurrence.
            float h=(float)step, f=(float)rate;
            store[index]=(float)((float)store[index]+h*f);
            double result=(float)((float)store[index]+(.5f*h)*f)-offset[index];
            previous[index]=rate;
            lastState[index]=result;
            return result;
        }
        double result = state + step * (currentWeight * rate - previousWeight * previous[index]);
        previous[index] = rate;
        return result;
    }

    /** A model freeze holds the state and discards its pre-freeze derivative. */
    double hold(int index, double state) {
        previous[index] = 0.0;
        if (singlePrecision) {
            store[index]=(float)(state+offset[index]);initialized[index]=true;
            lastState[index]=store[index]-offset[index];
            return lastState[index];
        }
        return state;
    }
}
