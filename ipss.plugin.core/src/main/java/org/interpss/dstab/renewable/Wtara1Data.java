package org.interpss.dstab.renewable;

/** Complete two-parameter PSS/E WTARA1 (WECC WTAR_A) record. */
public record Wtara1Data(double ka, double theta0) {
    public Wtara1Data {
        if (ka < 0.0) throw new IllegalArgumentException("WTARA1 Ka must be non-negative");
    }
}
