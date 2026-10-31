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
    /** Per-coordinate event policy; existing participants retain their whole-vector selection. */
    default boolean averageStaggeredRateAtDiscontinuity(int stateIndex) {
        return averageStaggeredRatesAtDiscontinuity();
    }
    /**
     * Average an externally notified input jump with the rate cached at the latest accepted endpoint,
     * rather than with the preceding integration interval's retained rate.
     * Opt in only when no subsequent accepted-history callback changes that RHS.
     * The two-step integration history remains separate and is never replaced.
     * Scheduled network-event boundaries retain their separate qualified policy.
     */
    default boolean useAcceptedEndpointRateAtInputDiscontinuity() { return false; }
    default void completeStaggeredStep(double stepSec) { }
    /** Explicit endpoint clock for discrete histories; existing duration-based participants retain their contract. */
    default void completeStaggeredStep(double stepSec, double endpointTimeSec) {
        completeStaggeredStep(stepSec);
    }
}
