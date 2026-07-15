/*
 * @(#)PWDDirectParser.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.pwd;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.OriginalDataFormat;

/**
 * Direct PowerWorld (PWD/AUX) file parser that bypasses the ODM XML layer.
 * Reads PowerWorld auxiliary files and populates AclfNetwork via AclfNetworkBuilder.
 *
 * PWD auxiliary file format uses DATA sections with field definitions:
 *   DATA (BUS, [BusNum, BusName, NomkV, BusType, ...])
 *   {
 *     1 "Bus1" 345.0 "PQ" ...
 *     ...
 *   }
 */
public class PWDDirectParser {
    private static final Logger log = LoggerFactory.getLogger(PWDDirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus";

    private final AclfNetworkBuilder builder;
    private double baseMva = 100.0;

    public PWDDirectParser() {
        this.builder = new AclfNetworkBuilder();
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            parseFromReader(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading PowerWorld file: " + filepath + ": " + e.getMessage());
        }
        return builder.getNetwork();
    }

    public AclfNetwork parseFromReader(BufferedReader reader) throws IOException, InterpssException {
        builder.setNetworkInfo("PWD_Case", "PowerWorld Case", baseMva * 1000.0, OriginalDataFormat.PWD);

        String line;
        StringBuilder buffer = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            buffer.append(line.trim()).append(" ");
        }

        String content = buffer.toString();
        parseDataSections(content);

        builder.finalizeNetwork();
        return builder.getNetwork();
    }

    private void parseDataSections(String content) throws InterpssException {
        int pos = 0;
        while (pos < content.length()) {
            int dataIdx = content.indexOf("DATA", pos);
            if (dataIdx < 0) break;

            int parenStart = content.indexOf('(', dataIdx);
            if (parenStart < 0) break;

            // Find the matching closing paren for the field definition
            int parenEnd = findMatchingParen(content, parenStart);
            if (parenEnd < 0) break;

            String header = content.substring(parenStart + 1, parenEnd).trim();

            // Find the data block { ... }
            int braceStart = content.indexOf('{', parenEnd);
            if (braceStart < 0) { pos = parenEnd + 1; continue; }

            int braceEnd = content.indexOf('}', braceStart);
            if (braceEnd < 0) { pos = braceStart + 1; continue; }

            String dataBlock = content.substring(braceStart + 1, braceEnd).trim();

            parseDataBlock(header, dataBlock);
            pos = braceEnd + 1;
        }
    }

    private void parseDataBlock(String header, String dataBlock) throws InterpssException {
        // Parse header: "TYPE, [field1, field2, ...]"
        int bracketStart = header.indexOf('[');
        int bracketEnd = header.indexOf(']');
        if (bracketStart < 0 || bracketEnd < 0) return;

        String dataType = header.substring(0, bracketStart).trim().toUpperCase();
        if (dataType.endsWith(",")) dataType = dataType.substring(0, dataType.length() - 1).trim();

        String fieldStr = header.substring(bracketStart + 1, bracketEnd);
        String[] fields = fieldStr.split(",");
        Map<String, Integer> fieldMap = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i++) {
            fieldMap.put(fields[i].trim().toLowerCase(), i);
        }

        // Parse data rows
        List<String[]> rows = parseDataRows(dataBlock, fields.length);

