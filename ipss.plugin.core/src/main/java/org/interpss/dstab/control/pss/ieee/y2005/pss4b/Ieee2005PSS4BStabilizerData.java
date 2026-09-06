package org.interpss.dstab.control.pss.ieee.y2005.pss4b;

/** Exact structured representation of the 75-parameter PSS/E PSS4B record. */
public record Ieee2005PSS4BStabilizerData(
        InputTransducerData input,
        BandData lowBand,
        BandData intermediateBand,
        BandData highBand,
        double vstmax,
        double vstmin) {

    public static final int PARAMETER_COUNT = 75;

    public Ieee2005PSS4BStabilizerData {
        if (input == null || lowBand == null || intermediateBand == null || highBand == null) {
            throw new IllegalArgumentException("PSS4B data groups must not be null");
        }
    }

    /** Build data from the PSS/E parameter order following bus, model name and id. */
    public static Ieee2005PSS4BStabilizerData fromParameters(double[] p) {
        if (p == null || p.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("PSS4B requires exactly 75 parameters");
        }
        return new Ieee2005PSS4BStabilizerData(
                new InputTransducerData(
                        p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7],
                        p[8], p[9], p[10], p[11], p[12], p[13], p[14], p[15]),
                BandData.fromParameters(p, 16),
                BandData.fromParameters(p, 35),
                BandData.fromParameters(p, 54),
                p[73], p[74]);
    }

    public record InputTransducerData(
            double cli, double dli, double ali, double bli,
            double bwli1, double wli1, double bwli2, double wli2,
            double th, double ah, double bh, double m,
            double bwh1, double wh1, double bwh2, double wh2) {
    }

    /** One of the low-, intermediate-, or high-frequency PSS4B bands. */
    public record BandData(
            double k1, double k11,
            double t1, double t2, double t3, double t4, double t5, double t6,
            double k2, double k17,
            double t7, double t8, double t9, double t10, double t11, double t12,
            double gain, double max, double min) {

        private static BandData fromParameters(double[] p, int offset) {
            return new BandData(
                    p[offset], p[offset + 1],
                    p[offset + 2], p[offset + 3], p[offset + 4], p[offset + 5],
                    p[offset + 6], p[offset + 7],
                    p[offset + 8], p[offset + 9],
                    p[offset + 10], p[offset + 11], p[offset + 12], p[offset + 13],
                    p[offset + 14], p[offset + 15],
                    p[offset + 16], p[offset + 17], p[offset + 18]);
        }
    }
}
