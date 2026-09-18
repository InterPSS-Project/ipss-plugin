package org.interpss.dstab.renewable;

/** Complete 51-parameter PSS/E REECA1 (WECC REEC_A) record. */
public record Reeca1Data(
        int remoteBus, int pfFlag, int vFlag, int qFlag, int pFlag, int pqFlag,
        double vdip, double vup, double trv, double dbd1, double dbd2,
        double kqv, double iqh1, double iql1, double vref0, double iqfrz,
        double thld, double thld2, double tp, double qmax, double qmin,
        double vmax, double vmin, double kqp, double kqi, double kvp,
        double kvi, double vref1, double tiq, double dpmax, double dpmin,
        double pmax, double pmin, double imax, double tpord,
        double vq1, double iq1, double vq2, double iq2,
        double vq3, double iq3, double vq4, double iq4,
        double vp1, double ip1, double vp2, double ip2,
        double vp3, double ip3, double vp4, double ip4) {

    public Reeca1Data {
        if (trv < 0 || tp < 0 || tiq < 0 || tpord < 0 || thld2 < 0) {
            throw new IllegalArgumentException("REECA1 time constants must be non-negative");
        }
        if (vup < vdip || iqh1 < iql1 || qmax < qmin || vmax < vmin
                || pmax < pmin || dpmax < dpmin) {
            throw new IllegalArgumentException("REECA1 upper limits must be >= lower limits");
        }
        checkFlag(pfFlag, "PFFLAG"); checkFlag(vFlag, "VFLAG");
        checkFlag(qFlag, "QFLAG"); checkFlag(pFlag, "PFLAG");
        checkFlag(pqFlag, "PQFLAG");
    }

    private static void checkFlag(int value, String name) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("REECA1 " + name + " must be 0 or 1");
        }
    }
}
