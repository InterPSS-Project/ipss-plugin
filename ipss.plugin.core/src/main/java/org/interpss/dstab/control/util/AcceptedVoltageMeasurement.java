package org.interpss.dstab.control.util;

/** Sampled measurement history owned by accepted network endpoints, never trial stages. */
public interface AcceptedVoltageMeasurement {
    boolean isActive();
    void acceptVoltageDerivativeEndpoint(double time, double step);
    void synchronizeVoltageDerivativeMemory();
}
