package org.interpss.dstab.renewable;

/** Exact REGCAU2 controls plus effective reactance on the generator MVA base. */
public record Regcau2Data(Regca1Data controls, double xe) {
    public Regcau2Data {
        java.util.Objects.requireNonNull(controls, "REGCAU2 controls");
        if (!Double.isFinite(xe) || xe < 0.)
            throw new IllegalArgumentException("REGCAU2 Xe must be finite and nonnegative");
        for (double value : new double[]{controls.tg(), controls.rrpwr(), controls.brkpt(),
                controls.zerox(), controls.lvpl1(), controls.volim(), controls.lvpnt1(),
                controls.lvpnt0(), controls.iolim(), controls.tfltr(), controls.khv(),
                controls.iqrmax(), controls.iqrmin(), controls.accel()})
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("REGCAU2 constants must be finite");
        if ((controls.lvplsw() != 0 && controls.lvplsw() != 1)
                || controls.accel() <= 0. || controls.accel() > 1.)
            throw new IllegalArgumentException("REGCAU2 requires a binary LVPL flag and 0 < Accel <= 1");
    }
}
