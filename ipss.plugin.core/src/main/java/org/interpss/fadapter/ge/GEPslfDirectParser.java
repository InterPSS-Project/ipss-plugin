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
 * Direct GE PSLF file parser that bypasses the ODM XML layer.
 * Reads GE Positive Sequence Load Flow files and populates AclfNetwork via AclfNetworkBuilder.
 *
 * GE PSLF format uses section-based data with keywords:
 * - title
 * - bus data
 * - generator data
 * - load data
 * - branch data
 * - transformer data
 * - shunt data
 * - area data
 * - zone data
 * Each section ends with "0 /" or a new section keyword.
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
        builder.setNetworkInfo("GE_PSLF_Case", "GE PSLF Case", baseMva * 1000.0, OriginalDataFormat.GE_PSLF);

        String line;
        String section = null;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Detect section headers (case-insensitive)
            String upper = trimmed.toUpperCase();
            if (upper.startsWith("TITLE")) { section = "TITLE"; continue; }
            if (upper.startsWith("BUS DATA") || upper.startsWith("BUS_DATA")) { section = "BUS"; continue; }
            if (upper.startsWith("GENERATOR DATA") || upper.startsWith("GENERATOR_DATA")) { section = "GEN"; continue; }
            if (upper.startsWith("LOAD DATA") || upper.startsWith("LOAD_DATA")) { section = "LOAD"; continue; }
            if (upper.startsWith("BRANCH DATA") || upper.startsWith("BRANCH_DATA") ||
                    upper.startsWith("LINE DATA") || upper.startsWith("LINE_DATA")) { section = "BRANCH"; continue; }
            if (upper.startsWith("TRANSFORMER DATA") || upper.startsWith("TRANSFORMER_DATA")) { section = "XFR"; continue; }
            if (upper.startsWith("SHUNT DATA") || upper.startsWith("SHUNT_DATA")) { section = "SHUNT"; continue; }
            if (upper.startsWith("AREA DATA") || upper.startsWith("AREA_DATA")) { section = "AREA"; continue; }
            if (upper.startsWith("ZONE DATA") || upper.startsWith("ZONE_DATA")) { section = "ZONE"; continue; }
            if (upper.startsWith("END") || upper.startsWith("OWNER DATA")) { section = null; continue; }

            // Skip section-end markers
            if (trimmed.startsWith("0 /") || trimmed.equals("0")) continue;
            // Skip header/comment lines
            if (trimmed.startsWith("/") || trimmed.startsWith("!")) continue;

            if (section == null) continue;

            String[] tokens = tokenize(trimmed);
            if (tokens.length < 2) continue;

            switch (section) {
                case "BUS": parseBusRecord(tokens); break;
                case "GEN": parseGenRecord(tokens); break;
                case "LOAD": parseLoadRecord(tokens); break;
                case "BRANCH": parseBranchRecord(tokens); break;
                case "XFR": parseXfrRecord(tokens); break;
                case "SHUNT": parseShuntRecord(tokens); break;
                case "AREA": parseAreaRecord(tokens); break;
                case "ZONE": parseZoneRecord(tokens); break;
            }
        }

        builder.finalizeNetwork();
        return builder.getNetwork();
    }

    // ==================== Record Parsers ====================

    // GE PSLF bus: number, name, baseKV, type, area, zone, Vm, Va, ...
    private void parseBusRecord(String[] t) throws InterpssException {
        if (t.length < 5) return;

        int busNum = intVal(t, 0);
        String name = strVal(t, 1);
        double baseKv = dblVal(t, 2);
        int type = intVal(t, 3);
        int area = intVal(t, 4);
        int zone = t.length > 5 ? intVal(t, 5) : 0;
        double vm = t.length > 6 ? dblVal(t, 6) : 1.0;
        double va = t.length > 7 ? dblVal(t, 7) : 0.0;

        String busId = BUS_ID_PREFIX + busNum;
        String areaId = area > 0 ? String.valueOf(area) : null;
        String zoneId = zone > 0 ? String.valueOf(zone) : null;

        if (areaId != null) builder.addArea(areaId, "Area " + area, null);
        if (zoneId != null) builder.addZone(zoneId, "Zone " + zone, null);

        BaseAclfBus bus = builder.addBus(busId, name, busNum, baseKv * 1000.0,
                vm, Math.toRadians(va), areaId, zoneId, null);

        if (type == 3 || type == -1) {
            builder.setSwingBus(busId, vm, Math.toRadians(va));
        } else if (type == 2) {
            bus.setGenCode(AclfGenCode.GEN_PV);
        } else {
            bus.setGenCode(AclfGenCode.NON_GEN);
        }
    }

    // GE PSLF gen: busNo, genId, Pg, Qg, Qmax, Qmin, Vs, mbase, status, Pmax, Pmin, ...
    private void parseGenRecord(String[] t) throws InterpssException {
        if (t.length < 6) return;

        int busNum = intVal(t, 0);
        String genId = strVal(t, 1);
        double pg = dblVal(t, 2);
        double qg = dblVal(t, 3);
        double qmax = dblVal(t, 4);
        double qmin = dblVal(t, 5);
        double vs = t.length > 6 ? dblVal(t, 6) : 1.0;
        double mbase = t.length > 7 ? dblVal(t, 7) : baseMva;
        int status = t.length > 8 ? intVal(t, 8) : 1;
        double pmax = t.length > 9 ? dblVal(t, 9) : 0.0;
        double pmin = t.length > 10 ? dblVal(t, 10) : 0.0;

        if (mbase == 0.0) mbase = baseMva;
        String busId = BUS_ID_PREFIX + busNum;

        builder.addContributeGen(busId, genId, status == 1,
                pg / baseMva, qg / baseMva, mbase, vs,
                qmax / baseMva, qmin / baseMva, pmax / baseMva, pmin / baseMva,
                null, null, 1.0, null, 1.0, 1.0);
    }

    // GE PSLF load: busNo, loadId, status, Pl, Ql, Ip, Iq, Yp, Yq, ...
    private void parseLoadRecord(String[] t) throws InterpssException {
        if (t.length < 5) return;

        int busNum = intVal(t, 0);
        String loadId = strVal(t, 1);
        int status = intVal(t, 2);
        double pl = dblVal(t, 3);
        double ql = dblVal(t, 4);
        double ip = t.length > 5 ? dblVal(t, 5) : 0.0;
        double iq = t.length > 6 ? dblVal(t, 6) : 0.0;
        double yp = t.length > 7 ? dblVal(t, 7) : 0.0;
        double yq = t.length > 8 ? dblVal(t, 8) : 0.0;

        String busId = BUS_ID_PREFIX + busNum;
        Complex constP = (pl != 0.0 || ql != 0.0) ? new Complex(pl / baseMva, ql / baseMva) : null;
        Complex constI = (ip != 0.0 || iq != 0.0) ? new Complex(ip / baseMva, iq / baseMva) : null;
        Complex constZ = (yp != 0.0 || yq != 0.0) ? new Complex(yp / baseMva, -yq / baseMva) : null;

        builder.addContributeLoad(busId, loadId, status == 1, constP, constI, constZ, null, false);
    }

    // GE PSLF branch: fromBus, toBus, ckt, r, x, b, rate1, rate2, rate3, status, ...
    private void parseBranchRecord(String[] t) throws InterpssException {
        if (t.length < 7) return;

        int fromNum = intVal(t, 0);
        int toNum = intVal(t, 1);
        String ckt = strVal(t, 2);
        double r = dblVal(t, 3);
        double x = dblVal(t, 4);
        double b = dblVal(t, 5);
        double rate1 = t.length > 6 ? dblVal(t, 6) : 0.0;
        double rate2 = t.length > 7 ? dblVal(t, 7) : 0.0;
        double rate3 = t.length > 8 ? dblVal(t, 8) : 0.0;
        int status = t.length > 9 ? intVal(t, 9) : 1;

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        builder.addLine(fromBusId, toBusId, ckt,
                new Complex(r, x), new Complex(0.0, b * 0.5),
                null, null, rate1, rate2, rate3, status == 1);
    }

    // GE PSLF transformer: fromBus, toBus, ckt, r, x, rate1, fromTap, toTap, angle, status, ...
    private void parseXfrRecord(String[] t) throws InterpssException {
        if (t.length < 8) return;

        int fromNum = intVal(t, 0);
        int toNum = intVal(t, 1);
        String ckt = strVal(t, 2);
        double r = dblVal(t, 3);
        double x = dblVal(t, 4);
        double rate1 = dblVal(t, 5);
        double fromTap = dblVal(t, 6);
        double toTap = t.length > 7 ? dblVal(t, 7) : 1.0;
        double angle = t.length > 8 ? dblVal(t, 8) : 0.0;
        int status = t.length > 9 ? intVal(t, 9) : 1;
        double rate2 = t.length > 10 ? dblVal(t, 10) : 0.0;
        double rate3 = t.length > 11 ? dblVal(t, 11) : 0.0;

        if (fromTap == 0.0) fromTap = 1.0;
        if (toTap == 0.0) toTap = 1.0;

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        if (angle != 0.0) {
            builder.addPsXformer(fromBusId, toBusId, ckt,
                    new Complex(r, x), fromTap, toTap, angle, 0.0,
                    null, null, rate1, rate2, rate3, 0, status == 1);
        } else {
            builder.addXformer2W(fromBusId, toBusId, ckt,
                    new Complex(r, x), fromTap, toTap,
                    null, null, rate1, rate2, rate3, 0, status == 1);
        }
    }

    // GE PSLF shunt: busNo, shuntId, status, g, b, ...
    private void parseShuntRecord(String[] t) throws InterpssException {
        if (t.length < 5) return;

        int busNum = intVal(t, 0);
        int status = intVal(t, 2);
        double g = dblVal(t, 3);
        double b = dblVal(t, 4);

        if (status == 1 && (g != 0.0 || b != 0.0)) {
            String busId = BUS_ID_PREFIX + busNum;
            builder.addToBusShuntY(busId, new Complex(g / baseMva, b / baseMva));
        }
    }

    private void parseAreaRecord(String[] t) throws InterpssException {
        if (t.length < 2) return;
        String areaId = String.valueOf(intVal(t, 0));
        String name = strVal(t, 1);
        builder.addArea(areaId, name, null);
    }

    private void parseZoneRecord(String[] t) throws InterpssException {
        if (t.length < 2) return;
        String zoneId = String.valueOf(intVal(t, 0));
        String name = strVal(t, 1);
        builder.addZone(zoneId, name, null);
    }

    // ==================== Utility Methods ====================

    private String[] tokenize(String line) {
        // GE PSLF uses comma, space, or slash as delimiters; quoted strings for names
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' || c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && (c == ',' || c == '/' || (c == ' ' && current.length() > 0 && !line.substring(i).matches("^\\s+\\S.*")))) {
                if (c == '/') break;
                if (current.length() > 0) {
                    tokens.add(current.toString().trim());
                    current.setLength(0);
                }
                continue;
            }
            if (!inQuote && c == ' ') {
                if (current.length() > 0) {
                    tokens.add(current.toString().trim());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString().trim());

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

    private static String strVal(String[] t, int idx) {
        if (idx >= t.length) return "";
        return t[idx].replace("'", "").replace("\"", "").trim();
    }
}
