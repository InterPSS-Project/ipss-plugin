package org.interpss.dstab.relay;

/** Native PSS/E FRQTPAT/VTGTPAT threshold and delay data. */
public record GeneratorTripRelayData(
        double lowerThreshold,
        double upperThreshold,
        double pickupTime,
        double breakerTime) {

    public GeneratorTripRelayData {
        if (!Double.isFinite(lowerThreshold) || !Double.isFinite(upperThreshold)
                || lowerThreshold > upperThreshold) {
            throw new IllegalArgumentException(
                    "generator-trip relay thresholds must be finite and lower <= upper");
        }
        if (!Double.isFinite(pickupTime) || !Double.isFinite(breakerTime)
                || pickupTime < 0.0 || breakerTime < 0.0) {
            throw new IllegalArgumentException(
                    "generator-trip relay pickup and breaker times must be finite and nonnegative");
        }
    }
}
