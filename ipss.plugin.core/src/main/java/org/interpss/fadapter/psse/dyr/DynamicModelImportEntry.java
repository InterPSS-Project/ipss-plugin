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
        boolean aliasConverted,
        String runtimeClassName,
        String message) {

    public DynamicModelImportEntry {
        source = source == null ? "" : source;
        deviceId = deviceId == null ? "" : deviceId;
        sourceModelName = DynamicModelDescriptor.normalizeName(sourceModelName);
        canonicalModelName = DynamicModelDescriptor.normalizeName(canonicalModelName);
        Objects.requireNonNull(status, "status");
        runtimeClassName = runtimeClassName == null ? "" : runtimeClassName.trim();
        message = message == null ? "" : message;
        if (startLine <= 0 || endLine < startLine) {
            throw new IllegalArgumentException("Invalid import-entry source location");
        }
    }

    public static DynamicModelImportEntry from(PsseDyrRecord record,
            DynamicModelImportStatus status, String message) {
        String runtimeClass = status == DynamicModelImportStatus.ATTACHED
                ? DynamicModelCatalog.find(record.canonicalModelName())
                        .map(DynamicModelDescriptor::runtimeClassName).orElse("")
                : "";
        return new DynamicModelImportEntry(record.source(), record.startLine(), record.endLine(),
                record.busNumber(), record.deviceId(), record.sourceModelName(),
                record.canonicalModelName(), record.parameterCount(), status,
                !record.sourceModelName().equals(record.canonicalModelName()), runtimeClass, message);
    }
}
