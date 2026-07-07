package org.interpss.plugin.aux_fmt.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared helpers for PowerWorld concise AUX parsing.
 */
public final class AuxParseUtil {

    private AuxParseUtil() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    public static String stripComment(String line) {
        int index = line.indexOf("//");
        return index >= 0 ? line.substring(0, index) : line;
    }

    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuote = false;
        boolean tokenStarted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    token.append('"');
                    tokenStarted = true;
                    i++;
                } else {
                    inQuote = !inQuote;
                    tokenStarted = true;
                }
            } else if (!inQuote && (ch == ',' || Character.isWhitespace(ch))) {
                if (tokenStarted || ch == ',') {
                    tokens.add(token.toString().trim());
                    token.setLength(0);
                    tokenStarted = false;
                }
            } else {
                token.append(ch);
                tokenStarted = true;
            }
        }
        if (tokenStarted) {
            tokens.add(token.toString().trim());
        }
        return tokens;
    }

    public static Map<String, String> mapFields(List<String> fields, List<String> values) {
        Map<String, String> mapped = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String value = i < values.size() ? values.get(i) : "";
            mapped.put(fields.get(i), value);
        }
        return mapped;
    }

    public static String firstPresent(Map<String, String> fields, String... names) {
        for (String name : names) {
            String value = fields.get(normalize(name));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static String trimTrailingComma(String line) {
        String trimmed = line.trim();
        if (trimmed.endsWith(",")) {
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
