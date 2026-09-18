package org.interpss.dstab.mach;

import java.util.Arrays;

/** Native PSS/E WT2G1 parameters in published DYR order. */
public record Wt2g1Data(
        double xa, double xm, double x1, double rotorResistance,
        double maximumRotorResistance, double e1, double se1, double e2, double se2,
        double[] powerReference, double[] slip) {

    private static final double EPS = 1.0e-10;

    public Wt2g1Data {
        requireFinite(xa, "XA");
        requireFinite(xm, "XM");
        requireFinite(x1, "X1");
        requireFinite(rotorResistance, "R_ROT_MACH");
        requireFinite(maximumRotorResistance, "R_ROT_MAX");
        requireFinite(e1, "E1");
        requireFinite(se1, "SE1");
        requireFinite(e2, "E2");
        requireFinite(se2, "SE2");
        if (xa < 0.0 || xm <= EPS || x1 <= EPS) {
            throw new IllegalArgumentException("WT2G1 requires XA >= 0 and XM/X1 > 0");
        }
        if (rotorResistance <= EPS || maximumRotorResistance < rotorResistance) {
            throw new IllegalArgumentException(
                    "WT2G1 rotor resistance must satisfy R_ROT_MAX >= R_ROT_MACH > 0");
        }
        if (e1 < 0.0 || se1 < 0.0 || se2 < 0.0 || (se1 > 0.0 && e2 <= e1)) {
            throw new IllegalArgumentException("invalid WT2G1 saturation points");
        }
        powerReference = validateCurve(powerReference, "power reference");
        slip = validateCurve(slip, "slip");
        for (int i = 1; i < powerReference.length; i++) {
            if (powerReference[i] < powerReference[i - 1]) {
                throw new IllegalArgumentException("WT2G1 power-reference coordinates must be ordered");
            }
        }
    }

    @Override
    public double[] powerReference() { return powerReference.clone(); }

    @Override
    public double[] slip() { return slip.clone(); }

    public double transientReactance() {
        return xa + xm * x1 / (xm + x1);
    }

    /** Positive generator speed deviation corresponding to the requested power. */
    public double speedDeviation(double power) {
        if (power <= powerReference[0]) return slip[0];
        int last = powerReference.length - 1;
        if (power >= powerReference[last]) return slip[last];
        for (int i = 1; i <= last; i++) {
            if (power <= powerReference[i]) {
                double width = powerReference[i] - powerReference[i - 1];
                if (width <= EPS) return slip[i];
                double fraction = (power - powerReference[i - 1]) / width;
                return slip[i - 1] + fraction * (slip[i] - slip[i - 1]);
            }
        }
        return slip[last];
    }

    /** Requested power corresponding to a generator speed deviation. */
    public double powerAtSpeedDeviation(double speedDeviation) {
        int nearest = 0;
        double nearestDistance = Math.abs(speedDeviation - slip[0]);
        for (int i = 1; i < slip.length; i++) {
            double lower = slip[i - 1];
            double upper = slip[i];
            if (speedDeviation >= Math.min(lower, upper)
                    && speedDeviation <= Math.max(lower, upper)) {
                double width = upper - lower;
                if (Math.abs(width) <= EPS) return powerReference[i];
                double fraction = (speedDeviation - lower) / width;
                return powerReference[i - 1]
                        + fraction * (powerReference[i] - powerReference[i - 1]);
            }
            double distance = Math.abs(speedDeviation - slip[i]);
            if (distance < nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        return powerReference[nearest];
    }

    private static double[] validateCurve(double[] values, String name) {
        if (values == null || values.length != 5) {
            throw new IllegalArgumentException("WT2G1 " + name + " curve must contain five values");
        }
        double[] copy = Arrays.copyOf(values, values.length);
        for (double value : copy) requireFinite(value, name);
        return copy;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("WT2G1 " + name + " must be finite");
        }
    }
}
