package org.interpss.dstab.control.pss.ieee.y2005.pss3b;

/** Exact 21-value PSS/E PSS3B record: four ICONs followed by 17 CONs. */
public record Ieee2005PSS3BStabilizerData(
        int ics1, int remoteBus1, int ics2, int remoteBus2,
        double ks1, double t1, double tw1,
        double ks2, double t2, double tw2, double tw3,
        double a1, double a2, double a3, double a4,
        double a5, double a6, double a7, double a8,
        double vstmax, double vstmin) {

    /** Backward-compatible local-signal form used by the programmatic API. */
    public Ieee2005PSS3BStabilizerData(
            int ics1, int ics2,
            double ks1, double t1, double tw1,
            double ks2, double t2, double tw2, double tw3,
            double a1, double a2, double a3, double a4,
            double a5, double a6, double a7, double a8,
            double vstmax, double vstmin) {
        this(ics1, 0, ics2, 0,
                ks1, t1, tw1, ks2, t2, tw2, tw3,
                a1, a2, a3, a4, a5, a6, a7, a8, vstmax, vstmin);
    }
}
