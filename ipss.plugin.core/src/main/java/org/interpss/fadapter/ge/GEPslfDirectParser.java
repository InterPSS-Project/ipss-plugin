/*
 * @(#)GEPslfDirectParser.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.ge;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.OriginalDataFormat;

/**
 * Direct GE PSLF file parser. GE PSLF .epc files use a colon (:) to separate
 * bus-identification fields from numeric data fields.  Branches span 2 lines
 * and transformers span 3 lines per record.
 */
public class GEPslfDirectParser {
    private static final Logger log = LoggerFactory.getLogger(GEPslfDirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus";

    private final AclfNetworkBuilder builder;
    private double baseMva = 100.0;

    public GEPslfDirectParser() {
        this.builder = new AclfNetworkBuilder();
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            parseFromReader(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading GE PSLF file: " + filepath + ": " + e.getMessage());
        }
        return builder.getNetwork();
    }

    public AclfNetwork parseFromReader(BufferedReader reader) throws IOException, InterpssException {
        String line;
        String section = null;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String upper = trimmed.toUpperCase();

            if (upper.startsWith("SOLUTION PARAMETERS")) {
                section = "SOLUTION";
                continue;
            }
            if (section != null && section.equals("SOLUTION")) {
                if (upper.startsWith("SBASE")) {
                    String[] parts = trimmed.split("\\s+");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equalsIgnoreCase("sbase")) {
                            baseMva = Double.parseDouble(parts[i + 1]);
                            break;
                        }
                    }
                }
                if (trimmed.equals("!")) section = null;
                continue;
            }

            if (upper.startsWith("TITLE") || upper.startsWith("COMMENTS")) { section = "SKIP"; continue; }
            if (upper.startsWith("BUS DATA")) { section = "BUS"; continue; }
            if (upper.startsWith("GENERATOR DATA")) { section = "GEN"; continue; }
            if (upper.startsWith("LOAD DATA")) { section = "LOAD"; continue; }
            if (upper.startsWith("BRANCH DATA") || upper.startsWith("LINE DATA")) { section = "BRANCH"; continue; }
            if (upper.startsWith("TRANSFORMER DATA")) { section = "XFR"; continue; }
            if (upper.startsWith("SHUNT DATA")) { section = "SHUNT"; continue; }
            if (upper.startsWith("AREA DATA")) { section = "AREA"; continue; }
            if (upper.startsWith("ZONE DATA")) { section = "ZONE"; continue; }
            if (upper.startsWith("SVD DATA") || upper.startsWith("INTERFACE DATA") ||
                upper.startsWith("DC BUS") || upper.startsWith("DC LINE") ||
                upper.startsWith("DC CONVERTER") || upper.startsWith("Z TABLE") ||
                upper.startsWith("GCD DATA") || upper.startsWith("TRANSACTION") ||
                upper.startsWith("OWNER DATA") || upper.startsWith("MOTOR DATA")) {
                section = "SKIP";
                continue;
            }
            if (upper.startsWith("END")) { section = null; break; }

            if (section == null || section.equals("SKIP")) continue;
            if (trimmed.startsWith("!") || trimmed.startsWith("#")) continue;

