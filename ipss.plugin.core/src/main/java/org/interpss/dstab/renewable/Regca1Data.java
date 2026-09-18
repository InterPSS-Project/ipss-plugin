package org.interpss.dstab.renewable;

/** PSS/E REGCA1 (WECC REGC_A) converter parameters on the generator MVA base. */
public record Regca1Data(
        int lvplsw, double tg, double rrpwr, double brkpt, double zerox,
        double lvpl1, double volim, double lvpnt1, double lvpnt0,
        double iolim, double tfltr, double khv, double iqrmax,
        double iqrmin, double accel) {

    public Regca1Data {
        if (tg < 0 || tfltr < 0) throw new IllegalArgumentException("REGCA1 time constants must be non-negative");
        if (brkpt <= zerox) throw new IllegalArgumentException("REGCA1 Brkpt must exceed Zerox");
        if (lvpnt1 <= lvpnt0) throw new IllegalArgumentException("REGCA1 Lvpnt1 must exceed Lvpnt0");
        if (iqrmax < iqrmin) throw new IllegalArgumentException("REGCA1 Iqrmax must be >= Iqrmin");
    }
}
