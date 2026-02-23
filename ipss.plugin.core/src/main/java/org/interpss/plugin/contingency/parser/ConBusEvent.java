package org.interpss.plugin.contingency.parser;

/**
 * An event record that disconnects an entire bus from the network.
 *
 * <p>When mapped to InterPSS this results in opening all active branches
 * incident to the bus (see {@link ConToIpssMapper}).
 *
 * <p>Example Con file line:
 * <pre>
 *   DISCONNECT BUS 500
 * </pre>
 */
public class ConBusEvent {

    /** integer bus number. */
    private int busNum;

    public ConBusEvent() {}

    public ConBusEvent(int busNum) {
        this.busNum = busNum;
    }

    public int getBusNum()         { return busNum; }

    public void setBusNum(int v)   { this.busNum = v; }

    @Override
    public String toString() {
        return String.format("DISCONNECT BUS %d", busNum);
    }
}
