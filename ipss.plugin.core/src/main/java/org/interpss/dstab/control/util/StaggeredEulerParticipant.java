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
    /** Copy the converged left-side derivatives of the latest committed local input
     * discontinuity. Called only when its revision changes, outside a scheduled
     * network event. Do not evaluate inputs or advance history here. Returning false
     * preserves the preceding-interval fallback. The solver keeps its two-step
     * integration history separate from these event-observation derivatives. */
    default boolean readStaggeredInputDiscontinuityLeftRates(double[] derivatives,int offset) { return false; }
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
    /**
     * Preserve the solver's latest accepted endpoint rates before a scheduled
     * network event changes its inputs. Opt in only when the final endpoint
     * evaluation includes all accepted history changes. The event average uses
     * this snapshot while the two-step formula retains its older rate history.
     * Model-prepared network-event rates, when provided, take precedence.
     */
    default boolean useAcceptedEndpointRateAtNetworkDiscontinuity() { return false; }
    /** Whether this owner prepares pre-event endpoint rates in its explicit network-event callback. */
    default boolean providesNetworkEventLeftRates() { return false; }
    /**
     * Purely copy prepared pre-event endpoint derivatives after the converged
     * network-event callback. Return false when none are prepared. These are
     * separate from two-step integration history; coordinate averaging policy
     * still decides which entries are used.
     */
    default boolean readNetworkEventLeftRates(double[] derivatives, int offset) { return false; }
    /**
     * Whether the latest successful accepted-step callback applied a model state
     * projection that invalidates selected two-step slopes. This is a pure query;
     * trials, derivative evaluations and getters must not publish a projection.
     * Existing participants retain their integration history unchanged.
     */
    default boolean hasAcceptedDerivativeHistoryInvalidation() { return false; }
    /**
     * Select a coordinate whose preceding integration slope is invalid after
     * that accepted projection. The solver owns and clears the retained slope;
     * unaffected coordinates preserve their history. Called only when the
     * accepted-step callback succeeded and hasAcceptedDerivativeHistoryInvalidation is true.
     */
    default boolean isAcceptedDerivativeHistoryInvalidated(int stateIndex) { return false; }
    default void completeStaggeredStep(double stepSec) { }
    /** Explicit endpoint clock for discrete histories; existing duration-based participants retain their contract. */
    default void completeStaggeredStep(double stepSec, double endpointTimeSec) {
        completeStaggeredStep(stepSec);
    }
}
