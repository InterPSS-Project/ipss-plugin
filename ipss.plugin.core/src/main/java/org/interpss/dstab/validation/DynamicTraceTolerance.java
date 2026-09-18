package org.interpss.dstab.validation;

/** Error gates for one dynamic signal. */
public record DynamicTraceTolerance(double maximumAbsoluteError, double maximumNormalizedRmse) {
    public DynamicTraceTolerance {
        if (maximumAbsoluteError < 0.0 || maximumNormalizedRmse < 0.0
                || !Double.isFinite(maximumAbsoluteError)
                || !Double.isFinite(maximumNormalizedRmse)) {
            throw new IllegalArgumentException("Trace tolerances must be finite and non-negative");
        }
    }
}
