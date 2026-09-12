package org.interpss.dstab.mach;

/** Ten constants used by the GEWTPTU1 pitch controller wrapper. */
public record Gewtptu1Data(double outputLagTime,
        double speedProportionalGain, double speedIntegralGain,
        double powerProportionalGain, double powerIntegralGain,
        double pitchMinimum, double pitchMaximum,
        double pitchRateMinimum, double pitchRateMaximum,
        double powerReference) {
    public Gewtptu1Data {
        if (!finite(outputLagTime, speedProportionalGain, speedIntegralGain,
                powerProportionalGain, powerIntegralGain, pitchMinimum,
                pitchMaximum, pitchRateMinimum, pitchRateMaximum,
                powerReference)) {
            throw new IllegalArgumentException("GEWTPTU1 constants must be finite");
        }
        if (outputLagTime < 0.0 || pitchMaximum < pitchMinimum
                || pitchRateMaximum < pitchRateMinimum) {
            throw new IllegalArgumentException("GEWTPTU1 limits are inconsistent");
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
