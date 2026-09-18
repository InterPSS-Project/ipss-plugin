package org.interpss.dstab.mach;

/**
 * Parameters for the WECC/PowerWorld GENQEC synchronous-machine model.
 *
 * <p>The constructor applies the validation/autocorrection rules published for
 * PowerWorld's GENQEC implementation. Saturation values are per-unit factors
 * (for example, {@code 0.05}), rather than percentages.</p>
 */
public record GenqecData(
        double h, double d, double ra,
        double xd, double xq, double xdp, double xqp,
        double xdpp, double xqpp, double xl,
        double tdop, double tqop, double tdopp, double tqopp,
        double s1, double s12,
        double rcomp, double xcomp,
        double accel, double kw, int satFunc) {

    private static final double EPS = 1.0e-9;

    public GenqecData {
        if (xd <= 0.0 || xq <= 0.0) {
            throw new IllegalArgumentException("GENQEC Xd and Xq must be positive");
        }
        if (tdop <= 0.0 || tdopp < 0.0 || tqop < 0.0 || tqopp < 0.0) {
            throw new IllegalArgumentException("GENQEC time constants are invalid");
        }

        if (xdp > xd) {
            xdp = 0.8 * xd;
        }
        if (tqop <= EPS || Math.abs(xqp) < EPS) {
            xqp = xq;
        }
        if (xqp > xq) {
            xqp = xq;
        }
        if (tdopp > EPS && xdpp > xdp) {
            xdpp = 0.8 * xdp;
        }
        if (tdopp > EPS && xdpp < 0.05) {
            xdpp = 0.05;
        }
        if (xqpp > xqp) {
            xqpp = 0.8 * xqp;
        }
        if (tdopp > EPS && tqop <= EPS && xqpp > 0.25 * xq) {
            xqpp = 0.25 * xq;
        }
        if (xqpp > 1.5 * xdpp) {
            xqpp = 1.5 * xdpp;
        }
        if (xqpp < 0.01 * xdpp) {
            xqpp = xdpp;
        } else if (xqpp < 0.5 * xdpp) {
            xqpp = 0.5 * xdpp;
        }
        // A zero rotor time constant removes the corresponding winding and
        // makes that subtransient reactance equal to the upstream reactance.
        if (tdopp <= EPS) {
            xdpp = xdp;
        }
        if (tqopp <= EPS) {
            xqpp = xqp;
        }
        double minSubtransientX = Math.min(xdpp, xqpp);
        if (xl > minSubtransientX) {
            xl = 0.8 * minSubtransientX;
        }

        if (xdp <= xl || xqp <= xl || xdpp < xl || xqpp < xl) {
            throw new IllegalArgumentException("GENQEC reactances must satisfy Xd', Xq' > Xl and Xd'', Xq'' >= Xl");
        }
        if (s1 < 0.0 || s12 < 0.0) {
            throw new IllegalArgumentException("GENQEC saturation factors cannot be negative");
        }
        if (kw < 0.0 || kw >= 1.0) {
            throw new IllegalArgumentException("GENQEC Kw must satisfy 0 <= Kw < 1");
        }
        // -1 is the PSLF convention for disabled saturation. Other unsupported
        // values follow the published rule and use exponential saturation.
        if (satFunc < -1 || satFunc > 2) {
            satFunc = 0;
        }
    }
}
