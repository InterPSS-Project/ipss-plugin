package org.interpss.dstab.renewable;

/** Published REGCB1/REGCBU1 selector and constant order. */
public record Regcb1Data(
        int rateFlag, int pqFlag,
        double tg, double tfltr, double iqrmax, double iqrmin,
        double rrpwr, double te, double imax) {

    public Regcb1Data {
        if (!binary(rateFlag) || !binary(pqFlag)) {
            throw new IllegalArgumentException("REGCB1 selectors must be zero or one");
        }
        if (!finite(tg, tfltr, iqrmax, iqrmin, rrpwr, te, imax)) {
            throw new IllegalArgumentException("REGCB1 constants must be finite");
        }
        if (tg <= 0.0 || tfltr < 0.0 || iqrmax <= 0.0 || iqrmin >= 0.0
                || rrpwr <= 0.0 || te < 0.0 || imax <= 0.0) {
            throw new IllegalArgumentException("REGCB1 limits or time constants are invalid");
        }
    }

    private static boolean binary(int value) { return value == 0 || value == 1; }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
