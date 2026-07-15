/*
 * @(#)IeeeCDFDirectParser.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.ieeecdf;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

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
 * Direct IEEE Common Data Format (CDF) file parser that bypasses the ODM XML layer.
 * Reads IEEE CDF files and populates AclfNetwork via AclfNetworkBuilder.
 *
 * The IEEE CDF format uses fixed-column positions for bus and branch data.
 * Reference: IEEE Standard for Power Flow Data Exchange (IEEE Std 399-1997)
 */
public class IeeeCDFDirectParser {
    private static final Logger log = LoggerFactory.getLogger(IeeeCDFDirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus";

    private final AclfNetworkBuilder builder;
    private double baseMva = 100.0;
    private boolean commaFormat = false;

    public IeeeCDFDirectParser() {
        this.builder = new AclfNetworkBuilder();
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            parseFromReader(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading IEEE CDF file: " + filepath + ": " + e.getMessage());
        }
        return builder.getNetwork();
    }

    public AclfNetwork parseFromReader(BufferedReader reader) throws IOException, InterpssException {
        String line;
        String section = "TITLE";

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;

            if (section.equals("TITLE")) {
                parseTitle(line);
                section = "HEADER";
                continue;
            }

            if (line.toUpperCase().contains("BUS DATA FOLLOWS")) {
                section = "BUS";
                continue;
            }
            if (line.toUpperCase().contains("BRANCH DATA FOLLOWS")) {
                section = "BRANCH";
                continue;
            }
            if (line.toUpperCase().contains("LOSS ZONES FOLLOW")) {
                section = "LOSSZONE";
                continue;
            }
            if (line.toUpperCase().contains("INTERCHANGE DATA FOLLOWS")) {
                section = "INTERCHANGE";
                continue;
            }
            if (line.toUpperCase().contains("TIE LINES FOLLOW")) {
                section = "TIELINE";
                continue;
            }
            if (line.trim().startsWith("-999") || line.trim().startsWith("-99") || line.trim().equals("-9")) {
                section = "BETWEEN";
                continue;
            }
            if (line.toUpperCase().contains("END OF DATA")) {
                break;
            }

            switch (section) {
                case "HEADER":
                    section = "BETWEEN";
                    break;
                case "BUS":
                    parseBusLine(line);
                    break;
                case "BRANCH":
                    parseBranchLine(line);
                    break;
                case "LOSSZONE":
                case "INTERCHANGE":
                case "TIELINE":
                    break;
            }
        }

        postProcess();
        builder.finalizeNetwork();
        return builder.getNetwork();
    }

    // ==================== Title / Header ====================

    private void parseTitle(String line) throws InterpssException {
        commaFormat = line.contains(",");

        if (commaFormat) {
            String[] fields = line.split(",");
            if (fields.length >= 3) {
                baseMva = parseDouble(fields[2]);
                if (baseMva == 0.0) baseMva = 100.0;
            }
        } else {
            if (line.length() >= 37) {
                String mvaStr = safeSubstring(line, 31, 37).trim();
                if (!mvaStr.isEmpty()) {
                    try {
                        baseMva = Double.parseDouble(mvaStr);
                    } catch (NumberFormatException e) {
                        baseMva = 100.0;
                    }
                }
            }
        }
        // Use the same fixed network id as the legacy ODM import path
        builder.setNetworkInfo("Base_Case_from_IEEECDF_format", "IEEE CDF Case",
                baseMva * 1000.0, OriginalDataFormat.IEEECDF);
    }

    // ==================== Bus Data ====================

