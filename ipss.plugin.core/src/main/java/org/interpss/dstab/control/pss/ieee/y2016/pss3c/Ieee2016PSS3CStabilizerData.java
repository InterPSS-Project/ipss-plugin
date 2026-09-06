package org.interpss.dstab.control.pss.ieee.y2016.pss3c;

import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizerData;

/** Exact 24-parameter PSS/E PSS3C record. */
public record Ieee2016PSS3CStabilizerData(
        int ics1, int ics2,
        double k1, double t1, double tw1,
        double k2, double t2, double tw2, double tw3,
        double a1, double a2, double a3, double a4,
        double a5, double a6, double a7, double a8,
        double vstmax, double vstmin,
        double pssActivation, double pssDeactivation, double tpgfilt,
        double xcomp, double tcomp) {

    public static final int PARAMETER_COUNT = 24;

    public static Ieee2016PSS3CStabilizerData fromParameters(double[] p) {
        if (p == null || p.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("PSS3C requires exactly 24 parameters");
        }
        if (!Double.isFinite(p[0]) || !Double.isFinite(p[1])
                || p[0] != Math.rint(p[0]) || p[1] != Math.rint(p[1])) {
            throw new IllegalArgumentException("PSS3C input selectors must be integers");
        }
        return new Ieee2016PSS3CStabilizerData(
                (int) p[0], (int) p[1],
                p[2], p[3], p[4], p[5], p[6], p[7], p[8],
                p[9], p[10], p[11], p[12], p[13], p[14], p[15], p[16],
                p[17], p[18], p[19], p[20], p[21], p[22], p[23]);
    }

    public Ieee2005PSS3BStabilizerData baseData() {
        return new Ieee2005PSS3BStabilizerData(
                ics1, ics2, k1, t1, tw1, k2, t2, tw2, tw3,
                a1, a2, a3, a4, a5, a6, a7, a8, vstmax, vstmin);
    }
}
