package org.interpss.dstab.control.pss.ieee.y2016.pss3c;

import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizerData;

/** Exact four-ICON plus 22-CON PSS/E PSS3C record. */
public record Ieee2016PSS3CStabilizerData(
        int ics1, int remoteBus1, int ics2, int remoteBus2,
        double k1, double t1, double tw1,
        double k2, double t2, double tw2, double tw3,
        double a1, double a2, double a3, double a4,
        double a5, double a6, double a7, double a8,
        double vstmax, double vstmin,
        double pssActivation, double pssDeactivation,
        double xcomp, double tcomp, double tpgfilt) {

    public static final int PARAMETER_COUNT = 26;

    public static Ieee2016PSS3CStabilizerData fromParameters(double[] p) {
        if (p == null || p.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("PSS3C requires exactly 26 parameters");
        }
        for (int i = 0; i < 4; i++) {
            if (!Double.isFinite(p[i]) || p[i] != Math.rint(p[i])) {
                throw new IllegalArgumentException("PSS3C ICON values must be integers");
            }
        }
        return new Ieee2016PSS3CStabilizerData(
                (int) p[0], (int) p[1], (int) p[2], (int) p[3],
                p[4], p[5], p[6], p[7], p[8], p[9], p[10],
                p[11], p[12], p[13], p[14], p[15], p[16], p[17], p[18],
                p[19], p[20], p[21], p[22], p[23], p[24], p[25]);
    }

    public Ieee2005PSS3BStabilizerData baseData() {
        return new Ieee2005PSS3BStabilizerData(
                ics1, remoteBus1, ics2, remoteBus2,
                k1, t1, tw1, k2, t2, tw2, tw3,
                a1, a2, a3, a4, a5, a6, a7, a8, vstmax, vstmin);
    }
}
