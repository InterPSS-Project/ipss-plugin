package org.interpss.dstab.renewable;

/** Published REECC1/REECCU1 selector and constant order. */
public record Reecc1Data(
        int remoteBus, int pfFlag, int vFlag, int qFlag, int pqFlag,
        double vdip, double vup, double trv, double dbd1, double dbd2,
        double kqv, double iqh1, double iql1, double vref0, double tp,
        double qmax, double qmin, double vmax, double vmin,
        double kqp, double kqi, double kvp, double kvi, double tiq,
        double dpmax, double dpmin, double pmax, double pmin,
        double imax, double tpord,
        double vq1, double iq1, double vq2, double iq2,
        double vq3, double iq3, double vq4, double iq4,
        double vp1, double ip1, double vp2, double ip2,
        double vp3, double ip3, double vp4, double ip4,
        double dischargeTime, double initialSoc, double maximumSoc, double minimumSoc) {

    public Reecc1Data {
        requireFlag(pfFlag, "PFFLAG");
        requireFlag(vFlag, "VFLAG");
        requireFlag(qFlag, "QFLAG");
        requireFlag(pqFlag, "PQFLAG");
        if (trv < 0.0 || tp < 0.0 || tiq < 0.0 || tpord < 0.0) {
            throw new IllegalArgumentException("REECC1 time constants must be non-negative");
        }
        if (!(dischargeTime > 0.0)) {
            throw new IllegalArgumentException("REECC1 battery discharge time must be positive");
        }
        if (maximumSoc < minimumSoc || initialSoc < minimumSoc || initialSoc > maximumSoc) {
            throw new IllegalArgumentException("REECC1 state of charge must lie within its limits");
        }
        double[] values = {vdip, vup, trv, dbd1, dbd2, kqv, iqh1, iql1, vref0,
                tp, qmax, qmin, vmax, vmin, kqp, kqi, kvp, kvi, tiq, dpmax,
                dpmin, pmax, pmin, imax, tpord, vq1, iq1, vq2, iq2, vq3, iq3,
                vq4, iq4, vp1, ip1, vp2, ip2, vp3, ip3, vp4, ip4,
                dischargeTime, initialSoc, maximumSoc, minimumSoc};
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("REECC1 constants must be finite");
            }
        }
    }

    private static void requireFlag(int value, String name) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("REECC1 " + name + " must be 0 or 1");
        }
    }
}
