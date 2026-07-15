/*
 * @(#)BPADirectParser.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.bpa;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
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
 * Direct BPA (Bonneville Power Administration) file parser that bypasses the ODM XML layer.
 * Reads BPA power flow data files and populates AclfNetwork via AclfNetworkBuilder.
 *
 * BPA format uses fixed-column card-type records identified by the first 1-3 characters:
 * - B/BC/BV/BQ/BG/BS: Bus records (different types)
 * - L/E/T/TP/R/RV/RQ/RN/RP: Branch/transformer records
 * - A: Area records
 * - +A: Area continuation
 * - .  : Comment
 * - (END): End of file
 *
 * Note: BPA originally loaded as DStab via ODM. This direct parser handles the load flow
 * portion only for the Aclf network model.
 */
public class BPADirectParser {
    private static final Logger log = LoggerFactory.getLogger(BPADirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus_";

    private final AclfNetworkBuilder builder;
    private double baseMva = 100.0;
    private int busCounter = 0;
    private final Map<String, String> busKeyToIdMap = new HashMap<>();

    public BPADirectParser() {
        this.builder = new AclfNetworkBuilder();
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            parseFromReader(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading BPA file: " + filepath + ": " + e.getMessage());
        }
        return builder.getNetwork();
    }

    public AclfNetwork parseFromReader(BufferedReader reader) throws IOException, InterpssException {
        builder.setNetworkInfo("BPA_Case", "BPA Case", baseMva * 1000.0, OriginalDataFormat.BPA);

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() < 2) continue;
            if (line.startsWith(".") || line.startsWith("C ")) continue;  // comment
            if (line.startsWith("(END)") || line.startsWith("(end)")) break;

            String cardType = getCardType(line);
            switch (cardType) {
                case "B":   parseBusCard(line); break;
                case "BC":  parseBusContinuation(line); break;
                case "BV":  parseBusVoltageCard(line); break;
                case "BG":  parseBusGenCard(line); break;
                case "L":   parseLineCard(line); break;
                case "E":   parseEquivLineCard(line); break;
                case "T":   parseTransformerCard(line); break;
                case "TP":  parseTapCard(line); break;
                case "A":   parseAreaCard(line); break;
                default:    break;
            }
        }

