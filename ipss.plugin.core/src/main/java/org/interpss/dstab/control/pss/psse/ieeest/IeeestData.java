package org.interpss.dstab.control.pss.psse.ieeest;

/** Parameters in a PSS/E IEEEST record after the machine identifier. */
public record IeeestData(
        int mode, int remoteBus,
        double a1, double a2, double a3, double a4, double a5, double a6,
        double t1, double t2, double t3, double t4, double t5, double t6,
        double ks, double lsmax, double lsmin, double vcu, double vcl) {
}
