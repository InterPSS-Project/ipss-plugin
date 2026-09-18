package org.interpss.dstab.mach;

/** Exact eight-constant WT3T1 mechanical-system record. */
public record Wt3t1Data(double initialWind, double h, double damp,
        double aerodynamicGain, double pitchAtTwiceRatedWind,
        double turbineInertiaFraction, double firstShaftFrequencyHz,
        double shaftDamping) {
    public Wt3t1Data {
        if (!finite(initialWind, h, damp, aerodynamicGain, pitchAtTwiceRatedWind,
                turbineInertiaFraction, firstShaftFrequencyHz, shaftDamping)
                || initialWind <= 0.0 || h <= 0.0 || damp < 0.0
                || aerodynamicGain < 0.0 || turbineInertiaFraction < 0.0
                || turbineInertiaFraction >= 1.0
                || (turbineInertiaFraction > 0.0 && firstShaftFrequencyHz <= 0.0)
                || shaftDamping < 0.0) {
            throw new IllegalArgumentException("invalid WT3T1 data");
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
