package org.interpss.dstab.control.gov.psse.lcfb1;

/** Native PSS/E LCFB1 data: two ICON flags followed by seven CON values. */
public record Lcfb1Data(
        int frequencyBiasFlag,
        int powerControlFlag,
        double frequencyBias,
        double powerTransducerTime,
        double deadband,
        double maximumError,
        double proportionalGain,
        double integralGain,
        double maximumReferenceBias) {

    public Lcfb1Data {
        if ((frequencyBiasFlag != 0 && frequencyBiasFlag != 1)
                || (powerControlFlag != 0 && powerControlFlag != 1)) {
            throw new IllegalArgumentException("LCFB1 flags must be zero or one");
        }
        if (!finite(frequencyBias, powerTransducerTime, deadband, maximumError,
                proportionalGain, integralGain, maximumReferenceBias)
                || powerTransducerTime < 0.0 || deadband < 0.0
                || maximumError < 0.0 || maximumReferenceBias < 0.0) {
            throw new IllegalArgumentException("LCFB1 limits and time constants must be finite and non-negative");
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
