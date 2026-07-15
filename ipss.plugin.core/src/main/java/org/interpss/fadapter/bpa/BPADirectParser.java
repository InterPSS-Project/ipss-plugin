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

        if (c0 == 'B') return "B";
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
    // BPA bus card (B type): fixed columns (1-based cols, 0-based [start,end) in code)
    // Col 1-2: card type + subtype (blank=load, T=gen-PQ, E=fixed-V gen, Q=gen w/ Q limits,
    //          G=remote-V gen, S=swing, X=swing)
    // Col 3: change code
    // Col 4-6: owner
    // Col 7-14: bus name (8 chars)          -> [6,14)
    // Col 15-18: base kV                    -> [14,18)
    // Col 19-20: zone number                -> [18,20)
    // Col 21-25: P load (MW)                -> [20,25)
    // Col 26-30: Q load (MVAR)              -> [25,30)
    // Col 31-34: G shunt (MW)               -> [30,34)
    // Col 35-38: B shunt (MVAR)             -> [34,38)
    // Col 39-42: P gen max (MW)             -> [38,42)
    // Col 43-47: P gen (MW)                 -> [42,47)
    // Col 48-52: Q sched or Q max (MVAR)    -> [47,52)
    // Col 53-57: Q min (MVAR)               -> [52,57)
    // Col 58-61: V hold/max (pu, F4.3 implied decimal) -> [57,61)
    // Col 62-65: V min (pu)                 -> [61,65)
    // Col 66-73: Remote bus name            -> [65,73)

    private void parseBusCard(String line) throws InterpssException {
        if (line.length() < 18) return;

        char subtype = line.charAt(1);
        String busName = safeSubstring(line, 6, 14).trim();
        double baseKv = parseDouble(safeSubstring(line, 14, 18));
        int zone = parseInt(safeSubstring(line, 18, 20));

        if (busName.isEmpty()) return;

        String busId = makeBusId(busName, baseKv);

        double pLoad = parseDouble(safeSubstring(line, 20, 25));
        double qLoad = parseDouble(safeSubstring(line, 25, 30));
        double gShunt = parseDouble(safeSubstring(line, 30, 34));
        double bShunt = parseDouble(safeSubstring(line, 34, 38));
        double pMax = parseDouble(safeSubstring(line, 38, 42));
        double pGen = parseDouble(safeSubstring(line, 42, 47));
        // col 48-52 holds Q-sched for PQ-gen buses, Q-max for V-controlled buses
        double qSchedOrMax = parseDouble(safeSubstring(line, 47, 52));
        double qMin = parseDouble(safeSubstring(line, 52, 57));
        double vSched = parseBpaVoltage(safeSubstring(line, 57, 61));

        String zoneId = zone > 0 ? String.valueOf(zone) : null;
        if (zoneId != null) builder.addZone(zoneId, "Zone " + zone, null);

        double vpu = vSched;
        if (vpu > 2.0 && baseKv > 0) vpu = vSched / baseKv;
        if (vpu <= 0.0) vpu = 1.0;

        BaseAclfBus bus = builder.addBus(busId, busName, 0, baseKv * 1000.0,
                vpu, 0.0, null, zoneId, null);

        // BPA bus subtype classification (same as ODM BPABusRecord):
        //  BS/BX = swing; BQ/BG/BF = PV with Q limits; BE = PV without Q limits;
        //  B/BC/BT/BV and others = PQ (load or PQ-gen)
        boolean isSwing = subtype == 'S' || subtype == 'X';
        boolean isPv = subtype == 'Q' || subtype == 'G' || subtype == 'F';
        boolean isPvNoQLimit = subtype == 'E';
        boolean vControlled = isSwing || isPv || isPvNoQLimit;
        double qSched = vControlled ? 0.0 : qSchedOrMax;
        double qMax = vControlled ? qSchedOrMax : 0.0;

        if (isSwing) {
            builder.setSwingBus(busId, vpu, 0.0);
        } else if (isPv || isPvNoQLimit) {
            bus.setGenCode(AclfGenCode.GEN_PV);
            builder.setPVBus(busId, pGen / baseMva, vpu,
                    qMax / baseMva, qMin / baseMva, isPv);
        } else if (pGen != 0.0 || qSched != 0.0) {
            bus.setGenCode(AclfGenCode.GEN_PQ);
            builder.setPQBus(busId, pGen / baseMva, qSched / baseMva, 0.0, 0.0);
        } else {
            bus.setGenCode(AclfGenCode.NON_GEN);
        }

        if (vControlled || pGen != 0.0 || qSched != 0.0) {
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

    /**
     * BPA voltage fields are F4.3 with an implied decimal point:
     * "1040" means 1.040 pu, but an explicit decimal ("1.01") is used as-is.
     */
    private static double parseBpaVoltage(String field) {
        String s = field == null ? "" : field.trim();
        if (s.isEmpty()) return 0.0;
        double v = parseDouble(s);
        if (!s.contains(".")) v /= 1000.0;
        return v;
    }

    /**
     * BPA impedance/admittance fields are F6.5 with an implied decimal point:
     * "  8500" means 0.08500 pu, but an explicit decimal (".0085") is used as-is.
     */
    private static double parseBpaImpedance(String field) {
        String s = field == null ? "" : field.trim();
        if (s.isEmpty()) return 0.0;
        double v = parseDouble(s);
        if (!s.contains(".") && Math.abs(v) >= 1.0) v /= 100000.0;
        return v;
    }

    // ==================== Branch Records ====================
    // BPA line card (L type): fixed columns (1-based cols, 0-based [start,end) in code)
    // Col 1-2: card type + subtype
    // Col 3: change code
    // Col 4-6: owner
    // Col 7-14: bus1 name       -> [6,14)
    // Col 15-18: bus1 base kV   -> [14,18)
    // Col 19: metered end
    // Col 20-27: bus2 name      -> [19,27)
    // Col 28-31: bus2 base kV   -> [27,31)
    // Col 32: circuit           -> [31,32)
    // Col 33: section           -> [32,33)
    // Col 34-38: current rating -> [33,38)
    // Col 39-44: R (pu, F6.5)   -> [38,44)
    // Col 45-50: X (pu, F6.5)   -> [44,50)
    // Col 51-56: G/2 (pu, F6.5) -> [50,56)
    // Col 57-62: B/2 (pu, F6.5) -> [56,62)

    private void parseLineCard(String line) throws InterpssException {
        if (line.length() < 46) return;

        String bus1Name = safeSubstring(line, 6, 14).trim();
        double bus1Kv = parseDouble(safeSubstring(line, 14, 18));
        String bus2Name = safeSubstring(line, 19, 27).trim();
        double bus2Kv = parseDouble(safeSubstring(line, 27, 31));
        String circuit = safeSubstring(line, 31, 32).trim();
        if (circuit.isEmpty()) circuit = "1";

        double r = parseBpaImpedance(safeSubstring(line, 38, 44));
        double x = parseBpaImpedance(safeSubstring(line, 44, 50));
        double halfG = parseBpaImpedance(safeSubstring(line, 50, 56));
        double halfB = parseBpaImpedance(safeSubstring(line, 56, 62));

        String fromBusId = makeBusId(bus1Name, bus1Kv);
        String toBusId = makeBusId(bus2Name, bus2Kv);

        builder.addLine(fromBusId, toBusId, circuit,
                new Complex(r, x), new Complex(halfG, halfB),
                null, null, 0.0, 0.0, 0.0, true);
    }

    private void parseEquivLineCard(String line) throws InterpssException {
        // E cards represent equivalent branches (similar to L)
        parseLineCard(line);
    }

    // ==================== Transformer Records ====================

    // BPA transformer card (T type): fixed columns (0-based [start,end))
    // Col 7-14: bus1 name  -> [6,14),  Col 15-18: bus1 kV -> [14,18)
    // Col 20-27: bus2 name -> [19,27), Col 28-31: bus2 kV -> [27,31)
    // Col 32: circuit -> [31,32)
    // Col 39-44: R (pu, F6.5) -> [38,44), Col 45-50: X -> [44,50)
    // Col 51-56: G (pu) -> [50,56), Col 57-62: B -> [56,62)
    // Col 63-67: from-side tap (kV, F5.2) -> [62,67)
    // Col 68-72: to-side tap (kV, F5.2)   -> [67,72)

    private void parseTransformerCard(String line) throws InterpssException {
        if (line.length() < 46) return;

        String bus1Name = safeSubstring(line, 6, 14).trim();
        double bus1Kv = parseDouble(safeSubstring(line, 14, 18));
        String bus2Name = safeSubstring(line, 19, 27).trim();
        double bus2Kv = parseDouble(safeSubstring(line, 27, 31));
        String circuit = safeSubstring(line, 31, 32).trim();
        if (circuit.isEmpty()) circuit = "1";

        double r = parseBpaImpedance(safeSubstring(line, 38, 44));
        double x = parseBpaImpedance(safeSubstring(line, 44, 50));

        double tapKv1 = parseDouble(safeSubstring(line, 62, 67));
        double tapKv2 = parseDouble(safeSubstring(line, 67, 72));

        // taps are rated winding voltages in kV; F5.2 implied-decimal if oversized
        if (tapKv1 >= 2.0 * bus1Kv && bus1Kv > 0) tapKv1 /= 100.0;
        if (tapKv2 >= 2.0 * bus2Kv && bus2Kv > 0) tapKv2 /= 100.0;

        double tap1 = (tapKv1 > 0 && bus1Kv > 0) ? tapKv1 / bus1Kv : 1.0;
        double tap2 = (tapKv2 > 0 && bus2Kv > 0) ? tapKv2 / bus2Kv : 1.0;

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