            switch (section) {
                case "BUS":    parseBusLine(trimmed); break;
                case "GEN":    parseGenLine(trimmed, reader); break;
                case "LOAD":   parseLoadLine(trimmed); break;
                case "BRANCH": parseBranchLine(trimmed, reader); break;
                case "XFR":    parseXfrLine(trimmed, reader); break;
                case "SHUNT":  parseShuntLine(trimmed); break;
                case "AREA":   parseAreaLine(trimmed); break;
                case "ZONE":   parseZoneLine(trimmed); break;
            }
        }

        builder.setNetworkInfo("GE_PSLF_Case", "GE PSLF Case", baseMva * 1000.0, OriginalDataFormat.GE_PSLF);
        builder.finalizeNetwork();
        return builder.getNetwork();
    }

    // ==================== Bus Record ====================
    // Format: busNum "name" baseKV : type vsched volt angle area zone vmax vmin ...
    private void parseBusLine(String line) throws InterpssException {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return;

        String[] before = tokenizeQuoted(line.substring(0, colonIdx));
        String[] after = line.substring(colonIdx + 1).trim().split("\\s+");
        if (before.length < 3 || after.length < 4) return;

        int busNum = intVal(before, 0);
        String name = before[1];
        double baseKv = dblVal(before, 2);

        int type = intVal(after, 0);
        double vsched = dblVal(after, 1);
        double volt = dblVal(after, 2);
        double angle = dblVal(after, 3);
        int area = after.length > 4 ? intVal(after, 4) : 0;
        int zone = after.length > 5 ? intVal(after, 5) : 0;

        String busId = BUS_ID_PREFIX + busNum;
        String areaId = area > 0 ? String.valueOf(area) : null;
        String zoneId = zone > 0 ? String.valueOf(zone) : null;

        if (areaId != null) builder.addArea(areaId, "Area " + area, null);
        if (zoneId != null) builder.addZone(zoneId, "Zone " + zone, null);

        double vm = volt > 0 ? volt : (vsched > 0 ? vsched : 1.0);
        BaseAclfBus bus = builder.addBus(busId, name, busNum, baseKv * 1000.0,
                vm, Math.toRadians(angle), areaId, zoneId, null);

        if (type == 0 || type == -1) {
            builder.setSwingBus(busId, vm, Math.toRadians(angle));
        } else if (type == 2) {
            bus.setGenCode(AclfGenCode.GEN_PV);
        } else {
            bus.setGenCode(AclfGenCode.NON_GEN);
        }
    }

    // ==================== Generator Record ====================
    // Format: busNum "name" baseKV "genId" "longId" : status regBusNum "regName" regKV prf qrf area zone pgen pmax pmin qgen qmax qmin mbase ...
    // Spans 2 lines; second line is continuation data (skip it)
    private void parseGenLine(String line, BufferedReader reader) throws IOException, InterpssException {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return;

        String[] before = tokenizeQuoted(line.substring(0, colonIdx));
        String afterStr = line.substring(colonIdx + 1).trim();
        if (afterStr.endsWith("/")) afterStr = afterStr.substring(0, afterStr.length() - 1).trim();
        String[] after = tokenizeQuoted(afterStr);

        if (before.length < 4) return;
        int busNum = intVal(before, 0);
        String genId = before.length > 3 ? before[3] : "1";

        // after: status regBusNum regName regKV prf qrf area zone pgen pmax pmin qgen qmax qmin mbase ...
        int status = intVal(after, 0);
        // skip regBus info (positions 1-3)
        // positions 4-5: prf qrf
        // positions 6-7: area zone
        double pgen = dblVal(after, 8);
        double pmax = dblVal(after, 9);
        double pmin = dblVal(after, 10);
        double qgen = dblVal(after, 11);
        double qmax = dblVal(after, 12);
        double qmin = dblVal(after, 13);
        double mbase = dblVal(after, 14);

        if (mbase == 0.0) mbase = baseMva;
        String busId = BUS_ID_PREFIX + busNum;

        // Get scheduled voltage from the bus record
        BaseAclfBus bus = builder.getBus(busId);
        double vs = 1.0;
        if (bus != null && bus.getVoltageMag() > 0) vs = bus.getVoltageMag();

        builder.addContributeGen(busId, genId, status == 1,
                pgen / baseMva, qgen / baseMva, mbase, vs,
                qmax / baseMva, qmin / baseMva, pmax / baseMva, pmin / baseMva,
                null, null, 1.0, null, 1.0, 1.0);

        // Set PV bus parameters if bus is a PV generator bus
        if (bus != null && bus.getGenCode() == AclfGenCode.GEN_PV) {
            builder.setPVBus(busId, pgen / baseMva, vs,
                    qmax / baseMva, qmin / baseMva, true);
        }

        // skip continuation line
        reader.readLine();
    }

    // ==================== Load Record ====================
    // Format: busNum "name" baseKV "loadId" "longId" : status mw mvar mw_i mvar_i mw_z mvar_z area zone ...
    private void parseLoadLine(String line) throws InterpssException {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return;

        String[] before = tokenizeQuoted(line.substring(0, colonIdx));
        String[] after = line.substring(colonIdx + 1).trim().split("\\s+");

        if (before.length < 1) return;
        int busNum = intVal(before, 0);
        String loadId = before.length > 3 ? before[3] : "1";

        int status = intVal(after, 0);
        double pl = dblVal(after, 1);
        double ql = dblVal(after, 2);
        double ip = dblVal(after, 3);
        double iq = dblVal(after, 4);
        double yp = dblVal(after, 5);
        double yq = dblVal(after, 6);

        String busId = BUS_ID_PREFIX + busNum;
        Complex constP = (pl != 0.0 || ql != 0.0) ? new Complex(pl / baseMva, ql / baseMva) : null;
        Complex constI = (ip != 0.0 || iq != 0.0) ? new Complex(ip / baseMva, iq / baseMva) : null;
        Complex constZ = (yp != 0.0 || yq != 0.0) ? new Complex(yp / baseMva, yq / baseMva) : null;

        builder.addContributeLoad(busId, loadId, status == 1, constP, constI, constZ, null, false);
    }

    // ==================== Branch Record (2 lines) ====================
    // Line 1: fromBus "name" kV toBus "name" kV "ckt" section "longId" : status R X B rate1 rate2 rate3 rate4 aloss length /
    // Line 2: continuation (skip)
    private void parseBranchLine(String line, BufferedReader reader) throws IOException, InterpssException {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) { reader.readLine(); return; }

        String[] before = tokenizeQuoted(line.substring(0, colonIdx));
        String afterStr = line.substring(colonIdx + 1).trim();
        if (afterStr.endsWith("/")) afterStr = afterStr.substring(0, afterStr.length() - 1).trim();
        String[] after = afterStr.split("\\s+");

        // skip continuation line
        reader.readLine();

        if (before.length < 7 || after.length < 4) return;

        int fromNum = intVal(before, 0);
        int toNum = intVal(before, 3);
        String ckt = before.length > 6 ? before[6] : "1";

        int status = intVal(after, 0);
        double r = dblVal(after, 1);
        double x = dblVal(after, 2);
        double b = dblVal(after, 3);
        double rate1 = after.length > 4 ? dblVal(after, 4) : 0.0;
        double rate2 = after.length > 5 ? dblVal(after, 5) : 0.0;
        double rate3 = after.length > 6 ? dblVal(after, 6) : 0.0;

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        builder.addLine(fromBusId, toBusId, ckt,
                new Complex(r, x), new Complex(0.0, b * 0.5),
                null, null, rate1, rate2, rate3, status == 1);
    }

    // ==================== Transformer Record (3 lines) ====================
    // Line 1: fromBus "name" kV toBus "name" kV "ckt" "longId" : status type [reg bus info...] area zone tbase R X ...  /
    // Line 2: tap1KV tap2KV angle ... rate1 ... tapLimits... tapRatio1 tapRatio2 tapRatio3 tapRatio4 ... /
    // Line 3: Z correction (skip)
    private void parseXfrLine(String line, BufferedReader reader) throws IOException, InterpssException {
        int colonIdx = line.indexOf(':');
        String line2 = reader.readLine();
        String line3 = reader.readLine();

        if (colonIdx < 0) return;

        String[] before = tokenizeQuoted(line.substring(0, colonIdx));
        String afterStr = line.substring(colonIdx + 1).trim();
        if (afterStr.endsWith("/")) afterStr = afterStr.substring(0, afterStr.length() - 1).trim();
        String[] afterTokens = tokenizeQuoted(afterStr);

        if (before.length < 6) return;
        int fromNum = intVal(before, 0);
        double fromKV = dblVal(before, 2);
        int toNum = intVal(before, 3);
        double toKV = dblVal(before, 5);
        String ckt = before.length > 6 ? before[6] : "1";

        // after: status type [regBus info with quoted names] area zone tbase R X ...
        // The R and X are near the end. Count backwards from the end (last 7 are: tbase R X pt_r pt_x ts_r ts_x)
        int status = intVal(afterTokens, 0);
        double r = 0.0, x = 0.0001, tbase = baseMva;
        int afterLen = afterTokens.length;
        if (afterLen >= 7) {
            tbase = dblVal(afterTokens, afterLen - 7);
            r = dblVal(afterTokens, afterLen - 6);
            x = dblVal(afterTokens, afterLen - 5);
        }
        // Convert from transformer base to system base
        if (tbase > 0 && tbase != baseMva) {
            double baseRatio = baseMva / tbase;
            r *= baseRatio;
            x *= baseRatio;
        }

        // Parse line 2 for tap data
        double fromTap = 1.0, toTap = 1.0, angle = 0.0, rate1 = 0.0;
        if (line2 != null) {
            String line2Str = line2.trim();
            if (line2Str.endsWith("/")) line2Str = line2Str.substring(0, line2Str.length() - 1).trim();
            String[] t2 = line2Str.split("\\s+");
            if (t2.length >= 2) {
                double tap1KV = dblVal(t2, 0);
                double tap2KV = dblVal(t2, 1);
                angle = t2.length > 2 ? dblVal(t2, 2) : 0.0;
                rate1 = t2.length > 6 ? dblVal(t2, 6) : 0.0;

                // Convert kV taps to PU
                fromTap = (tap1KV > 0 && fromKV > 0) ? tap1KV / fromKV : 1.0;
                toTap = (tap2KV > 0 && toKV > 0) ? tap2KV / toKV : 1.0;

                // If tap ratios are explicitly provided (positions 16-17 in line2), use them
                if (t2.length > 17) {
                    double tr1 = dblVal(t2, 16);
                    double tr2 = dblVal(t2, 17);
                    if (tr1 > 0) fromTap = tr1;
                    if (tr2 > 0) toTap = tr2;
                }
            }
        }

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        if (angle != 0.0) {
            builder.addPsXformer(fromBusId, toBusId, ckt,
                    new Complex(r, x), fromTap, toTap, angle, 0.0,
                    null, null, rate1, 0, 0, 0, status == 1);
        } else {
            builder.addXformer2W(fromBusId, toBusId, ckt,
                    new Complex(r, x), fromTap, toTap,
                    null, null, rate1, 0, 0, 0, status == 1);
        }
    }

    // ==================== Shunt Record ====================
    private void parseShuntLine(String line) throws InterpssException {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return;

        String[] before = tokenizeQuoted(line.substring(0, colonIdx));
        String[] after = line.substring(colonIdx + 1).trim().split("\\s+");

        if (before.length < 1) return;
        int busNum = intVal(before, 0);
        int status = intVal(after, 0);
        // GE shunt data fields after status: ar zone pu_mw pu_mvar
        double g = after.length > 3 ? dblVal(after, 3) : 0.0;
        double b = after.length > 4 ? dblVal(after, 4) : 0.0;

        if (status == 1 && (g != 0.0 || b != 0.0)) {
            String busId = BUS_ID_PREFIX + busNum;
            builder.addToBusShuntY(busId, new Complex(g / baseMva, b / baseMva));
        }
    }

    private void parseAreaLine(String line) throws InterpssException {
        int colonIdx = line.indexOf(':');
        if (colonIdx >= 0) return; // skip if it has a colon (not area data format)
        String[] tokens = tokenizeQuoted(line.trim());
        if (tokens.length < 2) return;
        String areaId = tokens[0];
        String name = tokens[1];
        builder.addArea(areaId, name, null);
    }

    private void parseZoneLine(String line) throws InterpssException {
        String[] tokens = tokenizeQuoted(line.trim());
        if (tokens.length < 2) return;
        String zoneId = tokens[0];
        String name = tokens[1];
        builder.addZone(zoneId, name, null);
    }

    // ==================== Tokenizer ====================
    // Splits on whitespace, preserving quoted strings as single tokens (quotes removed).
    // Stops at '/' character.
    private static String[] tokenizeQuoted(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!inQuote && (c == '\'' || c == '"')) {
                inQuote = true;
                quoteChar = c;
                continue;
            }
            if (inQuote && c == quoteChar) {
                inQuote = false;
                tokens.add(sb.toString().trim());
                sb.setLength(0);
                continue;
            }
            if (inQuote) {
                sb.append(c);
                continue;
            }
            if (c == '/') break;
            if (c == ':') {
                if (sb.length() > 0) { tokens.add(sb.toString().trim()); sb.setLength(0); }
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (sb.length() > 0) { tokens.add(sb.toString().trim()); sb.setLength(0); }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    private static int intVal(String[] t, int idx) {
        if (idx >= t.length) return 0;
        try { return Integer.parseInt(t[idx].trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static double dblVal(String[] t, int idx) {
        if (idx >= t.length) return 0.0;
        try { return Double.parseDouble(t[idx].trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
