package org.interpss.dstab.control.util;

/**
 * Explicit physical-output trial boundary, separate from numerical coordinates.
 * Begin follows the solved left endpoint and precedes derivative integration.
 * Publish follows numerical advancement and precedes the trial network solve.
 * Accept follows a successful physical step; discard restores this owner's trial.
 * Implementations prepare reusable storage, keep ordinary reads/RHS pure, and
 * define their own model-local rollback. This is not whole-network rollback.
 */
public interface PhysicalStepTrial {
    default boolean requiresPhysicalStepTrial() { return true; }
    void beginPhysicalStepTrial(double startTimeSec, double stepSec);
    void publishPhysicalStepTrial();
    /** Pure preflight; all owners validate before any commits. */
    void validatePhysicalStepAcceptance();
    /** Must not throw after successful validation with no intervening mutation. */
    void acceptPhysicalStepTrial();
    /** Idempotent, including after a partially failed begin; restores only this owner. */
    void discardPhysicalStepTrial();
}
