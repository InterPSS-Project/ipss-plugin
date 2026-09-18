package org.interpss.dstab.relay;

/** One PSS/E LDS3/LVS3 pickup, breaker, and initial-load shedding stage. */
public record LoadSheddingStage(double threshold, double pickupTime,
        double breakerTime, double fraction) {
    public LoadSheddingStage {
        if (!Double.isFinite(threshold) || !Double.isFinite(pickupTime)
                || !Double.isFinite(breakerTime) || !Double.isFinite(fraction)
                || pickupTime < 0.0 || breakerTime < 0.0 || fraction < 0.0) {
            throw new IllegalArgumentException("invalid load-shedding stage");
        }
    }

    public boolean enabled() {
        return threshold > 0.0 && fraction > 0.0;
    }
}
