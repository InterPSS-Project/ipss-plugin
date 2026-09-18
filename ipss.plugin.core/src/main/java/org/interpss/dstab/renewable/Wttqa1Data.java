package org.interpss.dstab.renewable;

/** Complete 16-parameter PSS/E WTTQA1 (WECC WTTQ_A) record. */
public record Wttqa1Data(
        int tFlag, double kpp, double kip, double tp, double twref,
        double teMax, double teMin,
        double p1, double sp1, double p2, double sp2,
        double p3, double sp3, double p4, double sp4, double turbineMva) {
    public Wttqa1Data {
        if (tFlag != 0 && tFlag != 1) {
            throw new IllegalArgumentException("WTTQA1 TFLAG must be 0 or 1");
        }
        if (tp < 0.0 || twref < 0.0) {
            throw new IllegalArgumentException("WTTQA1 time constants must be non-negative");
        }
        if (teMax < teMin) {
            throw new IllegalArgumentException("WTTQA1 Temax must be >= Temin");
        }
        if (!(p1 < p2 && p2 < p3 && p3 < p4)) {
            throw new IllegalArgumentException("WTTQA1 power breakpoints must increase");
        }
    }
}
