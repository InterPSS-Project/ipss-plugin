package org.interpss.threePhase.basic;

import org.apache.commons.math3.complex.Complex;

/** Leakage primitive on winding-one's power base, including zero/negative star arms. */
public final class ThreeWindingTransformerAdmittance {
    private ThreeWindingTransformerAdmittance() { }

    /** Pair impedances are per unit on one common power base and respective rated voltages. */
    public static Complex[][] perUnit(Complex z12, Complex z13, Complex z23) {
        // Use winding 1 as the voltage reference. Inverting this two-loop matrix
        // avoids division by individual star arms, which can legitimately be zero.
        Complex mutual = z12.add(z13).subtract(z23).multiply(0.5);
        Complex det = z12.multiply(z13).subtract(mutual.multiply(mutual));
        if (!Double.isFinite(det.abs()) || det.abs() <= 1e-14 * Math.max(z12.abs() * z13.abs(), 1e-30))
            throw new IllegalArgumentException("Singular three-winding leakage impedance");
        Complex a = z13.divide(det), b = mutual.negate().divide(det), c = z12.divide(det);
        return new Complex[][]{
            {a.add(b.multiply(2)).add(c), a.add(b).negate(), b.add(c).negate()},
            {a.add(b).negate(), a, b},
            {b.add(c).negate(), b, c}
        };
    }
}
