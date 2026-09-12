package org.interpss.dstab.mach;

/** Native WT3G1 parameters in published DYR order. */
public record Wt3g1Data(
        int turbineCount, double equivalentReactance, double pllGain,
        double pllIntegratorGain, double pllMaximum, double turbineRatedMw) {

    private static final double EPS = 1.0e-10;

    public Wt3g1Data {
        if (turbineCount <= 0) {
            throw new IllegalArgumentException("WT3G1 turbine count must be positive");
        }
        requireFinite(equivalentReactance, "Xeq");
        requireFinite(pllGain, "Kpll");
        requireFinite(pllIntegratorGain, "KIpll");
        requireFinite(pllMaximum, "Pllmax");
        requireFinite(turbineRatedMw, "Prated");
        if (equivalentReactance <= EPS) {
            throw new IllegalArgumentException("WT3G1 Xeq must be positive");
        }
        if (pllGain < 0.0 || pllIntegratorGain < 0.0 || pllMaximum < 0.0) {
            throw new IllegalArgumentException("WT3G1 PLL gains and limit must be nonnegative");
        }
        if (turbineRatedMw <= EPS) {
            throw new IllegalArgumentException("WT3G1 Prated must be positive");
        }
    }

    public double aggregateRatedMw() {
        return turbineCount * turbineRatedMw;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("WT3G1 " + name + " must be finite");
        }
    }
}
