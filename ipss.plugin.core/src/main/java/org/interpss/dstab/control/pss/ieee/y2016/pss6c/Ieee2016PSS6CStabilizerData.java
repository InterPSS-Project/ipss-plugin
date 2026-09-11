package org.interpss.dstab.control.pss.ieee.y2016.pss6c;

/** PSS6C record: four ICONs plus 30 native CONs or one PowerWorld extension. */
public record Ieee2016PSS6CStabilizerData(
        int ics1, int remoteBus1, int ics2, int remoteBus2,
        double t1, double ks2, double t2, double ks1,
        double t3, double macc, double t4, double td,
        double k0, double k1, double k2, double k3, double k4,
        double ti1, double ti2, double ki3, double ti3,
        double ki4, double ti4, double ks,
        double vsi1max, double vsi1min, double vsi2max, double vsi2min,
        double vstmax, double vstmin,
        double pssActivation, double pssDeactivation,
        double xcomp, double tcomp, double tpgfilt) {

    public static final int PARAMETER_COUNT = 35;
    public static final int LEGACY_PARAMETER_COUNT = 34;

    public static Ieee2016PSS6CStabilizerData fromPsseParameters(double[] p) {
        if (p == null || (p.length != PARAMETER_COUNT
                && p.length != LEGACY_PARAMETER_COUNT)) {
            throw new IllegalArgumentException("PSS6C requires exactly 34 or 35 parameters");
        }
        boolean includesPgenFilter = p.length == PARAMETER_COUNT;
        return new Ieee2016PSS6CStabilizerData(
                exactInt(p[0], "ICS1"), exactInt(p[1], "REMBUS1"),
                exactInt(p[2], "ICS2"), exactInt(p[3], "REMBUS2"),
                p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11],
                p[12], p[13], p[14], p[15], p[16], p[17], p[18], p[19],
                p[20], p[21], p[22], p[23], p[24], p[25], p[26], p[27],
                p[28], p[29], p[30], p[31],
                p[includesPgenFilter ? 33 : 32], p[includesPgenFilter ? 34 : 33],
                includesPgenFilter ? p[32] : 0.0);
    }

    private static int exactInt(double value, String name) {
        int result = (int) value;
        if (!Double.isFinite(value) || result != value) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return result;
    }
}
