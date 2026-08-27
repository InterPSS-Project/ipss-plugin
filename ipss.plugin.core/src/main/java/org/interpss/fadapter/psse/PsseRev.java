/*
 * @(#)PsseRev.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.psse;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared PSS/E REV (revision) field parsing from the IC/SBASE/REV header line.
 * Missing or invalid REV falls back to 30 (same as historical IpssAdapter behavior).
 * <p>
 * Handles both comma-separated headers and mixed whitespace forms used by some
 * fixtures, e.g. {@code 0     100.00  33 , 0, 0, 60.00}.
 */
public final class PsseRev {
    private static final int DEFAULT_REV = 30;

    private PsseRev() {}

    /**
     * Parse REV from a raw header data line.
     */
    public static int fromHeaderLine(String line) {
        if (line == null || line.isEmpty()) {
            return DEFAULT_REV;
        }
        String cleaned = stripSlashComment(line).trim();
        if (cleaned.isEmpty()) {
            return DEFAULT_REV;
        }
        List<String> fields = expandHeaderFields(cleaned);
        if (fields.size() < 3) {
            return DEFAULT_REV;
        }
        return parseRevToken(fields.get(2));
    }

    /**
     * Read REV from a tokenized header record (field index 2).
     * Prefer {@link #fromHeaderLine(String)} when the raw line may mix spaces and commas.
     */
    public static int fromHeaderRec(PSSEDataRec rec) {
        if (rec == null || rec.size() < 3) {
            return DEFAULT_REV;
        }
        return parseRevToken(rec.getString(2));
    }

    private static int parseRevToken(String revStr) {
        if (revStr == null || revStr.isEmpty()) {
            return DEFAULT_REV;
        }
        int slash = revStr.indexOf('/');
        if (slash >= 0) {
            revStr = revStr.substring(0, slash).trim();
        }
        if (revStr.isEmpty()) {
            return DEFAULT_REV;
        }
        try {
            return clamp((int) Double.parseDouble(revStr.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_REV;
        }
    }

    /**
     * Expand CSV tokens that themselves contain whitespace-separated numerics
     * (IC SBASE REV packed into the first comma field).
     */
    private static List<String> expandHeaderFields(String cleaned) {
        List<String> fields = new ArrayList<>();
        for (String part : cleaned.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                fields.add("");
                continue;
            }
            String[] ws = trimmed.split("\\s+");
            if (ws.length > 1 && allNumeric(ws)) {
                for (String w : ws) {
                    fields.add(w);
                }
            } else {
                fields.add(trimmed);
            }
        }
        return fields;
    }

    private static boolean allNumeric(String[] tokens) {
        for (String t : tokens) {
            try {
                Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static String stripSlashComment(String str) {
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\'' || c == '"') {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                    quoteChar = 0;
                }
            }
            if (c == '/' && !inQuotes) {
                return str.substring(0, i);
            }
        }
        return str;
    }

    /**
     * Clamp a raw REV integer to the supported layout range (29–36).
     * Values above 36 use layout 36; below 29 use 29.
     */
    public static int clamp(int rev) {
        if (rev >= 36) return 36;
        if (rev <= 29) return 29;
        return rev;
    }

    public static int defaultRev() {
        return DEFAULT_REV;
    }
}
