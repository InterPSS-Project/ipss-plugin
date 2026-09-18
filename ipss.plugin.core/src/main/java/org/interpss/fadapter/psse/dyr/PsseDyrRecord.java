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

    /** Return a required parameter by its zero-based position in the model data list. */
    public String parameter(int index) {
        if (index < 0 || index >= parameterCount()) {
            throw parameterError(index, "is missing", null);
        }
        return fields.get(parameterOffset + index);
    }

    /** Return a required floating-point parameter with source-located diagnostics. */
    public double doubleParameter(int index) {
        String value = parameter(index);
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw parameterError(index, "must be a floating-point number but was '" + value + "'", e);
        }
    }

    /** Return an optional floating-point parameter, defaulting only when it is absent. */
    public double optionalDoubleParameter(int index, double defaultValue) {
        return index >= parameterCount() ? defaultValue : doubleParameter(index);
    }

    /** Return a required integer flag with source-located diagnostics. */
    public int intParameter(int index) {
        String value = parameter(index);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw parameterError(index, "must be an integer but was '" + value + "'", e);
        }
    }

    /** Return an optional integer flag, defaulting only when it is absent. */
    public int optionalIntParameter(int index, int defaultValue) {
        return index >= parameterCount() ? defaultValue : intParameter(index);
    }

    private IllegalArgumentException parameterError(int index, String detail, Exception cause) {
        String message = "DYR " + canonicalModelName + " parameter " + (index + 1) + " "
                + detail + " at " + source + ":" + startLine;
        return cause == null ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }
}
