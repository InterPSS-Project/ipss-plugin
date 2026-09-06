package org.interpss.dstab.control.pss.ieee.y2005.pss3b;

/** Exact 19-parameter PSS/E PSS3B record. */
public record Ieee2005PSS3BStabilizerData(
        int ics1, int ics2,
        double ks1, double t1, double tw1,
        double ks2, double t2, double tw2, double tw3,
        double a1, double a2, double a3, double a4,
        double a5, double a6, double a7, double a8,
        double vstmax, double vstmin) {
}
