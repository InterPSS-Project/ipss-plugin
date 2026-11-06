package org.interpss.core.dstab.reference;

/** Root-mean-square error divided by the reference root-mean-square magnitude. */
public final class RelativeRmse {
    public static final double ONE_PERCENT = 0.01;

    private double squaredError;
    private double squaredReference;
    private int samples;

    public void add(double actual, double reference) {
        if (!Double.isFinite(actual) || !Double.isFinite(reference)) {
            throw new IllegalArgumentException("Trajectory samples must be finite");
        }
        double error = actual - reference;
        squaredError += error * error;
        squaredReference += reference * reference;
        samples++;
    }

    public int samples() {
        return samples;
    }

    public double rmse() {
        if (samples == 0) {
            throw new IllegalStateException("No trajectory samples were compared");
        }
        return Math.sqrt(squaredError / samples);
    }

    public double referenceRms() {
        if (samples == 0) {
            throw new IllegalStateException("No trajectory samples were compared");
        }
        return Math.sqrt(squaredReference / samples);
    }

    public double value() {
        if (samples == 0) {
            throw new IllegalStateException("No trajectory samples were compared");
        }
        if (squaredReference == 0.0) {
            return squaredError == 0.0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(squaredError / squaredReference);
    }
}
