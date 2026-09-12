package org.interpss.dstab.mach;

/** Six timing and magnitude constants for the GE wind gust/ramp model. */
public record Gewtgdu1Data(double gustStart, double gustDuration,
        double gustMaximum, double rampStart, double rampEnd,
        double rampMaximum) {
    public Gewtgdu1Data {
        double[] values = {gustStart, gustDuration, gustMaximum,
                rampStart, rampEnd, rampMaximum};
        for (double value : values) if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("GEWTGDU1 constants must be finite");
        }
        if (gustStart < 0.0 || gustDuration < 0.0 || rampStart < 0.0
                || rampEnd < rampStart) {
            throw new IllegalArgumentException("GEWTGDU1 times are invalid");
        }
        if (gustMaximum != 0.0 && gustDuration == 0.0) {
            throw new IllegalArgumentException("GEWTGDU1 nonzero gust requires positive duration");
        }
        if (rampMaximum != 0.0 && rampEnd == rampStart) {
            throw new IllegalArgumentException("GEWTGDU1 nonzero ramp requires a time interval");
        }
    }
}
