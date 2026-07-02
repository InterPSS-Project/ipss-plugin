package org.interpss.plugin.contingency.aux_fmt.bean;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuxCtgElement {
    private final Map<String, String> fields;
    private final int lineNumber;

    public AuxCtgElement(Map<String, String> fields, int lineNumber) {
        this.fields = new LinkedHashMap<>(fields);
        this.lineNumber = lineNumber;
    }

    public Map<String, String> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    public String get(String fieldName) {
        return fields.get(normalize(fieldName));
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public static String normalize(String fieldName) {
        return fieldName == null ? "" : fieldName.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
