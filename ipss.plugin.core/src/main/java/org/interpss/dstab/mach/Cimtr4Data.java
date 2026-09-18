package org.interpss.dstab.mach;

/** Native PSS/E CIMTR4 parameters in published record order. */
public record Cimtr4Data(
        double tp, double tpp, double h,
        double x, double xp, double xpp, double xl,
        double e1, double se1, double e2, double se2,
        double d, double synchronousTorque) {

    private static final double EPS = 1.0e-9;

    public Cimtr4Data {
        requireFinite(tp, "T'");
        requireFinite(tpp, "T''");
        requireFinite(h, "H");
        requireFinite(x, "X");
        requireFinite(xp, "X'");
        requireFinite(xpp, "X''");
        requireFinite(xl, "Xl");
        requireFinite(e1, "E1");
        requireFinite(se1, "S(E1)");
        requireFinite(e2, "E2");
        requireFinite(se2, "S(E2)");
        requireFinite(d, "D");
        requireFinite(synchronousTorque, "SYN-TOR");
        if (tp <= EPS) throw new IllegalArgumentException("CIMTR4 T' must be positive");
        if (tpp < 0.0) throw new IllegalArgumentException("CIMTR4 T'' must be nonnegative");
        if (h <= EPS) throw new IllegalArgumentException("CIMTR4 H must be positive");
        if (!(x > xp && xp > xl && xl >= 0.0)) {
            throw new IllegalArgumentException("CIMTR4 reactances must satisfy X > X' > Xl >= 0");
        }
        if (tpp > EPS && xpp > EPS && !(xp > xpp && xpp > xl)) {
            throw new IllegalArgumentException("two-cage CIMTR4 must satisfy X' > X'' > Xl");
        }
        if (e1 < 0.0 || se1 < 0.0 || se2 < 0.0 || (se1 > 0.0 && e2 <= e1)) {
            throw new IllegalArgumentException("invalid CIMTR4 saturation points");
        }
        if ((tpp <= EPS) != (xpp <= EPS)) {
            // PSS/E treats either zero as the single-cage switch.
            tpp = 0.0;
            xpp = xp;
        } else if (tpp <= EPS) {
            tpp = 0.0;
            xpp = xp;
        }
    }

    public boolean twoCage() {
        return tpp > EPS && xpp > EPS && xpp < xp;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("CIMTR4 " + name + " must be finite");
        }
    }
}
