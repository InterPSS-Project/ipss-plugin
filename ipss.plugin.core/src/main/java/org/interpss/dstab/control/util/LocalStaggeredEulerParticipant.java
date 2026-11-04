package org.interpss.dstab.control.util;

/** Explicit local integration within one outer network interval.
 * The shared solver owns reusable numerical staging and derivative-history buffers.
 * Inputs requiring outer-boundary sampling belong to the participant's interval
 * callbacks. Local completion must not accept protection clocks or network events;
 * completeStaggeredStep remains the once-per-outer-interval endpoint callback.
 * The count is immutable after solver initialization. One retains the ordinary path.
 */
public interface LocalStaggeredEulerParticipant extends StaggeredEulerParticipant {
    int staggeredLocalStepCount();
    /** Keep the captured left event derivative in each local slope blend during
     * the first outer interval after a scheduled network discontinuity. The left rate is fixed;
     * the right rate is reevaluated at each local state. This does not replace
     * two-step history or average the physical input. Existing participants
     * blend only the first local slope unless they explicitly opt in. Internal
     * protection/input revisions retain their separate first-local-step policy. */
    default boolean averageNetworkEventRatesThroughoutLocalInterval() { return false; }
    void beginStaggeredLocalInterval(double outerStepSec);
    void completeStaggeredLocalStep(double localStepSec);
}
