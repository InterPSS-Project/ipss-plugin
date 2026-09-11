package org.interpss.dstab.mach;

/** Native PSS/E GENTRA constants in published record order. */
public record GentraData(
        double tdop, double h, double d, double xd, double xq, double xdp,
        double s1, double s12, double accelerationFactor) {

    public GentraData {
        if (!Double.isFinite(tdop) || tdop <= 0.0) {
            throw new IllegalArgumentException("GENTRA T'do must be positive and finite");
        }
        if (!Double.isFinite(h) || h <= 0.0) {
            throw new IllegalArgumentException("GENTRA H must be positive and finite");
        }
        if (!Double.isFinite(d) || !Double.isFinite(xd) || !Double.isFinite(xq)
                || !Double.isFinite(xdp) || !Double.isFinite(s1)
                || !Double.isFinite(s12) || !Double.isFinite(accelerationFactor)) {
            throw new IllegalArgumentException("GENTRA constants must be finite");
        }
        if (!(xd > xdp && xq > xdp && xdp > 0.0)) {
            throw new IllegalArgumentException("GENTRA requires Xd and Xq > X'd > 0");
        }
        if (s1 < 0.0 || s12 < 0.0) {
            throw new IllegalArgumentException("GENTRA saturation factors must be nonnegative");
        }
    }
}
