package org.interpss.dstab.control.pss.ieee.y2016.pss5c;

/** Exact 21-value IEEE/PowerWorld PSS5C parameter representation. */
public record Ieee2016PSS5CStabilizerData(
        BandData veryLowBand,
        BandData lowBand,
        BandData intermediateBand,
        BandData highBand,
        double k1, double k2, double k3,
        double vstmax, double vstmin) {

    public static final int PARAMETER_COUNT = 21;

    public Ieee2016PSS5CStabilizerData {
        if (veryLowBand == null || lowBand == null
                || intermediateBand == null || highBand == null) {
            throw new IllegalArgumentException("PSS5C band data must not be null");
        }
    }

    public static Ieee2016PSS5CStabilizerData fromPowerWorldParameters(double[] p) {
        if (p == null || p.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("PSS5C requires exactly 21 parameters");
        }
        return new Ieee2016PSS5CStabilizerData(
                BandData.fromParameters(p, 0),
                BandData.fromParameters(p, 4),
                BandData.fromParameters(p, 8),
                BandData.fromParameters(p, 12),
                p[16], p[17], p[18], p[19], p[20]);
    }

    public record BandData(double gain, double frequency, double max, double min) {
        private static BandData fromParameters(double[] p, int offset) {
            return new BandData(p[offset], p[offset + 1],
                    p[offset + 2], p[offset + 3]);
        }
    }
}
