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

/**
 * Shared PSS/E REV (revision) field parsing from the IC/SBASE/REV header line.
 * Missing or invalid REV falls back to 30 (same as historical IpssAdapter behavior).
 */
public final class PsseRev {
    private static final int DEFAULT_REV = 30;

    private PsseRev() {}

    /**
     * Parse REV from a raw header data line (comments after {@code /} are stripped
     * by {@link PSSEDataRec}).
     */
    public static int fromHeaderLine(String line) {
        if (line == null || line.isEmpty()) {
            return DEFAULT_REV;
        }
        return fromHeaderRec(new PSSEDataRec(line));
    }

    /**
     * Read REV from a tokenized header record (field index 2).
     */
    public static int fromHeaderRec(PSSEDataRec rec) {
        if (rec == null || rec.size() < 3) {
            return DEFAULT_REV;
        }
        String revStr = rec.getString(2);
        if (revStr == null || revStr.isEmpty()) {
            return DEFAULT_REV;
        }
        // Belt-and-suspenders: strip trailing /comment if still present (e.g. "36/PSS...")
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
