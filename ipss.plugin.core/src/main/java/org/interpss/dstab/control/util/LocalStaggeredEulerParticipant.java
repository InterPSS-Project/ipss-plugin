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
    void beginStaggeredLocalInterval(double outerStepSec);
    void completeStaggeredLocalStep(double localStepSec);
}
