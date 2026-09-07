package org.interpss.dstab.renewable;

/** Complete five-parameter PSS/E WTDTA1 wind drive-train record. */
public record Wtdta1Data(double h, double damp, double htfrac,
        double freq1, double dshaft) {
    public Wtdta1Data {
        requireFinite("H", h);
        requireFinite("DAMP", damp);
        requireFinite("Htfrac", htfrac);
        requireFinite("Freq1", freq1);
        requireFinite("Dshaft", dshaft);
        if (h <= 0.0) throw new IllegalArgumentException("WTDTA1 H must be positive");
        if (htfrac < 0.0 || htfrac >= 1.0) {
            throw new IllegalArgumentException("WTDTA1 Htfrac must be in [0, 1)");
        }
        if (htfrac > 0.0 && freq1 <= 0.0) {
            throw new IllegalArgumentException("WTDTA1 Freq1 must be positive for two-mass mode");
        }
        if (dshaft < 0.0) {
            throw new IllegalArgumentException("WTDTA1 Dshaft must be non-negative");
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("WTDTA1 " + name + " must be finite");
        }
    }
}
