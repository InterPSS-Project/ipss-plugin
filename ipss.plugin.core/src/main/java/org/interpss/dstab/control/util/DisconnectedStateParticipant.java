package org.interpss.dstab.control.util;

/** Explicit allocated-state contract for a qualified disconnected host lifecycle. */
public interface DisconnectedStateParticipant {
    int disconnectedStateCount();
    boolean supportsDisconnectedStateLifecycle();
    void readDisconnectedState(double[] values, int offset);
    /** Pure accepted-state derivative read, called only while the host is active. */
    void readConnectedRates(double[] rates, int offset);
    /** Finite checked coordinate replacement without projection or history updates. */
    void writeDisconnectedState(double[] values, int offset);
    void acceptHostDisconnection();
    void publishHostReactivation();
}
