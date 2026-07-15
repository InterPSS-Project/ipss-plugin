/*
 * @(#)MatpowerDirectParser.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.matpower;

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
 * Direct MATPOWER .m file parser that bypasses the ODM XML layer.
 * Reads MATPOWER case files and populates AclfNetwork via AclfNetworkBuilder.
 *
 * MATPOWER format uses MATLAB struct-based data with:
 * - mpc.baseMVA
 * - mpc.bus matrix (bus_i, type, Pd, Qd, Gs, Bs, area, Vm, Va, baseKV, zone, Vmax, Vmin)
 * - mpc.gen matrix (bus, Pg, Qg, Qmax, Qmin, Vg, mBase, status, Pmax, Pmin, ...)
 * - mpc.branch matrix (fbus, tbus, r, x, b, rateA, rateB, rateC, ratio, angle, status, ...)
 */
public class MatpowerDirectParser {
    private static final Logger log = LoggerFactory.getLogger(MatpowerDirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus";

    private final AclfNetworkBuilder builder;
    private double baseMva = 100.0;

    public MatpowerDirectParser() {
        this.builder = new AclfNetworkBuilder();
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            parseFromReader(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading MATPOWER file: " + filepath + ": " + e.getMessage());
        }
        return builder.getNetwork();
    }

    public AclfNetwork parseFromReader(BufferedReader reader) throws IOException, InterpssException {
        List<double[]> busData = new ArrayList<>();
        List<double[]> genData = new ArrayList<>();
        List<double[]> branchData = new ArrayList<>();
        List<double[]> gencostData = new ArrayList<>();

        String line;
        String currentSection = null;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("%") || trimmed.startsWith("//")) continue;

            // Detect baseMVA
            if (trimmed.contains("baseMVA") && trimmed.contains("=")) {
                String val = trimmed.replaceAll(".*=\\s*", "").replace(";", "").trim();
                try { baseMva = Double.parseDouble(val); }
                catch (NumberFormatException e) { /* use default */ }
                continue;
            }

            // Detect section start
            if (trimmed.contains("mpc.bus") && trimmed.contains("=") && !trimmed.contains("mpc.bus_name")) {
                currentSection = "bus";
                continue;
            }
            if (trimmed.contains("mpc.gen") && trimmed.contains("=") && !trimmed.contains("mpc.gencost")) {
                currentSection = "gen";
                continue;
            }
            if (trimmed.contains("mpc.branch") && trimmed.contains("=")) {
                currentSection = "branch";
                continue;
            }
            if (trimmed.contains("mpc.gencost") && trimmed.contains("=")) {
                currentSection = "gencost";
                continue;
            }

            // Detect section end
            if (trimmed.contains("];")) {
                currentSection = null;
                continue;
            }

            if (currentSection != null) {
                double[] values = parseDataLine(trimmed);
                if (values != null && values.length > 0) {
                    switch (currentSection) {
                        case "bus": busData.add(values); break;
                        case "gen": genData.add(values); break;
                        case "branch": branchData.add(values); break;
                        case "gencost": gencostData.add(values); break;
                    }
                }
            }
        }

        builder.setNetworkInfo("MATPOWER_Case", "MATPOWER Case", baseMva * 1000.0, OriginalDataFormat.IPSS_API);

        processBusData(busData);
        processGenData(genData);
        processBranchData(branchData);