    private void parseBusLine(String line) throws InterpssException {
        int busNum, areaNum, lossZone, type;
        String name;
        double vm, va, pLoad, qLoad, pGen, qGen, baseKv;
        double desiredV, qMax = 0.0, qMin = 0.0;
        double gShunt = 0.0, bShunt = 0.0;
        int remoteBusNum = 0;

        if (commaFormat) {
            String[] f = line.split(",");
            if (f.length < 12) return;
            busNum = parseInt(f[0]); name = f[1].trim();
            areaNum = parseInt(f[2]); lossZone = parseInt(f[3]);
            type = parseInt(f[4]); vm = parseDouble(f[5]); va = parseDouble(f[6]);
            pLoad = parseDouble(f[7]); qLoad = parseDouble(f[8]);
            pGen = parseDouble(f[9]); qGen = parseDouble(f[10]); baseKv = parseDouble(f[11]);
            desiredV = f.length > 12 ? parseDouble(f[12]) : vm;
            qMax = f.length > 13 ? parseDouble(f[13]) : 0.0;
            qMin = f.length > 14 ? parseDouble(f[14]) : 0.0;
            gShunt = f.length > 15 ? parseDouble(f[15]) : 0.0;
            bShunt = f.length > 16 ? parseDouble(f[16]) : 0.0;
            remoteBusNum = f.length > 17 ? parseInt(f[17]) : 0;
        } else {
            if (line.length() < 83) return;
            busNum = parseInt(safeSubstring(line, 0, 4));
            name = safeSubstring(line, 5, 17).trim();
            areaNum = parseInt(safeSubstring(line, 18, 20));
            lossZone = parseInt(safeSubstring(line, 20, 23));
            type = parseInt(safeSubstring(line, 24, 26));
            vm = parseDouble(safeSubstring(line, 27, 33));
            va = parseDouble(safeSubstring(line, 33, 40));
            pLoad = parseDouble(safeSubstring(line, 40, 49));
            qLoad = parseDouble(safeSubstring(line, 49, 59));
            pGen = parseDouble(safeSubstring(line, 59, 67));
            qGen = parseDouble(safeSubstring(line, 67, 75));
            baseKv = parseDouble(safeSubstring(line, 76, 83));
            desiredV = vm;
            if (line.length() >= 90) desiredV = parseDouble(safeSubstring(line, 84, 90));
            if (line.length() >= 98) qMax = parseDouble(safeSubstring(line, 90, 98));
            if (line.length() >= 106) qMin = parseDouble(safeSubstring(line, 98, 106));
            if (line.length() >= 114) gShunt = parseDouble(safeSubstring(line, 106, 114));
            if (line.length() >= 122) bShunt = parseDouble(safeSubstring(line, 114, 122));
            if (line.length() >= 127) remoteBusNum = parseInt(safeSubstring(line, 123, 127));
        }

        if (desiredV == 0.0) desiredV = vm;
        if (vm == 0.0) vm = 1.0;
        if (baseKv == 0.0) baseKv = 1.0;

        String busId = BUS_ID_PREFIX + busNum;
        String areaId = areaNum > 0 ? String.valueOf(areaNum) : null;
        String zoneId = lossZone > 0 ? String.valueOf(lossZone) : null;

        if (areaId != null) builder.addArea(areaId, "Area " + areaNum, null);
        if (zoneId != null) builder.addZone(zoneId, "Zone " + lossZone, null);

        BaseAclfBus bus = builder.addBus(busId, name, busNum, baseKv * 1000.0,
                vm, Math.toRadians(va), areaId, zoneId, null);

        // Set bus type
        if (type == 3) {
            builder.setSwingBus(busId, desiredV, Math.toRadians(va));
        } else if (type == 2) {
            String remoteBusId = (remoteBusNum > 0 && remoteBusNum != busNum) ? BUS_ID_PREFIX + remoteBusNum : null;
            builder.setPVBus(busId, pGen / baseMva, desiredV, qMax / baseMva, qMin / baseMva,
                    remoteBusId == null);
        } else {
            if (pGen != 0.0 || qGen != 0.0) {
                bus.setGenCode(AclfGenCode.GEN_PQ);
            } else {
                bus.setGenCode(AclfGenCode.NON_GEN);
            }
        }

        // Add generator contribution
        if (pGen != 0.0 || qGen != 0.0) {
            String genId = busId + "-G1";
            var gen = builder.addContributeGen(busId, genId, true,
                    pGen / baseMva, qGen / baseMva, baseMva, desiredV,
                    qMax / baseMva, qMin / baseMva, 0.0, 0.0,
                    null, null, 1.0, null, 1.0, 1.0);
            if (gen != null) gen.setName(genId);
            bus.setGenP(pGen / baseMva);
        }

        // Add load contribution
        if (pLoad != 0.0 || qLoad != 0.0) {
            String loadId = busId + "-L1";
            Complex constP = new Complex(pLoad / baseMva, qLoad / baseMva);
            var load = builder.addContributeLoad(busId, loadId, true, constP, null, null, null, false);
            if (load != null) load.setName(loadId);
        }

        // Add fixed shunt. The CDF shunt G/B fields are already in per unit
        // on the system base, so no baseMva conversion is needed.
        if (gShunt != 0.0 || bShunt != 0.0) {
            builder.addToBusShuntY(busId, new Complex(gShunt, bShunt));
        }
    }

