package org.interpss.dstab.control.util;

/**
 * Explicit initialization action after the initialized operating-point current has
 * been captured. Implementations must not advance time or integrate states.
 * Repeated calls must be idempotent. A true result requests a fresh network
 * solution because an initial injection or topology has changed. Ordinary
 * actions represent physical events. An explicitly declared operating-point
 * preparation runs first and can request qualified source balance reconciliation.
 */
public interface InitialEndpointAction {
    /** A control-mode preparation that establishes the initial operating point,
     * rather than a physical event applied to an already initialized plant.
     * Such preparations settle before ordinary zero-time physical actions.
     * Their source models must explicitly support initial balance reconciliation. */
    default boolean isInitialOperatingPointPreparation() { return false; }

    boolean acceptInitialEndpoint();

    /** Sample the settled initial network after its physical actions, without
     * integrating states, advancing a clock or applying another action. */
    default void acceptInitialNetworkEndpoint() { }
}
