package org.interpss.dstab.validation;

/** Stable device/signal identity used to join traces from different tools. */
public record DynamicTraceKey(String deviceId, String signal) implements Comparable<DynamicTraceKey> {
    public DynamicTraceKey {
        if (deviceId == null || deviceId.isBlank() || signal == null || signal.isBlank()) {
            throw new IllegalArgumentException("Trace device and signal must not be blank");
        }
        deviceId = deviceId.trim();
        signal = signal.trim();
    }

    @Override
    public int compareTo(DynamicTraceKey other) {
        int device = deviceId.compareTo(other.deviceId);
        return device != 0 ? device : signal.compareTo(other.signal);
    }
}