    // ==================== Branch Data ====================

    private void parseBranchLine(String line) throws InterpssException {
        int tapBusNum, zBusNum, brType;
        String circuitStr;
        double r, x, b, rating1, rating2, rating3, tapRatio, shiftAngle;

        if (commaFormat) {
            String[] f = line.split(",");
            if (f.length < 9) return;
            tapBusNum = parseInt(f[0]); zBusNum = parseInt(f[1]);
            // f[2]=area, f[3]=lossZone
            circuitStr = f[4].trim();
            if (circuitStr.isEmpty()) circuitStr = "1";
            brType = parseInt(f[5]);
            r = parseDouble(f[6]); x = parseDouble(f[7]); b = parseDouble(f[8]);
            rating1 = f.length > 9 ? parseDouble(f[9]) : 0.0;
            rating2 = f.length > 10 ? parseDouble(f[10]) : 0.0;
            rating3 = f.length > 11 ? parseDouble(f[11]) : 0.0;
            tapRatio = f.length > 14 ? parseDouble(f[14]) : 0.0;
            if (tapRatio == 0.0) tapRatio = 1.0;
            shiftAngle = f.length > 15 ? parseDouble(f[15]) : 0.0;
        } else {
            if (line.length() < 56) return;
            tapBusNum = parseInt(safeSubstring(line, 0, 4));
            zBusNum = parseInt(safeSubstring(line, 5, 9));
            circuitStr = safeSubstring(line, 16, 17).trim();
            if (circuitStr.isEmpty()) circuitStr = "1";
            brType = parseInt(safeSubstring(line, 18, 19));
            r = parseDouble(safeSubstring(line, 19, 29));
            x = parseDouble(safeSubstring(line, 29, 40));
            b = parseDouble(safeSubstring(line, 40, 50));
            rating1 = parseDouble(safeSubstring(line, 50, 55));
            rating2 = parseDouble(safeSubstring(line, 55, 61));
            rating3 = parseDouble(safeSubstring(line, 61, 67));
            tapRatio = 0.0;
            shiftAngle = 0.0;
            if (line.length() >= 82) tapRatio = parseDouble(safeSubstring(line, 76, 82));
            if (tapRatio == 0.0) tapRatio = 1.0;
            if (line.length() >= 90) shiftAngle = parseDouble(safeSubstring(line, 83, 90));
        }

        String fromBusId = BUS_ID_PREFIX + tapBusNum;
        String toBusId = BUS_ID_PREFIX + zBusNum;

        if (brType == 0) {
            builder.addLine(fromBusId, toBusId, circuitStr,
                    new Complex(r, x), new Complex(0.0, b * 0.5),
                    null, null, rating1, rating2, rating3, true);
        } else if (shiftAngle != 0.0) {
            builder.addPsXformer(fromBusId, toBusId, circuitStr,
                    new Complex(r, x), tapRatio, 1.0, shiftAngle, 0.0,
                    null, null, rating1, rating2, rating3, 0, true);
        } else {
            builder.addXformer2W(fromBusId, toBusId, circuitStr,
                    new Complex(r, x), tapRatio, 1.0,
                    null, null, rating1, rating2, rating3, 0, true);
        }
    }

    // ==================== Post-processing ====================

    private void postProcess() {
        AclfNetwork net = builder.getNetwork();
        net.getBusList().forEach(bus -> {
            // Remove empty contribution gen/load as IeeeCDFFormat does
            if (bus.getContributeGenList().size() > 0) {
                var gen = bus.getContributeGenList().get(0);
                if (bus.getGenCode() == AclfGenCode.GEN_PQ) {
                    if (gen.getGen().abs() == 0.0) {
                        bus.getContributeGenList().remove(0);
                    }
                }
            }
            if (bus.getContributeLoadList().size() > 0) {
                var load = bus.getContributeLoadList().get(0);
                if (load.getLoadCP() != null && load.getLoadCP().abs() == 0.0) {
                    bus.getContributeLoadList().remove(0);
                }
            }
        });
    }

    // ==================== Utility Methods ====================

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
