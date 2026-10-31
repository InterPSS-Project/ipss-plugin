package org.interpss.dstab.control.util;

/**
 * Differential-state boundary for a controller integrated by the shared
 * staggered solver. Evaluation may refresh algebraic outputs, but must not
 * advance a state, timer, transport history, or committed limiter reference.
 * Buffers and derivative history belong to the enclosing solver.
 */
public interface StaggeredEulerParticipant {
    int staggeredStateCount();
    void readStaggeredState(double[] values, int offset);
    void evaluateStaggered(double stepSec, double[] derivatives, int offset);
    void applyStaggeredState(double[] values, int offset, double stepSec);
    /** Some sampled-input models rebase measurement memory instead of averaging slopes. */
    default boolean averageStaggeredRatesAtDiscontinuity() { return true; }
    default void completeStaggeredStep(double stepSec) { }
    /** Explicit endpoint clock for discrete histories; existing duration-based participants retain their contract. */
    default void completeStaggeredStep(double stepSec, double endpointTimeSec) {
        completeStaggeredStep(stepSec);
    }
}
