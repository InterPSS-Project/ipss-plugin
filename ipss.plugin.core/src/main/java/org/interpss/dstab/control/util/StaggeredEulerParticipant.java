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
    default void completeStaggeredStep(double stepSec) { }
}
