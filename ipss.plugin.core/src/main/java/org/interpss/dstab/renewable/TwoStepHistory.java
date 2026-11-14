package org.interpss.dstab.renewable;

import java.util.Arrays;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;

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
    private boolean eventPending;
    private boolean eventStep;

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
        eventPending = eventStep = false;
    }

    /** Idempotent; only the next positive integration step consumes this event. */
    void rebaseNextStep() { eventPending = true; }

    void begin(double dt) {
        if (!Double.isFinite(dt) || dt <= 0.0)
            throw new IllegalArgumentException("Two-step integration requires a positive finite step");
        eventStep = eventPending;
        eventPending = false;
        step = dt;
        previousWeight = previousStep == 0.0 ? .5 : .5 * dt / previousStep;
        currentWeight = 1.0 + previousWeight;
        previousStep = dt;
    }

    double advance(int index, double state, double rate) {
        if (eventStep) rate = .5 * (rate + previous[index]);
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

    /** Publish retained integration rates after advance/hold has selected them. */
    void observeRates(IntegrationStateMonitor monitor) {
        monitor.begin(eventStep ? Stage.TWO_STEP_EVENT : Stage.TWO_STEP);
        for (int i = 0; i < previous.length; i++) monitor.recordRate(i, previous[i]);
    }

    /** A collapsed state can use a different semantic observation coordinate. */
    void observeRate(IntegrationStateMonitor monitor, int stateIndex, int observationIndex) {
        monitor.begin(eventStep ? Stage.TWO_STEP_EVENT : Stage.TWO_STEP);
        monitor.recordRate(observationIndex, previous[stateIndex]);
    }

    /**
     * Anchor frozen storage at STATE. At a single-precision event boundary,
     * preserve the preceding rate for the first event average with the new zero
     * rate. Ordinary frozen steps hold STATE and clear derivative history.
     */
    double hold(int index, double state) {
        if (eventStep && singlePrecision) {
            store[index] = (float) (state + offset[index]);
            initialized[index] = true;
            lastState[index] = store[index] - offset[index];
            return advance(index, lastState[index], 0.0);
        }
        previous[index] = 0.0;
        if (singlePrecision) {
            store[index]=(float)(state+offset[index]);initialized[index]=true;
            lastState[index]=store[index]-offset[index];
            return lastState[index];
        }
        return state;
    }
}
