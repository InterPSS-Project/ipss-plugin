package org.interpss.dstab.control.util;

/**
 * Explicit zero-time input-history boundary after a scheduled network event has
 * converged, before integration resumes. Implementations must be idempotent for
 * repeated notifications at the same physical time and must not advance a clock,
 * timer or differential state. An algebraically bypassed coordinate may project
 * to the event's input value. Ordinary derivative/current evaluation stays pure.
 */
public interface NetworkEventInput {
    void acceptNetworkEventInput(double physicalTimeSec);
}
