package org.interpss.dstab.renewable;

/** PSS/E REECB1 (WECC REEC_B) electrical-control parameters. */
public record Reecb1Data(
        int remoteBus, int pfFlag, int vFlag, int qFlag, int pqFlag,
        double vdip, double vup, double trv, double dbd1, double dbd2,
        double kqv, double iqh1, double iql1, double vref0, double tp,
        double qmax, double qmin,
        double vmax, double vmin, double kqp, double kqi, double kvp,
        double kvi, double tiq, double dpmax, double dpmin, double pmax,
        double pmin, double imax, double tpord) {

    public Reecb1Data {
        if (trv < 0 || tp < 0 || tiq < 0 || tpord < 0)
            throw new IllegalArgumentException("REECB1 time constants must be non-negative");
        if (iqh1 < iql1 || qmax < qmin || vmax < vmin || pmax < pmin || dpmax < dpmin)
            throw new IllegalArgumentException("REECB1 upper limits must be >= lower limits");
        if (imax <= 0)
            throw new IllegalArgumentException("REECB1 Imax must be positive");
    }
}
