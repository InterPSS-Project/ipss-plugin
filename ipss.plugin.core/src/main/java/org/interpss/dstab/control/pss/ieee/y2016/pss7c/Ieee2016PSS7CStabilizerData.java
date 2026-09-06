package org.interpss.dstab.control.pss.ieee.y2016.pss7c;

/** PSS7C record: four selectors plus 34 or 35 constants. */
public record Ieee2016PSS7CStabilizerData(
        int ics1, int remoteBus1, int ics2, int remoteBus2,
        int m, int n, double ks1, double ks2, double ks3,
        double t6, double t7, double tw1, double tw2, double tw3, double tw4,
        double t8, double t9,
        double k0, double k1, double k2, double k3, double k4,
        double ki3, double ki4, double ti1, double ti2, double ti3, double ti4,
        double vsi1max, double vsi1min, double vsi2max, double vsi2min,
        double vstmax, double vstmin,
        double pssActivation, double pssDeactivation, double tpgfilt,
        double xcomp, double tcomp) {

    public static final int PARAMETER_COUNT = 39;
    public static final int LEGACY_PARAMETER_COUNT = 38;

    public static Ieee2016PSS7CStabilizerData fromPsseParameters(double[] p) {
        if (p == null || (p.length != PARAMETER_COUNT
                && p.length != LEGACY_PARAMETER_COUNT)) {
            throw new IllegalArgumentException("PSS7C requires exactly 38 or 39 parameters");
        }
        boolean includesPgenFilter = p.length == PARAMETER_COUNT;
        return new Ieee2016PSS7CStabilizerData(
                exactInt(p[0], "ICS1"), exactInt(p[1], "REMBUS1"),
                exactInt(p[2], "ICS2"), exactInt(p[3], "REMBUS2"),
                exactInt(p[4], "M"), exactInt(p[5], "N"),
                p[6], p[7], p[8], p[9], p[10], p[11], p[12], p[13], p[14],
                p[15], p[16], p[17], p[18], p[19], p[20], p[21], p[22], p[23],
                p[24], p[25], p[26], p[27], p[28], p[29], p[30], p[31],
                p[32], p[33], p[34], p[35],
                includesPgenFilter ? p[36] : 0.0,
                p[includesPgenFilter ? 37 : 36], p[includesPgenFilter ? 38 : 37]);
    }

    private static int exactInt(double value, String name) {
        int result = (int) value;
        if (!Double.isFinite(value) || result != value) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return result;
    }
}
