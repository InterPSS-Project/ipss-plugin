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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Direct PowerWorld (PWD/AUX) file parser that bypasses the ODM XML layer.
 * Reads PowerWorld auxiliary files and populates AclfNetwork via AclfNetworkBuilder.
 *
 * Legacy AUX format uses DATA sections with field definitions:
 *   DATA (BUS, [BusNum, BusName, BusNomVolt, BusSlack, ...])
 *   {
 *     1 "Bus1" 345.0 "YES" ...
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

        // Preserve newlines so DATA rows remain one-record-per-line.
        StringBuilder buffer = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//")) {
                continue; // comment line
            }
            buffer.append(line).append('\n');
        }

        parseDataSections(buffer.toString());

        builder.finalizeNetwork();
        return builder.getNetwork();
    }

    private void parseDataSections(String content) throws InterpssException {
        int pos = 0;
        while (pos < content.length()) {
            int dataIdx = indexOfIgnoreCase(content, "DATA", pos);
            if (dataIdx < 0) break;

            // Avoid matching words that contain "DATA" inside comments already stripped
            int parenStart = content.indexOf('(', dataIdx);
            if (parenStart < 0) break;

            int parenEnd = findMatchingParen(content, parenStart);
            if (parenEnd < 0) break;

            String header = content.substring(parenStart + 1, parenEnd).trim();

            int braceStart = content.indexOf('{', parenEnd);
            if (braceStart < 0) {
                pos = parenEnd + 1;
                continue;
            }

            int braceEnd = findMatchingBrace(content, braceStart);
            if (braceEnd < 0) {
                pos = braceStart + 1;
                continue;
            }

            String dataBlock = content.substring(braceStart + 1, braceEnd);
            parseDataBlock(header, dataBlock);
            pos = braceEnd + 1;
        }
    }

    private void parseDataBlock(String header, String dataBlock) throws InterpssException {
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

        List<String[]> rows = parseDataRows(dataBlock);

        switch (dataType) {
            case "BUS": processBusData(fieldMap, rows); break;
            case "GEN": case "GENERATOR": processGenData(fieldMap, rows); break;
            case "LOAD": processLoadData(fieldMap, rows); break;
            case "BRANCH": processBranchData(fieldMap, rows); break;
            case "TRANSFORMER": processTransformerData(fieldMap, rows); break;
            case "SHUNT": processShuntData(fieldMap, rows); break;
            case "AREA": processAreaData(fieldMap, rows); break;
            case "ZONE": processZoneData(fieldMap, rows); break;
            default:
                // OWNER, PWCASEINFORMATION, CONTINGENCY, SCRIPT, etc. — skip safely
                log.debug("Skipping unsupported PWD DATA type: {}", dataType);
                break;
        }
    }

    private List<String[]> parseDataRows(String dataBlock) {
        List<String[]> rows = new ArrayList<>();
        String[] lines = dataBlock.split("\\r?\\n|;");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
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
                if (inQuote) {
                    // Closing quote — emit token even if empty (e.g. "")
                    tokens.add(current.toString());
                    current.setLength(0);
                    inQuote = false;
                } else {
                    inQuote = true;
                }
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
            int busNum = getInt(row, fields, 0, "busnum");
            if (busNum == 0) continue;

            String name = getString(row, fields, "", "busname");
            double baseKv = getDouble(row, fields, 0.0, "busnomvolt", "nomkv");
            String busType = getString(row, fields, "PQ", "bustype");
            String busSlack = getString(row, fields, "", "busslack");
            double vm = getDouble(row, fields, 1.0, "buspuvolt");
            double va = getDouble(row, fields, 0.0, "busangle");
            int area = getInt(row, fields, 0, "areanum", "areaname");
            int zone = getInt(row, fields, 0, "zonenum", "zonename");

            String busId = BUS_ID_PREFIX + busNum;
            String areaId = area > 0 ? String.valueOf(area) : null;
            String zoneId = zone > 0 ? String.valueOf(zone) : null;

            if (areaId != null) builder.addArea(areaId, "Area " + area, null);
            if (zoneId != null) builder.addZone(zoneId, "Zone " + zone, null);

            BaseAclfBus bus = builder.addBus(busId, name, busNum, baseKv * 1000.0,
                    vm, Math.toRadians(va), areaId, zoneId, null);

            boolean isSwing = "Slack".equalsIgnoreCase(busType)
                    || "Swing".equalsIgnoreCase(busType)
                    || "YES".equalsIgnoreCase(busSlack.trim());
            if (isSwing) {
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
            int busNum = getInt(row, fields, 0, "busnum");
            if (busNum == 0) continue;

            String genId = getString(row, fields, "1", "genid").trim();
            if (genId.isEmpty()) genId = "1";
            double pg = getDouble(row, fields, 0.0, "genmw");
            double qg = getDouble(row, fields, 0.0, "genmvr", "genmvar");
            double qmax = getDouble(row, fields, 0.0, "genmvrmax", "genmvarmax");
            double qmin = getDouble(row, fields, 0.0, "genmvrmin", "genmvarmin");
            double vs = getDouble(row, fields, 1.0, "genvoltset", "genregpuvolt");
            double mbase = getDouble(row, fields, baseMva, "genmvabase");
            String status = getString(row, fields, "Open", "genstatus");
            double pmax = getDouble(row, fields, 0.0, "genmwmax");
            double pmin = getDouble(row, fields, 0.0, "genmwmin");

            if (mbase == 0.0) mbase = baseMva;
            String busId = BUS_ID_PREFIX + busNum;
            boolean genStatus = "Closed".equalsIgnoreCase(status.trim());

            builder.addContributeGen(busId, genId, genStatus,
                    pg / baseMva, qg / baseMva, mbase, vs,
                    qmax / baseMva, qmin / baseMva, pmax / baseMva, pmin / baseMva,
                    null, null, 1.0, null, 1.0, 1.0);

            // Contribute gens do not set bus genCode; AVR-able gens → PV (unless already swing)
            BaseAclfBus bus = builder.getNetwork().getBus(busId);
            if (bus != null && bus.getGenCode() != AclfGenCode.SWING && genStatus) {
                String avr = getString(row, fields, "YES", "genavrable");
                if ("YES".equalsIgnoreCase(avr.trim()) || vs > 0.0) {
                    bus.setGenCode(AclfGenCode.GEN_PV);
                } else {
                    bus.setGenCode(AclfGenCode.GEN_PQ);
                }
            }
        }
    }

    private void processLoadData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int busNum = getInt(row, fields, 0, "busnum");
            if (busNum == 0) continue;

            String loadId = getString(row, fields, "1", "loadid").trim();
            if (loadId.isEmpty()) loadId = "1";
            double pl = getDouble(row, fields, 0.0, "loadsmw", "loadmw");
            double ql = getDouble(row, fields, 0.0, "loadsmvr", "loadmvar");
            String status = getString(row, fields, "Closed", "loadstatus");

            String busId = BUS_ID_PREFIX + busNum;
            Complex constP = (pl != 0.0 || ql != 0.0) ? new Complex(pl / baseMva, ql / baseMva) : null;

            builder.addContributeLoad(busId, loadId, "Closed".equalsIgnoreCase(status.trim()),
                    constP, null, null, null, false);
        }
    }

    private void processBranchData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int fromNum = getInt(row, fields, 0, "busnumfrom", "busnum");
            int toNum = getInt(row, fields, 0, "busnumto", "busnum:1");
            if (fromNum == 0 || toNum == 0) continue;

            String ckt = getString(row, fields, "1", "linecircuit").trim();
            if (ckt.isEmpty()) ckt = "1";
            double r = getDouble(row, fields, 0.0, "liner");
            double x = getDouble(row, fields, 0.0, "linex");
            double b = getDouble(row, fields, 0.0, "linec", "lineb");
            double rate1 = getDouble(row, fields, 0.0, "lineamva", "linemva", "limitmva");
            String status = getString(row, fields, "Closed", "linestatus");
            double tap = getDouble(row, fields, 1.0, "linetap");
            double phaseDeg = getDouble(row, fields, 0.0, "linephase");
            String deviceType = getString(row, fields, "", "branchdevicetype");

            if (tap == 0.0) tap = 1.0;

            String fromBusId = BUS_ID_PREFIX + fromNum;
            String toBusId = BUS_ID_PREFIX + toNum;
            boolean closed = "Closed".equalsIgnoreCase(status.trim());

            boolean isXfr = "Transformer".equalsIgnoreCase(deviceType.trim())
                    || Math.abs(tap - 1.0) > 1.0e-6
                    || Math.abs(phaseDeg) > 1.0e-6;

            if (isXfr) {
                if (Math.abs(phaseDeg) > 1.0e-6) {
                    builder.addPsXformer(fromBusId, toBusId, ckt,
                            new Complex(r, x), tap, 1.0, Math.toRadians(phaseDeg), 0.0,
                            null, null, rate1, 0.0, 0.0, 0, closed);
                } else {
                    builder.addXformer2W(fromBusId, toBusId, ckt,
                            new Complex(r, x), tap, 1.0,
                            null, null, rate1, 0.0, 0.0, 0, closed);
                }
            } else {
                builder.addLine(fromBusId, toBusId, ckt,
                        new Complex(r, x), new Complex(0.0, b * 0.5),
                        null, null, rate1, 0.0, 0.0, closed);
            }
        }
    }

    private void processTransformerData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        // Optional overlay: only create equipment when R/X are present.
        // Typical PW exports put impedance on BRANCH and control settings here.
        for (String[] row : rows) {
            int fromNum = getInt(row, fields, 0, "busnumfrom", "busnum");
            int toNum = getInt(row, fields, 0, "busnumto", "busnum:1");
            if (fromNum == 0 || toNum == 0) continue;

            String ckt = getString(row, fields, "1", "linecircuit").trim();
            if (ckt.isEmpty()) ckt = "1";
            double r = getDouble(row, fields, 0.0, "xfmrr", "liner");
            double x = getDouble(row, fields, 0.0, "xfmrx", "linex");
            if (r == 0.0 && x == 0.0) {
                continue; // control-only TRANSFORMER row; BRANCH already has the device
            }

            double tap = getDouble(row, fields, 1.0, "xfmrfromtap", "linetap", "xffixedtap");
            double angle = getDouble(row, fields, 0.0, "xfmrangle", "linephase");
            double rate1 = getDouble(row, fields, 0.0, "lineamva", "linemva");
            String status = getString(row, fields, "Closed", "linestatus");

            if (tap == 0.0) tap = 1.0;

            String fromBusId = BUS_ID_PREFIX + fromNum;
            String toBusId = BUS_ID_PREFIX + toNum;
            boolean closed = "Closed".equalsIgnoreCase(status.trim());

            double angleRad = Math.abs(angle) > Math.PI ? Math.toRadians(angle) : angle;
            if (Math.abs(angle) > 1.0e-6) {
                builder.addPsXformer(fromBusId, toBusId, ckt,
                        new Complex(r, x), tap, 1.0, angleRad, 0.0,
                        null, null, rate1, 0.0, 0.0, 0, closed);
            } else {
                builder.addXformer2W(fromBusId, toBusId, ckt,
                        new Complex(r, x), tap, 1.0,
                        null, null, rate1, 0.0, 0.0, 0, closed);
            }
        }
    }

    private void processShuntData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int busNum = getInt(row, fields, 0, "busnum");
            if (busNum == 0) continue;

            double g = getDouble(row, fields, 0.0, "shuntmw");
            double b = getDouble(row, fields, 0.0, "shuntmvar", "shuntmvr");
            String status = getString(row, fields, "Closed", "shuntstatus");

            if ("Closed".equalsIgnoreCase(status.trim()) && (g != 0.0 || b != 0.0)) {
                String busId = BUS_ID_PREFIX + busNum;
                builder.addToBusShuntY(busId, new Complex(g / baseMva, b / baseMva));
            }
        }
    }

    private void processAreaData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int areaNum = getInt(row, fields, 0, "areanum");
            String name = getString(row, fields, "Area " + areaNum, "areaname");
            if (areaNum > 0) builder.addArea(String.valueOf(areaNum), name, null);
        }
    }

    private void processZoneData(Map<String, Integer> fields, List<String[]> rows) throws InterpssException {
        for (String[] row : rows) {
            int zoneNum = getInt(row, fields, 0, "zonenum");
            String name = getString(row, fields, "Zone " + zoneNum, "zonename");
            if (zoneNum > 0) builder.addZone(String.valueOf(zoneNum), name, null);
        }
    }

    // ==================== Utility Methods ====================

    private static int indexOfIgnoreCase(String s, String target, int from) {
        String lower = s.toLowerCase();
        return lower.indexOf(target.toLowerCase(), from);
    }

    private int findMatchingParen(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int getInt(String[] row, Map<String, Integer> fields, int defaultVal, String... names) {
        for (String name : names) {
            Integer idx = fields.get(name);
            if (idx == null || idx >= row.length) continue;
            try {
                return Integer.parseInt(row[idx].trim());
            } catch (NumberFormatException e) {
                // try next alias
            }
        }
        return defaultVal;
    }

    private double getDouble(String[] row, Map<String, Integer> fields, double defaultVal, String... names) {
        for (String name : names) {
            Integer idx = fields.get(name);
            if (idx == null || idx >= row.length) continue;
            try {
                return Double.parseDouble(row[idx].trim());
            } catch (NumberFormatException e) {
                // try next alias
            }
        }
        return defaultVal;
    }

    private String getString(String[] row, Map<String, Integer> fields, String defaultVal, String... names) {
        for (String name : names) {
            Integer idx = fields.get(name);
            if (idx == null || idx >= row.length) continue;
            return row[idx].trim();
        }
        return defaultVal;
    }
}
