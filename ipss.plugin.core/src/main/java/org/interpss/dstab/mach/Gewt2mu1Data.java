package org.interpss.dstab.mach;

/** Five constants for the GE two-mass wind-turbine shaft model. */
public record Gewt2mu1Data(double h, double damp, double turbineInertiaFraction,
        double firstShaftFrequencyHz, double shaftDamping) {
    public Gewt2mu1Data {
        double[] values = {h, damp, turbineInertiaFraction,
                firstShaftFrequencyHz, shaftDamping};
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("GEWT2MU1 constants must be finite");
            }
        }
        if (h <= 0.0) throw new IllegalArgumentException("GEWT2MU1 H must be positive");
        if (turbineInertiaFraction < 0.0 || turbineInertiaFraction >= 1.0) {
            throw new IllegalArgumentException("GEWT2MU1 turbine inertia fraction must be in [0,1)");
        }
        if (turbineInertiaFraction > 0.0 && firstShaftFrequencyHz <= 0.0) {
            throw new IllegalArgumentException("GEWT2MU1 shaft frequency must be positive");
        }
        if (shaftDamping < 0.0) {
            throw new IllegalArgumentException("GEWT2MU1 shaft damping must be nonnegative");
        }
    }

    Wt12t1Data asTwoMassData() {
        return new Wt12t1Data(h, damp, turbineInertiaFraction,
                firstShaftFrequencyHz, shaftDamping);
    }
}
