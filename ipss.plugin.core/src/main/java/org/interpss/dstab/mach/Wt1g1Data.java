package org.interpss.dstab.mach;

/** Native PSS/E WT1G1 parameters in published DYR order. */
public record Wt1g1Data(
        double tp, double tpp, double x, double xp, double xpp, double xl,
        double e1, double se1, double e2, double se2) {

    private static final double EPS = 1.0e-9;

    public Wt1g1Data {
        requireFinite(tp, "T'");
        requireFinite(tpp, "T''");
        requireFinite(x, "X");
        requireFinite(xp, "X'");
        requireFinite(xpp, "X''");
        requireFinite(xl, "Xl");
        requireFinite(e1, "E1");
        requireFinite(se1, "S(E1)");
        requireFinite(e2, "E2");
        requireFinite(se2, "S(E2)");
        if (tp <= EPS) throw new IllegalArgumentException("WT1G1 T' must be positive");
        if (tpp < 0.0) throw new IllegalArgumentException("WT1G1 T'' must be nonnegative");
        if (!(x > xp && xp > xl && xl >= 0.0)) {
            throw new IllegalArgumentException("WT1G1 reactances must satisfy X > X' > Xl >= 0");
        }
        if (tpp > EPS && xpp > EPS && !(xp > xpp && xpp > xl)) {
            throw new IllegalArgumentException("two-cage WT1G1 must satisfy X' > X'' > Xl");
        }
        if (e1 < 0.0 || se1 < 0.0 || se2 < 0.0 || (se1 > 0.0 && e2 <= e1)) {
            throw new IllegalArgumentException("invalid WT1G1 saturation points");
        }
        if ((tpp <= EPS) != (xpp <= EPS) || tpp <= EPS) {
            // PSS/E treats either zero as the single-cage switch.
            tpp = 0.0;
            xpp = xp;
        }
    }

    public boolean twoCage() {
        return tpp > EPS && xpp > EPS && xpp < xp;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("WT1G1 " + name + " must be finite");
        }
    }
}