        switch (dataType) {
            case "BUS": processBusData(fieldMap, rows); break;
            case "GEN": case "GENERATOR": processGenData(fieldMap, rows); break;
            case "LOAD": processLoadData(fieldMap, rows); break;
            case "BRANCH": processBranchData(fieldMap, rows); break;
            case "TRANSFORMER": processTransformerData(fieldMap, rows); break;
            case "SHUNT": processShuntData(fieldMap, rows); break;
            case "AREA": processAreaData(fieldMap, rows); break;
            case "ZONE": processZoneData(fieldMap, rows); break;
        }
    }

    private List<String[]> parseDataRows(String dataBlock, int expectedFields) {
        List<String[]> rows = new ArrayList<>();
        // Split by newline markers or semicolons
        String[] lines = dataBlock.split("\\s*;\\s*|\\s*\\n\\s*");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] values = tokenizePWDLine(line);
            if (values.length > 0) rows.add(values);
        }
        return rows;
    }

    private String[] tokenizePWDLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && (c == ' ' || c == '\t' || c == ',')) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());

        return tokens.toArray(new String[0]);
    }

    // ==================== Data Processing ====================

    private void processBusData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int busNum = getInt(row, fields, "busnum", 0);
            if (busNum == 0) continue;

            String name = getString(row, fields, "busname", "");
            double baseKv = getDouble(row, fields, "nomkv", 0.0);
            String busType = getString(row, fields, "bustype", "PQ");
            double vm = getDouble(row, fields, "buspuvolt", 1.0);
            double va = getDouble(row, fields, "busangle", 0.0);
            int area = getInt(row, fields, "areaname", 0);
            if (area == 0) area = getInt(row, fields, "areanum", 0);
            int zone = getInt(row, fields, "zonename", 0);
            if (zone == 0) zone = getInt(row, fields, "zonenum", 0);

            String busId = BUS_ID_PREFIX + busNum;
            String areaId = area > 0 ? String.valueOf(area) : null;
            String zoneId = zone > 0 ? String.valueOf(zone) : null;

            if (areaId != null) builder.addArea(areaId, "Area " + area, null);
            if (zoneId != null) builder.addZone(zoneId, "Zone " + zone, null);

            AclfBus bus = builder.addBus(busId, name, busNum, baseKv * 1000.0,
                    vm, Math.toRadians(va), areaId, zoneId, null);

            if ("Slack".equalsIgnoreCase(busType) || "Swing".equalsIgnoreCase(busType)) {
                builder.setSwingBus(busId, vm, Math.toRadians(va));
            } else if ("PV".equalsIgnoreCase(busType)) {
                bus.setGenCode(AclfGenCode.GEN_PV);
            } else {
                bus.setGenCode(AclfGenCode.NON_GEN);
            }
        }
    }

    private void processGenData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int busNum = getInt(row, fields, "busnum", 0);
            if (busNum == 0) continue;

            String genId = getString(row, fields, "genid", "1");
            double pg = getDouble(row, fields, "genmw", 0.0);
            double qg = getDouble(row, fields, "genmvar", 0.0);
            double qmax = getDouble(row, fields, "genmvarmax", 0.0);
            double qmin = getDouble(row, fields, "genmvarmin", 0.0);
            double vs = getDouble(row, fields, "genregpuvolt", 1.0);
            double mbase = getDouble(row, fields, "genmvabase", baseMva);
            String status = getString(row, fields, "genstatus", "Open");
            double pmax = getDouble(row, fields, "genmwmax", 0.0);
            double pmin = getDouble(row, fields, "genmwmin", 0.0);

            if (mbase == 0.0) mbase = baseMva;
            String busId = BUS_ID_PREFIX + busNum;
            boolean genStatus = "Closed".equalsIgnoreCase(status);

            builder.addContributeGen(busId, genId, genStatus,
                    pg / baseMva, qg / baseMva, mbase, vs,
                    qmax / baseMva, qmin / baseMva, pmax / baseMva, pmin / baseMva,
                    null, null, 1.0, null, 1.0, 1.0);
        }
    }

    private void processLoadData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int busNum = getInt(row, fields, "busnum", 0);
            if (busNum == 0) continue;

            String loadId = getString(row, fields, "loadid", "1");
            double pl = getDouble(row, fields, "loadmw", 0.0);
            double ql = getDouble(row, fields, "loadmvar", 0.0);
            String status = getString(row, fields, "loadstatus", "Closed");

            String busId = BUS_ID_PREFIX + busNum;
            Complex constP = (pl != 0.0 || ql != 0.0) ? new Complex(pl / baseMva, ql / baseMva) : null;

            builder.addContributeLoad(busId, loadId, "Closed".equalsIgnoreCase(status),
                    constP, null, null, null, false);
        }
    }

    private void processBranchData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int fromNum = getInt(row, fields, "busnumfrom", 0);
            if (fromNum == 0) fromNum = getInt(row, fields, "busnum", 0);
            int toNum = getInt(row, fields, "busnumto", 0);
            if (fromNum == 0 || toNum == 0) continue;

            String ckt = getString(row, fields, "linecircuit", "1");
            double r = getDouble(row, fields, "liner", 0.0);
            double x = getDouble(row, fields, "linex", 0.0);
            double b = getDouble(row, fields, "lineb", 0.0);
            double rate1 = getDouble(row, fields, "linemva", 0.0);
            if (rate1 == 0.0) rate1 = getDouble(row, fields, "limitmva", 0.0);
            String status = getString(row, fields, "linestatus", "Closed");

            String fromBusId = BUS_ID_PREFIX + fromNum;
            String toBusId = BUS_ID_PREFIX + toNum;

            builder.addLine(fromBusId, toBusId, ckt,
                    new Complex(r, x), new Complex(0.0, b * 0.5),
                    null, null, rate1, 0.0, 0.0, "Closed".equalsIgnoreCase(status));
        }
    }

    private void processTransformerData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int fromNum = getInt(row, fields, "busnumfrom", 0);
            if (fromNum == 0) fromNum = getInt(row, fields, "busnum", 0);
            int toNum = getInt(row, fields, "busnumto", 0);
            if (fromNum == 0 || toNum == 0) continue;

            String ckt = getString(row, fields, "linecircuit", "1");
            double r = getDouble(row, fields, "xfmrr", 0.0);
            if (r == 0.0) r = getDouble(row, fields, "liner", 0.0);
            double x = getDouble(row, fields, "xfmrx", 0.0);
            if (x == 0.0) x = getDouble(row, fields, "linex", 0.0);
            double tap = getDouble(row, fields, "xfmrfromtap", 1.0);
            double angle = getDouble(row, fields, "xfmrangle", 0.0);
            double rate1 = getDouble(row, fields, "linemva", 0.0);
            String status = getString(row, fields, "linestatus", "Closed");

            if (tap == 0.0) tap = 1.0;

            String fromBusId = BUS_ID_PREFIX + fromNum;
            String toBusId = BUS_ID_PREFIX + toNum;

            if (angle != 0.0) {
                builder.addPsXformer(fromBusId, toBusId, ckt,
                        new Complex(r, x), tap, 1.0, angle, 0.0,
                        null, null, rate1, 0.0, 0.0, 0, "Closed".equalsIgnoreCase(status));
            } else {
                builder.addXformer2W(fromBusId, toBusId, ckt,
                        new Complex(r, x), tap, 1.0,
                        null, null, rate1, 0.0, 0.0, 0, "Closed".equalsIgnoreCase(status));
            }
        }
    }

    private void processShuntData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int busNum = getInt(row, fields, "busnum", 0);
            if (busNum == 0) continue;

            double g = getDouble(row, fields, "shuntmw", 0.0);
            double b = getDouble(row, fields, "shuntmvar", 0.0);
            String status = getString(row, fields, "shuntstatus", "Closed");

            if ("Closed".equalsIgnoreCase(status) && (g != 0.0 || b != 0.0)) {
                String busId = BUS_ID_PREFIX + busNum;
                builder.addToBusShuntY(busId, new Complex(g / baseMva, b / baseMva));
            }
        }
    }

    private void processAreaData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int areaNum = getInt(row, fields, "areanum", 0);
            String name = getString(row, fields, "areaname", "Area " + areaNum);
            if (areaNum > 0) builder.addArea(String.valueOf(areaNum), name, null);
        }
    }

    private void processZoneData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int zoneNum = getInt(row, fields, "zonenum", 0);
            String name = getString(row, fields, "zonename", "Zone " + zoneNum);
            if (zoneNum > 0) builder.addZone(String.valueOf(zoneNum), name, null);
        }
    }

    // ==================== Utility Methods ====================

    private int findMatchingParen(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private int getInt(String[] row, Map<String, Integer> fields, String field, int defaultVal) {
        Integer idx = fields.get(field);
        if (idx == null || idx >= row.length) return defaultVal;
        try { return Integer.parseInt(row[idx].trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private double getDouble(String[] row, Map<String, Integer> fields, String field, double defaultVal) {
        Integer idx = fields.get(field);
        if (idx == null || idx >= row.length) return defaultVal;
        try { return Double.parseDouble(row[idx].trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private String getString(String[] row, Map<String, Integer> fields, String field, String defaultVal) {
        Integer idx = fields.get(field);
        if (idx == null || idx >= row.length) return defaultVal;
        return row[idx].trim();
    }
}
