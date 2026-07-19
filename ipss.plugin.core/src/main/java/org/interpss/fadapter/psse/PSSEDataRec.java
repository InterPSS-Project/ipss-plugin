/*
 * @(#)PSSEDataRec.java
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
 * Tokenizer for PSS/E RAW comma-separated data lines.
 * Handles quoted strings (single or double quotes), comments after "/",
 * and missing trailing fields.
 */
public class PSSEDataRec {
    private final List<String> fields = new ArrayList<>();
    private String nameTagDescription;
    private String externalUid;

    public PSSEDataRec() {}

    public PSSEDataRec(String lineStr) {
        parse(lineStr);
    }

    public void parse(String lineStr) {
        fields.clear();
        nameTagDescription = null;
        externalUid = null;
        parseNameTagMetadata(lineStr);
        String str = removeComments(lineStr);

        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i <= str.length(); i++) {
            char c = (i == str.length()) ? ',' : str.charAt(i);

            if (i < str.length() && (c == '\'' || c == '"')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                    continue;
                } else if (c == quoteChar) {
                    inQuotes = false;
                    quoteChar = 0;
                    continue;
                }
            }

            if ((c == ',' && !inQuotes) || i == str.length()) {
                fields.add(token.toString().trim());
                token.setLength(0);
            } else {
                token.append(c);
            }
        }
    }

    private void parseNameTagMetadata(String str) {
        int commentStart = str.indexOf("/*");
        int commentEnd = commentStart >= 0 ? str.indexOf("*/", commentStart + 2) : -1;
        if (commentEnd < 0) return;

        int descriptionStart = str.indexOf('[', commentStart + 2);
        int descriptionEnd = descriptionStart >= 0 ? str.indexOf(']', descriptionStart + 1) : -1;
        if (descriptionStart < 0 || descriptionEnd < 0 || descriptionEnd > commentEnd) return;

        nameTagDescription = str.substring(descriptionStart, descriptionEnd + 1);
        String entries = str.substring(descriptionStart + 1, descriptionEnd);
        int comma = entries.indexOf(',');
        externalUid = (comma >= 0 ? entries.substring(0, comma) : entries).trim();
        if (externalUid.isEmpty()) externalUid = null;
    }

    private String removeComments(String str) {
        if (!str.contains("/")) return str;

        StringBuilder result = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\'' || c == '"') {
                if (!inQuotes) { inQuotes = true; quoteChar = c; }
                else if (c == quoteChar) { inQuotes = false; quoteChar = 0; }
            }
            if (c == '/' && !inQuotes) {
                boolean preceded = i == 0 || Character.isWhitespace(str.charAt(i - 1));
                boolean followed = i + 1 >= str.length() || Character.isWhitespace(str.charAt(i + 1));
                if (preceded || followed) break;
            }
            result.append(c);
        }
        return result.toString();
    }

    public int size() { return fields.size(); }

    public boolean hasNameTagMetadata() { return nameTagDescription != null; }

    public String getNameTagDescription() { return nameTagDescription; }

    public String getExternalUid() { return externalUid; }

    public String getString(int idx) {
        return idx < fields.size() ? fields.get(idx) : "";
    }

    public String getString(int idx, String defaultVal) {
        if (idx >= fields.size()) return defaultVal;
        String s = fields.get(idx);
        return s.isEmpty() ? defaultVal : s;
    }

    public int getInt(int idx) {
        return getInt(idx, 0);
    }

    public int getInt(int idx, int defaultVal) {
        if (idx >= fields.size()) return defaultVal;
        String s = fields.get(idx);
        if (s.isEmpty()) return defaultVal;
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    public double getDouble(int idx) {
        return getDouble(idx, 0.0);
    }

    public double getDouble(int idx, double defaultVal) {
        if (idx >= fields.size()) return defaultVal;
        String s = fields.get(idx);
        if (s.isEmpty()) return defaultVal;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    /**
     * Check if the third field (K in transformer records) is non-zero, indicating a 3W transformer.
     */
    public boolean is3WXfr() {
        return getInt(2) != 0;
    }

    /**
     * Check if this is an end-of-section record.
     * PSS/E uses lines starting with "0" or "Q" to mark section boundaries.
     */
    public static boolean isEndRec(String lineStr) {
        if (lineStr == null) return true;
        String s = lineStr.trim();
        if (s.isEmpty()) return false;
        return s.charAt(0) == '0' || s.charAt(0) == 'Q' || s.charAt(0) == 'q';
    }
}
