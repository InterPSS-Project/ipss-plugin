package org.interpss.dstab.control.pss.psse.st2cut;

/** Parameters in PSS/E ST2CUT record order after the machine identifier. */
public record St2cutData(
        int mode1, int remoteBus1, int mode2, int remoteBus2,
        double k1, double k2, double t1, double t2,
        double t3, double t4, double t5, double t6,
        double t7, double t8, double t9, double t10,
        double vsmax, double vsmin, double vcu, double vcl) {
}
