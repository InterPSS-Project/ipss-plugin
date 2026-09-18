package org.interpss.dstab.mach;

/** Nine constants for the GE wind-turbine aerodynamic conversion model. */
public record Gewtaru1Data(double lambdaMaximum, double lambdaMinimum,
        double pitchMaximum, double pitchMinimum, double conversionTime,
        double airDensity, double bladeRadius, double gearboxRatio,
        double synchronousRpm) {
    public Gewtaru1Data {
        double[] values = {lambdaMaximum, lambdaMinimum, pitchMaximum, pitchMinimum,
                conversionTime, airDensity, bladeRadius, gearboxRatio, synchronousRpm};
        for (double value : values) if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("GEWTARU1 constants must be finite");
        }
        if (lambdaMaximum <= lambdaMinimum || lambdaMinimum < 0.0) {
            throw new IllegalArgumentException("GEWTARU1 lambda limits are invalid");
        }
        if (pitchMaximum < pitchMinimum) {
            throw new IllegalArgumentException("GEWTARU1 pitch limits are reversed");
        }
        if (conversionTime < 0.0 || airDensity <= 0.0 || bladeRadius <= 0.0
                || gearboxRatio <= 0.0 || synchronousRpm <= 0.0) {
            throw new IllegalArgumentException("GEWTARU1 physical constants are invalid");
        }
    }
}
