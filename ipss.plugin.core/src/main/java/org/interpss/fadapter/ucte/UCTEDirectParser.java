/*
 * @(#)UCTEDirectParser.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.ucte;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

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
 * Direct UCTE-DEF file parser that bypasses the ODM XML layer.
 * Reads UCTE Data Exchange Format files and populates AclfNetwork via AclfNetworkBuilder.
 *
 * UCTE-DEF format has sections:
 * - ##C (comment)
 * - ##N (node/bus data)
 * - ##L (line data)
 * - ##T (transformer data)
 * - ##R (regulation data, tap changers)
 * - ##TT (2-winding transformer description)
 * - ##E (exchange powers)
 */
public class UCTEDirectParser {
    private static final Logger log = LoggerFactory.getLogger(UCTEDirectParser.class);

    private final AclfNetworkBuilder builder;
    private double baseMva = 100.0;

    public UCTEDirectParser() {
        this.builder = new AclfNetworkBuilder();
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            parseFromReader(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading UCTE file: " + filepath + ": " + e.getMessage());
        }
        return builder.getNetwork();
    }

    public AclfNetwork parseFromReader(BufferedReader reader) throws IOException, InterpssException {
        builder.setNetworkInfo("UCTE_Case", "UCTE-DEF Case", baseMva * 1000.0, OriginalDataFormat.UCTE);

        String line;
        String section = null;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("##C")) { section = "COMMENT"; continue; }
            if (trimmed.startsWith("##N")) { section = "NODE"; continue; }
            if (trimmed.startsWith("##L")) { section = "LINE"; continue; }
            if (trimmed.startsWith("##T")) {
                if (trimmed.startsWith("##TT")) { section = "XFR_DESC"; }
                else { section = "TRANSFORMER"; }
                continue;
            }
            if (trimmed.startsWith("##R")) { section = "REGULATION"; continue; }
            if (trimmed.startsWith("##E")) { section = "EXCHANGE"; continue; }

