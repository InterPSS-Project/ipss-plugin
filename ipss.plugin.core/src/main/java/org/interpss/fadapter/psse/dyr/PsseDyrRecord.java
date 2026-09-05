package org.interpss.fadapter.psse.dyr;

import java.util.List;
import java.util.Objects;

/** One tokenized PSS/E DYR record with source location retained for diagnostics. */
public record PsseDyrRecord(
        String source,
        int startLine,
        int endLine,
        String rawText,
        int busNumber,
        String sourceModelName,
        String canonicalModelName,
        String deviceId,
        List<String> fields,
        int parameterOffset) {

    public PsseDyrRecord {
        source = source == null ? "" : source;
        rawText = Objects.requireNonNull(rawText, "rawText");
        sourceModelName = DynamicModelDescriptor.normalizeName(sourceModelName);
        canonicalModelName = DynamicModelDescriptor.normalizeName(canonicalModelName);
        deviceId = Objects.requireNonNull(deviceId, "deviceId").trim();
        fields = List.copyOf(fields);
        if (startLine <= 0 || endLine < startLine) {
            throw new IllegalArgumentException("Invalid DYR source line range");
        }
        if (parameterOffset < 0 || parameterOffset > fields.size()) {
            throw new IllegalArgumentException("Invalid DYR parameter offset");
        }
    }

    public int parameterCount() {
        return fields.size() - parameterOffset;
    }

    public List<String> parameters() {
        return fields.subList(parameterOffset, fields.size());
    }
}
