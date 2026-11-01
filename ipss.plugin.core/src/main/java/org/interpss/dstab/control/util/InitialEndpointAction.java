package org.interpss.dstab.control.util;

/**
 * Explicit physical action after the initialized operating-point current has
 * been captured. Implementations must not advance time or integrate states.
 * Repeated calls must be idempotent. A true result requests a fresh network
 * solution because an initial physical injection or topology has changed.
 */
public interface InitialEndpointAction {
    boolean acceptInitialEndpoint();

    /** Sample the settled initial network after its physical actions, without
     * integrating states, advancing a clock or applying another action. */
    default void acceptInitialNetworkEndpoint() { }
}
