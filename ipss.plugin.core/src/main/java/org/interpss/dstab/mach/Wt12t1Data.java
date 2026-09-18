package org.interpss.dstab.mach;

/** Native PSS/E WT12T1 parameters in published DYR order. */
public record Wt12t1Data(double h, double damp, double htfrac,
        double freq1Hz, double dshaft) {
    public Wt12t1Data {
        if (!Double.isFinite(h) || !Double.isFinite(damp)
                || !Double.isFinite(htfrac) || !Double.isFinite(freq1Hz)
                || !Double.isFinite(dshaft)) {
            throw new IllegalArgumentException("WT12T1 parameters must be finite");
        }
        if (h <= 0.0) throw new IllegalArgumentException("WT12T1 H must be positive");
        if (htfrac < 0.0 || htfrac >= 1.0) {
            throw new IllegalArgumentException("WT12T1 Htfrac must be in [0, 1)");
        }
        if (htfrac > 0.0 && freq1Hz <= 0.0) {
            throw new IllegalArgumentException("WT12T1 Freq1 must be positive in two-mass mode");
        }
        if (dshaft < 0.0) {
            throw new IllegalArgumentException("WT12T1 Dshaft must be nonnegative");
        }
    }
}
