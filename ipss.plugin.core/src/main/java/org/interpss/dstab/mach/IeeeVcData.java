package org.interpss.dstab.mach;

/** Native PSS/E IEEEVC constants in published record order. */
public record IeeeVcData(double rc, double xc) {
    public IeeeVcData {
        if (!Double.isFinite(rc) || !Double.isFinite(xc)) {
            throw new IllegalArgumentException("IEEEVC RC and XC must be finite");
        }
    }
}
