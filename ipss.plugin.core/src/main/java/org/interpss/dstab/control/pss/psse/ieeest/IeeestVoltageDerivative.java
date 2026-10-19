package org.interpss.dstab.control.pss.psse.ieeest;

/** Sampled voltage derivative: held during integration, updated at accepted endpoints. */
public final class IeeestVoltageDerivative {
    private double voltage, derivative, acceptedTime;

    public void initialize(double initialVoltage) {
        requireFinite(initialVoltage);
        voltage = initialVoltage;
        derivative = 0.0;
        acceptedTime = 0.0;
    }

    /** An algebraic event changes the memory, not the held derivative. */
    public void synchronizeVoltage(double settledVoltage) {
        requireFinite(settledVoltage);
        voltage = settledVoltage;
    }

    /** The fixed smoothing coefficient is a sampled rule, not a fitted time constant. */
    public void accept(double time, double settledVoltage, double step) {
        requireFinite(time); requireFinite(settledVoltage); requireFinite(step);
        if (step < 0.0 || time < acceptedTime) throw new IllegalArgumentException("Derivative endpoint cannot move backwards");
        if (step == 0.0 || time == acceptedTime) return;
        derivative = .8 * derivative + .2 * (settledVoltage - voltage) / step;
        voltage = settledVoltage;
        acceptedTime = time;
    }

    public double getVoltage() { return voltage; }
    public double getDerivative() { return derivative; }
    public double getAcceptedTime() { return acceptedTime; }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Derivative endpoint must be finite");
    }
}
