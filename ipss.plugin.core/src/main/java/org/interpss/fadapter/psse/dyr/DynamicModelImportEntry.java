package org.interpss.fadapter.psse.dyr;

import java.util.Objects;

/** Source-located import result for one DYR record. */
public record DynamicModelImportEntry(
        String source,
        int startLine,
        int endLine,
        int busNumber,
        String deviceId,
        String sourceModelName,
        String canonicalModelName,
        int parameterCount,
        DynamicModelImportStatus status,
        String message) {

    public DynamicModelImportEntry {
        source = source == null ? "" : source;
        deviceId = deviceId == null ? "" : deviceId;
        sourceModelName = DynamicModelDescriptor.normalizeName(sourceModelName);
        canonicalModelName = DynamicModelDescriptor.normalizeName(canonicalModelName);
        Objects.requireNonNull(status, "status");
        message = message == null ? "" : message;
        if (startLine <= 0 || endLine < startLine) {
            throw new IllegalArgumentException("Invalid import-entry source location");
        }
    }

    public static DynamicModelImportEntry from(PsseDyrRecord record,
            DynamicModelImportStatus status, String message) {
        return new DynamicModelImportEntry(record.source(), record.startLine(), record.endLine(),
                record.busNumber(), record.deviceId(), record.sourceModelName(),
                record.canonicalModelName(), record.parameterCount(), status, message);
    }
}