        builder.finalizeNetwork();
        return builder.getNetwork();
    }

    // ==================== Data Parsing ====================

    private double[] parseDataLine(String line) {
        line = line.replace(";", "").replace("[", "").replace("]", "").trim();
        if (line.isEmpty()) return null;

        // Remove trailing comments
        int commentIdx = line.indexOf('%');
        if (commentIdx >= 0) line = line.substring(0, commentIdx).trim();
        commentIdx = line.indexOf("//");
        if (commentIdx >= 0) line = line.substring(0, commentIdx).trim();
        if (line.isEmpty()) return null;

        String[] tokens = line.split("[\\s,;]+");
        List<Double> values = new ArrayList<>();
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;
            try {
                values.add(Double.parseDouble(token));
            } catch (NumberFormatException e) {
                // skip non-numeric tokens
            }
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // ==================== Bus Processing ====================
    // MATPOWER bus format: bus_i(0) type(1) Pd(2) Qd(3) Gs(4) Bs(5) area(6) Vm(7) Va(8) baseKV(9) zone(10) Vmax(11) Vmin(12)

    private void processBusData(List<double[]> busData) throws InterpssException {
        for (double[] d : busData) {
            if (d.length < 10) continue;

            int busNum = (int) d[0];
            int type = (int) d[1];
            double pd = d[2];
            double qd = d[3];
            double gs = d[4];
            double bs = d[5];
            int area = (int) d[6];
            double vm = d[7];
            double va = d[8];
            double baseKv = d[9];
            int zone = d.length > 10 ? (int) d[10] : 0;
            double vmax = d.length > 11 ? d[11] : 1.1;
            double vmin = d.length > 12 ? d[12] : 0.9;

            String busId = BUS_ID_PREFIX + busNum;
            String areaId = area > 0 ? String.valueOf(area) : null;
            String zoneId = zone > 0 ? String.valueOf(zone) : null;

            if (areaId != null) builder.addArea(areaId, "Area " + area, null);
            if (zoneId != null) builder.addZone(zoneId, "Zone " + zone, null);

            BaseAclfBus bus = builder.addBus(busId, "Bus" + busNum, busNum, baseKv * 1000.0,
                    vm, Math.toRadians(va), areaId, zoneId, null);

            builder.setBusVoltageLimit(busId, vmax, vmin);

            // type: 1=PQ, 2=PV, 3=Ref/Swing, 4=isolated
            if (type == 4) {
                bus.setStatus(false);
            } else if (type == 3) {
                builder.setSwingBus(busId, vm, Math.toRadians(va));
            } else if (type == 2) {
                bus.setGenCode(AclfGenCode.GEN_PV);
            } else {
                bus.setGenCode(AclfGenCode.NON_GEN);
            }

            // Add load
            if (pd != 0.0 || qd != 0.0) {
                Complex constP = new Complex(pd / baseMva, qd / baseMva);
                builder.addContributeLoad(busId, "1", true, constP, null, null, null, false);
            }

            // Add shunt
            if (gs != 0.0 || bs != 0.0) {
                builder.addToBusShuntY(busId, new Complex(gs / baseMva, bs / baseMva));
            }
        }
    }

    // ==================== Generator Processing ====================
    // MATPOWER gen format: bus(0) Pg(1) Qg(2) Qmax(3) Qmin(4) Vg(5) mBase(6) status(7) Pmax(8) Pmin(9) ...

    private void processGenData(List<double[]> genData) throws InterpssException {
        int[] genCount = new int[100000];  // track gen count per bus

        for (double[] d : genData) {
            if (d.length < 10) continue;

            int busNum = (int) d[0];
            double pg = d[1];
            double qg = d[2];
            double qmax = d[3];
            double qmin = d[4];
            double vg = d[5];
            double mbase = d[6];
            int status = (int) d[7];
            double pmax = d[8];
            double pmin = d[9];

            if (mbase == 0.0) mbase = baseMva;

            String busId = BUS_ID_PREFIX + busNum;
            BaseAclfBus bus = (BaseAclfBus) builder.getNetwork().getBus(busId);
            if (bus == null) continue;

            genCount[busNum]++;
            String genId = String.valueOf(genCount[busNum]);

            builder.addContributeGen(busId, genId, status == 1,
                    pg / baseMva, qg / baseMva, mbase, vg,
                    qmax / baseMva, qmin / baseMva, pmax / baseMva, pmin / baseMva,
                    null, null, 1.0, null, 1.0, 1.0);

            if (status == 1 && bus.getGenCode() != AclfGenCode.NON_GEN) {
                if (bus.getGenCode() == AclfGenCode.SWING) {
                    builder.setSwingBus(busId, vg, bus.getVoltageAng());
                    bus.setGenP(pg / baseMva);
                } else if (bus.getGenCode() == AclfGenCode.GEN_PV) {
                    builder.setPVBus(busId, pg / baseMva, vg, qmax / baseMva, qmin / baseMva, true);
                } else {
                    builder.setPQBus(busId, pg / baseMva, qg / baseMva, 0.0, 0.0);
                }
            }
        }
    }

    // ==================== Branch Processing ====================
    // MATPOWER branch format: fbus(0) tbus(1) r(2) x(3) b(4) rateA(5) rateB(6) rateC(7) ratio(8) angle(9) status(10) ...

    private void processBranchData(List<double[]> branchData) throws InterpssException {
        int[][] branchCount = new int[100000][100000 > 1000 ? 1 : 1]; // too large, use map instead
        java.util.Map<String, Integer> circuitMap = new java.util.HashMap<>();

        for (double[] d : branchData) {
            if (d.length < 11) continue;

            int fromNum = (int) d[0];
            int toNum = (int) d[1];
            double r = d[2];
            double x = d[3];
            double b = d[4];
            double rateA = d[5];
            double rateB = d[6];
            double rateC = d[7];
            double ratio = d[8];
            double angle = d[9];
            int status = (int) d[10];

            String fromBusId = BUS_ID_PREFIX + fromNum;
            String toBusId = BUS_ID_PREFIX + toNum;

            String brKey = fromNum + "_" + toNum;
            int cir = circuitMap.getOrDefault(brKey, 0) + 1;
            circuitMap.put(brKey, cir);
            String cirId = String.valueOf(cir);

            boolean isXfr = (ratio != 0.0 && ratio != 1.0) || angle != 0.0;

            if (!isXfr) {
                builder.addLine(fromBusId, toBusId, cirId,
                        new Complex(r, x), new Complex(0.0, b * 0.5),
                        null, null, rateA, rateB, rateC, status == 1);
            } else if (angle != 0.0) {
                if (ratio == 0.0) ratio = 1.0;
                builder.addPsXformer(fromBusId, toBusId, cirId,
                        new Complex(r, x), ratio, 1.0, angle, 0.0,
                        null, null, rateA, rateB, rateC, 0, status == 1);
            } else {
                builder.addXformer2W(fromBusId, toBusId, cirId,
                        new Complex(r, x), ratio, 1.0,
                        null, null, rateA, rateB, rateC, 0, status == 1);
            }
        }
    }
}
