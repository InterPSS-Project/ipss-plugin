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
    /** Monotonic revision for an internally accepted discontinuous input/regime change.
     * Publish only after accepting the event, never during a trial or getter evaluation.
     * The solver applies the existing per-coordinate discontinuity policy once for
     * this participant without rebasing unrelated models. Reset only on reinitialization. */
    default long staggeredInputDiscontinuityVersion() { return 0; }
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
    /** Whether this owner prepares pre-event endpoint rates in its explicit network-event callback. */
    default boolean providesNetworkEventLeftRates() { return false; }
    /**
     * Purely copy prepared pre-event endpoint derivatives after the converged
     * network-event callback. Return false when none are prepared. These are
     * separate from two-step integration history; coordinate averaging policy
     * still decides which entries are used.
     */
    default boolean readNetworkEventLeftRates(double[] derivatives, int offset) { return false; }
    default void completeStaggeredStep(double stepSec) { }
    /** Explicit endpoint clock for discrete histories; existing duration-based participants retain their contract. */
    default void completeStaggeredStep(double stepSec, double endpointTimeSec) {
        completeStaggeredStep(stepSec);
    }
}
