package org.interpss.dstab.mach;

/** Native WT2E1 parameters in published DYR order. */
public record Wt2e1Data(double speedFilterTime, double powerFilterTime,
        double integratorTime, double proportionalGain,
        double outputMax, double outputMin) {
    public Wt2e1Data {
        double[] values = {speedFilterTime, powerFilterTime, integratorTime,
                proportionalGain, outputMax, outputMin};
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("WT2E1 parameters must be finite");
            }
        }
        if (speedFilterTime < 0.0 || powerFilterTime < 0.0 || integratorTime <= 0.0) {
            throw new IllegalArgumentException(
                    "WT2E1 filter times must be nonnegative and Ti must be positive");
        }
        if (outputMax < outputMin) {
            throw new IllegalArgumentException("WT2E1 limits are reversed");
        }
    }
}