        builder.finalizeNetwork();
        return builder.getNetwork();
    }

    // ==================== Card Type Detection ====================

    private String getCardType(String line) {
        if (line.length() < 2) return "";
        char c0 = line.charAt(0);
        char c1 = line.charAt(1);

        if (c0 == 'B') {
            if (c1 == 'C') return "BC";
            if (c1 == 'V') return "BV";
            if (c1 == 'G') return "BG";
            if (c1 == ' ' || c1 == 'T' || c1 == 'F' || c1 == 'X' || c1 == 'S' || c1 == 'E' || c1 == 'Q') return "B";
            return "B";
        }
        if (c0 == 'L') return "L";
        if (c0 == 'E') return "E";
        if (c0 == 'T') {
            if (c1 == 'P') return "TP";
            return "T";
        }
        if (c0 == 'R') return "R";
        if (c0 == 'A') return "A";
        if (c0 == '+' && c1 == 'A') return "+A";

        return "";
    }

    // ==================== Bus Records ====================
    // BPA bus card (B type): fixed columns
    // Col 1: B (card type)
    // Col 2: subtype (blank=load, T=gen-PQ, F=gen-PV, X=swing)
    // Col 3: change code
    // Col 4-6: owner
    // Col 7-14: bus name (8 chars)
    // Col 15-18: base kV
    // Col 19-20: zone number
    // Col 21-25: P load (MW)
    // Col 26-30: Q load (MVAR)
    // Col 31-34: G shunt (MW)
    // Col 35-38: B shunt (MVAR)
    // Col 39-43: P gen max (MW)
    // Col 44-47: P gen (MW)
    // Col 48-52: Q gen scheduled (MVAR)
    // Col 53-57: Q gen max (MVAR)
    // Col 58-62: Q gen min (MVAR)
    // Col 63-67: V scheduled (kV or pu)
    // Col 68-75: Remote bus name

    private void parseBusCard(String line) throws InterpssException {
        if (line.length() < 18) return;

        char subtype = line.charAt(1);
        String busName = safeSubstring(line, 6, 14).trim();
        double baseKv = parseDouble(safeSubstring(line, 14, 18));
        int zone = parseInt(safeSubstring(line, 18, 20));

        if (busName.isEmpty()) return;

        String busId = makeBusId(busName, baseKv);

        double pLoad = line.length() > 25 ? parseDouble(safeSubstring(line, 20, 25)) : 0.0;
        double qLoad = line.length() > 30 ? parseDouble(safeSubstring(line, 25, 30)) : 0.0;
        double gShunt = line.length() > 34 ? parseDouble(safeSubstring(line, 30, 34)) : 0.0;
        double bShunt = line.length() > 38 ? parseDouble(safeSubstring(line, 34, 38)) : 0.0;
        double pMax = line.length() > 43 ? parseDouble(safeSubstring(line, 38, 43)) : 0.0;
        double pGen = line.length() > 47 ? parseDouble(safeSubstring(line, 43, 47)) : 0.0;
        double qSched = line.length() > 52 ? parseDouble(safeSubstring(line, 47, 52)) : 0.0;
        double qMax = line.length() > 57 ? parseDouble(safeSubstring(line, 52, 57)) : 0.0;
        double qMin = line.length() > 62 ? parseDouble(safeSubstring(line, 57, 62)) : 0.0;
        double vSched = line.length() > 67 ? parseDouble(safeSubstring(line, 62, 67)) : 0.0;

        String zoneId = zone > 0 ? String.valueOf(zone) : null;
        if (zoneId != null) builder.addZone(zoneId, "Zone " + zone, null);

        double vpu = vSched;
        if (vpu > 2.0 && baseKv > 0) vpu = vSched / baseKv;
        if (vpu <= 0.0) vpu = 1.0;

        BaseAclfBus bus = builder.addBus(busId, busName, 0, baseKv * 1000.0,
                vpu, 0.0, null, zoneId, null);

        if (subtype == 'X' || subtype == 'S') {
            builder.setSwingBus(busId, vpu, 0.0);
        } else if (subtype == 'F' || subtype == 'E' || subtype == 'Q') {
            bus.setGenCode(AclfGenCode.GEN_PV);
            builder.setPVBus(busId, pGen / baseMva, vpu, qMax / baseMva, qMin / baseMva, true);
        } else if (subtype == 'T' || pGen != 0.0) {
            bus.setGenCode(AclfGenCode.GEN_PQ);
            builder.setPQBus(busId, pGen / baseMva, qSched / baseMva, 0.0, 0.0);
        } else {
            bus.setGenCode(AclfGenCode.NON_GEN);
        }

        if (pGen != 0.0 || qSched != 0.0) {
            builder.addContributeGen(busId, "1", true,
                    pGen / baseMva, qSched / baseMva, baseMva, vpu,
                    qMax / baseMva, qMin / baseMva, pMax / baseMva, 0.0,
                    null, null, 1.0, null, 1.0, 1.0);
        }

        if (pLoad != 0.0 || qLoad != 0.0) {
            Complex constP = new Complex(pLoad / baseMva, qLoad / baseMva);
            builder.addContributeLoad(busId, "1", true, constP, null, null, null, false);
        }

        if (gShunt != 0.0 || bShunt != 0.0) {
            builder.addToBusShuntY(busId, new Complex(gShunt / baseMva, bShunt / baseMva));
        }
    }

    private void parseBusContinuation(String line) {
        // BC cards add additional data to the preceding bus
    }

    private void parseBusVoltageCard(String line) {
        // BV cards specify voltage limit data
    }

    private void parseBusGenCard(String line) {
        // BG cards add generator data
    }

    // ==================== Branch Records ====================
    // BPA line card (L type): fixed columns
    // Col 1: L (card type)
    // Col 2: change code
    // Col 3: owner
    // Col 4-6: metered end
    // Col 7-14: bus1 name
    // Col 15-18: bus1 base kV
    // Col 19: metered
    // Col 20-27: bus2 name
    // Col 28-31: bus2 base kV
    // Col 32: circuit
    // Col 33: section
    // Col 34-39: R (pu)
    // Col 40-45: X (pu)
    // Col 46-49: G1 (micro mhos)
    // Col 50-53: B1 (micro mhos)
    // Col 54-57: G2 (micro mhos)
    // Col 58-61: B2 (micro mhos)
    // Col 62-65: rating (amps)
    // ...

    private void parseLineCard(String line) throws InterpssException {
        if (line.length() < 46) return;

        String bus1Name = safeSubstring(line, 6, 14).trim();
        double bus1Kv = parseDouble(safeSubstring(line, 14, 18));
        String bus2Name = safeSubstring(line, 19, 27).trim();
        double bus2Kv = parseDouble(safeSubstring(line, 27, 31));
        String circuit = safeSubstring(line, 31, 32).trim();
        if (circuit.isEmpty()) circuit = "1";

        double r = parseDouble(safeSubstring(line, 33, 39));
        double x = parseDouble(safeSubstring(line, 39, 45));
        double g1 = line.length() > 49 ? parseDouble(safeSubstring(line, 45, 49)) : 0.0;
        double b1 = line.length() > 53 ? parseDouble(safeSubstring(line, 49, 53)) : 0.0;
        double g2 = line.length() > 57 ? parseDouble(safeSubstring(line, 53, 57)) : 0.0;
        double b2 = line.length() > 61 ? parseDouble(safeSubstring(line, 57, 61)) : 0.0;

        String fromBusId = makeBusId(bus1Name, bus1Kv);
        String toBusId = makeBusId(bus2Name, bus2Kv);

        // Convert charging from micro-mhos to pu
        double halfB = (b1 + b2) * 0.5 / 1000000.0;

        builder.addLine(fromBusId, toBusId, circuit,
                new Complex(r, x), new Complex(0.0, halfB),
                null, null, 0.0, 0.0, 0.0, true);
    }

    private void parseEquivLineCard(String line) throws InterpssException {
        // E cards represent equivalent branches (similar to L)
        parseLineCard(line);
    }

    // ==================== Transformer Records ====================

    private void parseTransformerCard(String line) throws InterpssException {
        if (line.length() < 46) return;

        String bus1Name = safeSubstring(line, 6, 14).trim();
        double bus1Kv = parseDouble(safeSubstring(line, 14, 18));
        String bus2Name = safeSubstring(line, 19, 27).trim();
        double bus2Kv = parseDouble(safeSubstring(line, 27, 31));
        String circuit = safeSubstring(line, 31, 32).trim();
        if (circuit.isEmpty()) circuit = "1";

        double r = parseDouble(safeSubstring(line, 38, 44));
        double x = parseDouble(safeSubstring(line, 44, 50));

        double tap1 = line.length() > 67 ? parseDouble(safeSubstring(line, 62, 67)) : 0.0;
        double tap2 = line.length() > 72 ? parseDouble(safeSubstring(line, 67, 72)) : 0.0;

        if (tap1 == 0.0) tap1 = 1.0;
        if (tap2 == 0.0) tap2 = 1.0;

        // If taps are in kV, convert to pu
        if (tap1 > 2.0 && bus1Kv > 0) tap1 = tap1 / bus1Kv;
        if (tap2 > 2.0 && bus2Kv > 0) tap2 = tap2 / bus2Kv;

        String fromBusId = makeBusId(bus1Name, bus1Kv);
        String toBusId = makeBusId(bus2Name, bus2Kv);

        builder.addXformer2W(fromBusId, toBusId, circuit,
                new Complex(r, x), tap1, tap2,
                null, null, 0.0, 0.0, 0.0, 0, true);
    }

    private void parseTapCard(String line) {
        // TP cards modify transformer tap settings
    }

    // ==================== Area Records ====================

    private void parseAreaCard(String line) throws InterpssException {
        if (line.length() < 14) return;
        String areaName = safeSubstring(line, 3, 13).trim();
        if (!areaName.isEmpty()) {
            builder.addArea(areaName, areaName, null);
        }
    }

    // ==================== Utility Methods ====================

    private String makeBusId(String name, double baseKv) {
        String key = name + "_" + (int) baseKv;
        return busKeyToIdMap.computeIfAbsent(key, k -> "Bus" + (++busCounter));
    }

    private static String safeSubstring(String str, int start, int end) {
        if (str == null) return "";
        if (start >= str.length()) return "";
        if (end > str.length()) end = str.length();
        return str.substring(start, end);
    }

    private static int parseInt(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return 0; }
    }

    private static double parseDouble(String s) {
        if (s == null) return 0.0;
        s = s.trim();
        if (s.isEmpty()) return 0.0;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
