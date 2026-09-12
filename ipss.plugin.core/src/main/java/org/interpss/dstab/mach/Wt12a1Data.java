package org.interpss.dstab.mach;

/** Native PSS/E WT12A1 parameters in published DYR order. */
public record Wt12a1Data(double droop, double kp, double ti, double t1,
        double t2, double tp, double limitMax, double limitMin) {
    public Wt12a1Data {
        double[] values = {droop, kp, ti, t1, t2, tp, limitMax, limitMin};
        for (double value : values) if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("WT12A1 parameters must be finite");
        }
        if (ti <= 0.0 || t1 <= 0.0 || t2 <= 0.0 || tp <= 0.0) {
            throw new IllegalArgumentException("WT12A1 time constants must be positive");
        }
        if (limitMax < limitMin) {
            throw new IllegalArgumentException("WT12A1 limits are reversed");
        }
    }
}
