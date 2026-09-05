package org.interpss.dstab.validation;

/** One value in the canonical long-form dynamic-validation trace. */
public record DynamicTraceSample(
        double timeSeconds,
        String deviceId,
        String signal,
        double value,
        String unit,
        String base) {

    public DynamicTraceSample {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Trace time must be finite and non-negative");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Trace value must be finite");
        }
        deviceId = requireText(deviceId, "deviceId");
        signal = requireText(signal, "signal");
        unit = requireText(unit, "unit");
        base = requireText(base, "base");
    }

    public DynamicTraceKey key() {
        return new DynamicTraceKey(deviceId, signal);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " must be a single line");
        }
        return value.trim();
    }
}
