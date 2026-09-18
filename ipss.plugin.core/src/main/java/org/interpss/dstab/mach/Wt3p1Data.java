package org.interpss.dstab.mach;

/** Exact nine-constant WT3P1 pitch-controller record. */
public record Wt3p1Data(double bladeTime, double speedProportionalGain,
        double speedIntegralGain, double compensationProportionalGain,
        double compensationIntegralGain, double thetaMin, double thetaMax,
        double thetaRateMax, double powerSetpoint) {
    public Wt3p1Data {
        if (!finite(bladeTime, speedProportionalGain, speedIntegralGain,
                compensationProportionalGain, compensationIntegralGain,
                thetaMin, thetaMax, thetaRateMax, powerSetpoint)
                || bladeTime < 0.0 || thetaMax < thetaMin || thetaRateMax < 0.0) {
            throw new IllegalArgumentException("invalid WT3P1 data");
        }
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