            switch (section != null ? section : "") {
                case "NODE": parseNodeLine(line); break;
                case "LINE": parseLineLine(line); break;
                case "TRANSFORMER": parseTransformerLine(line); break;
                case "REGULATION": parseRegulationLine(line); break;
                default: break;
            }
        }

        builder.finalizeNetwork();
        return builder.getNetwork();
    }

    // ==================== Node (Bus) Data ====================
    // UCTE node format: NodeCode(0-7) NodeName(9-20) Status(21) NodeType(24) Voltage(26-31)
    // P_load(33-39) Q_load(41-47) P_gen(49-55) Q_gen(57-63) Q_min(65-71) Q_max(73-79)
    // StaticP(81-87) StaticI(89-95) StaticZ(97-103) StatusReg(105-107) ...

    private void parseNodeLine(String line) throws InterpssException {
        if (line.length() < 32) return;

        String nodeCode = safeSubstring(line, 0, 8).trim();
        String nodeName = safeSubstring(line, 9, 21).trim();
        int status = parseInt(safeSubstring(line, 21, 22));
        int nodeType = parseInt(safeSubstring(line, 24, 25));  // 0=PQ, 1=Q-theta, 2=PV, 3=Slack
        double voltage = parseDouble(safeSubstring(line, 26, 32));

        double pLoad = line.length() > 39 ? parseDouble(safeSubstring(line, 33, 40)) : 0.0;
        double qLoad = line.length() > 47 ? parseDouble(safeSubstring(line, 41, 48)) : 0.0;
        double pGen = line.length() > 55 ? parseDouble(safeSubstring(line, 49, 56)) : 0.0;
        double qGen = line.length() > 63 ? parseDouble(safeSubstring(line, 57, 64)) : 0.0;
        double qMin = line.length() > 71 ? parseDouble(safeSubstring(line, 65, 72)) : 0.0;
        double qMax = line.length() > 79 ? parseDouble(safeSubstring(line, 73, 80)) : 0.0;

        // Base voltage determined from the 7th character of node code (voltage level)
        double baseKv = estimateBaseKvFromNodeCode(nodeCode);
        double vpu = (baseKv > 0 && voltage > 0) ? voltage / baseKv : 1.0;

        String busId = nodeCode;

        AclfBus bus = builder.addBus(busId, nodeName, 0, baseKv * 1000.0,
                vpu, 0.0, null, null, null);

        if (status == 1) bus.setStatus(false);

        if (nodeType == 3) {
            builder.setSwingBus(busId, vpu, 0.0);
        } else if (nodeType == 2) {
            bus.setGenCode(AclfGenCode.GEN_PV);
            builder.setPVBus(busId, pGen / baseMva, vpu, qMax / baseMva, qMin / baseMva, true);
        } else {
            if (pGen != 0.0 || qGen != 0.0) {
                bus.setGenCode(AclfGenCode.GEN_PQ);
            } else {
                bus.setGenCode(AclfGenCode.NON_GEN);
            }
        }

        if (pGen != 0.0 || qGen != 0.0) {
            builder.addContributeGen(busId, "1", true,
                    pGen / baseMva, qGen / baseMva, baseMva, vpu,
                    qMax / baseMva, qMin / baseMva, 0.0, 0.0,
                    null, null, 1.0, null, 1.0, 1.0);
        }

        if (pLoad != 0.0 || qLoad != 0.0) {
            Complex constP = new Complex(pLoad / baseMva, qLoad / baseMva);
            builder.addContributeLoad(busId, "1", true, constP, null, null, null, false);
        }
    }

    // ==================== Line Data ====================
    // UCTE line format: Node1(0-7) Node2(9-16) OrderCode(18) Status(20) R(22-27) X(29-34) B(36-43) I(45-50)

    private void parseLineLine(String line) throws InterpssException {
        if (line.length() < 44) return;

        String node1 = safeSubstring(line, 0, 8).trim();
        String node2 = safeSubstring(line, 9, 17).trim();
        String orderCode = safeSubstring(line, 18, 19).trim();
        int status = parseInt(safeSubstring(line, 20, 21));

        double r = parseDouble(safeSubstring(line, 22, 28));
        double x = parseDouble(safeSubstring(line, 29, 35));
        double b = parseDouble(safeSubstring(line, 36, 44));
        double ratingI = line.length() > 50 ? parseDouble(safeSubstring(line, 45, 51)) : 0.0;

        // Convert impedance from ohms to per-unit
        AclfBus fromBus = builder.getNetwork().getBus(node1);
        double baseKv = (fromBus != null) ? fromBus.getBaseVoltage() / 1000.0 : 1.0;
        double zBase = (baseKv * baseKv) / baseMva;

        double rpu = zBase > 0 ? r / zBase : r;
        double xpu = zBase > 0 ? x / zBase : x;
        double bpu = b * zBase / 1000000.0;  // convert from microsiemens to pu

        double ratingMva = ratingI * baseKv * Math.sqrt(3.0) / 1000.0;

        String cirId = orderCode.isEmpty() ? "1" : orderCode;

        builder.addLine(node1, node2, cirId,
                new Complex(rpu, xpu), new Complex(0.0, bpu * 0.5),
                null, null, ratingMva, 0.0, 0.0, status != 8 && status != 9);
    }

    // ==================== Transformer Data ====================
    // UCTE transformer: Node1(0-7) Node2(9-16) OrderCode(18) Status(20)
    //   V1(22-27) V2(29-34) NomPower(36-40) R(42-47) X(49-54)
    //   B(56-63) G(65-72) I(74-79)

    private void parseTransformerLine(String line) throws InterpssException {
        if (line.length() < 55) return;

        String node1 = safeSubstring(line, 0, 8).trim();
        String node2 = safeSubstring(line, 9, 17).trim();
        String orderCode = safeSubstring(line, 18, 19).trim();
        int status = parseInt(safeSubstring(line, 20, 21));

        double v1 = parseDouble(safeSubstring(line, 22, 28));
        double v2 = parseDouble(safeSubstring(line, 29, 35));
        double nomPower = parseDouble(safeSubstring(line, 36, 41));
        double r = parseDouble(safeSubstring(line, 42, 48));
        double x = parseDouble(safeSubstring(line, 49, 55));
        double b = line.length() > 63 ? parseDouble(safeSubstring(line, 56, 64)) : 0.0;
        double g = line.length() > 72 ? parseDouble(safeSubstring(line, 65, 73)) : 0.0;

        AclfBus fromBus = builder.getNetwork().getBus(node1);
        AclfBus toBus = builder.getNetwork().getBus(node2);

        double baseKvFrom = (fromBus != null) ? fromBus.getBaseVoltage() / 1000.0 : v1;
        double baseKvTo = (toBus != null) ? toBus.getBaseVoltage() / 1000.0 : v2;

        double sBase = nomPower > 0 ? nomPower : baseMva;
        double zBase = (v1 * v1) / sBase;
        double rpu = zBase > 0 ? r / zBase : r;
        double xpu = zBase > 0 ? x / zBase : x;

        // Adjust Z to system base
        if (sBase != baseMva) {
            rpu *= baseMva / sBase;
            xpu *= baseMva / sBase;
        }

        double fromTap = (baseKvFrom > 0 && v1 > 0) ? v1 / baseKvFrom : 1.0;
        double toTap = (baseKvTo > 0 && v2 > 0) ? v2 / baseKvTo : 1.0;

        Complex magY = null;
        if (g != 0.0 || b != 0.0) {
            double gpu = g * zBase / 1000000.0;
            double bpu2 = b * zBase / 1000000.0;
            magY = new Complex(gpu, bpu2);
        }

        String cirId = orderCode.isEmpty() ? "1" : orderCode;

        builder.addXformer2W(node1, node2, cirId,
                new Complex(rpu, xpu), fromTap / toTap, 1.0,
                magY, null, nomPower, 0.0, 0.0, 0, status != 8 && status != 9);
    }

    // ==================== Regulation Data ====================
    // Regulation applies to existing transformer records

    private void parseRegulationLine(String line) throws InterpssException {
        // Regulation data modifies existing transformer tap settings
        // Minimal implementation - tap adjustments are already applied via V1/V2 in transformer data
    }

    // ==================== Utility Methods ====================

    private double estimateBaseKvFromNodeCode(String nodeCode) {
        if (nodeCode.length() < 7) return 110.0;
        char voltLevel = nodeCode.charAt(6);
        switch (voltLevel) {
            case '0': return 750.0;
            case '1': return 380.0;
            case '2': return 220.0;
            case '3': return 150.0;
            case '4': return 120.0;
            case '5': return 110.0;
            case '6': return 70.0;
            case '7': return 27.0;
            case '8': return 330.0;
            case '9': return 500.0;
            default: return 110.0;
        }
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
