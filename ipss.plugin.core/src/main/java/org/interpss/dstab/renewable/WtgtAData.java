package org.interpss.dstab.renewable;

/** Complete six-parameter PowerWorld WTGT_A wind drive-train record. */
public record WtgtAData(double ht, double hg, double dshaft,
        double kshaft, double mwCap, double w0) {
    public WtgtAData {
        requireFinite("Ht", ht);
        requireFinite("Hg", hg);
        requireFinite("DShaft", dshaft);
        requireFinite("KShaft", kshaft);
        requireFinite("MWCap", mwCap);
        requireFinite("W0", w0);
        if (ht <= 0.0 || hg <= 0.0) {
            throw new IllegalArgumentException("WTGT_A Ht and Hg must be positive");
        }
        if (dshaft < 0.0 || kshaft <= 0.0) {
            throw new IllegalArgumentException(
                    "WTGT_A DShaft must be non-negative and KShaft must be positive");
        }
        if (w0 <= 0.0) throw new IllegalArgumentException("WTGT_A W0 must be positive");
    }

    /** Convert the explicit two-mass data to the equivalent standard WTDTA1 form. */
    public Wtdta1Data toWtdta1Data() {
        double h = ht + hg;
        double htfrac = ht / h;
        double freq1 = Math.sqrt(kshaft * h / (2.0 * ht * hg));
        return new Wtdta1Data(h, 0.0, htfrac, freq1, dshaft);
    }

    public double effectiveModelBaseMva(double machineBaseMva) {
        if (!Double.isFinite(machineBaseMva) || machineBaseMva <= 0.0) {
            throw new IllegalArgumentException("WTGT_A machine MVA base must be positive");
        }
        return mwCap > 0.0 ? mwCap : machineBaseMva;
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("WTGT_A " + name + " must be finite");
        }
    }
}
