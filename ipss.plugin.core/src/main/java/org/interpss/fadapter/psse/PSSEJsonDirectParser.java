/*
 * @(#)PSSEJsonDirectParser.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.psse;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder.ShuntBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.net.OriginalDataFormat;

/**
 * Direct PSS/E JSON (RAWX) file parser that bypasses the ODM XML intermediate layer.
 * Reads PSS/E RAWX files and populates AclfNetwork via AclfNetworkBuilder.
 *
 * The RAWX format is a JSON structure where each data type has a "fields" array
 * defining column names and a "data" array with rows of values.
 */
public class PSSEJsonDirectParser {
    private static final Logger log = LoggerFactory.getLogger(PSSEJsonDirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus";

    private final AclfNetworkBuilder builder;
    private double baseMva = 100.0;

    public PSSEJsonDirectParser() {
        this.builder = new AclfNetworkBuilder();
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        try (FileReader reader = new FileReader(filepath)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            parseJsonObject(root);
        } catch (IOException e) {
            throw new InterpssException("Error reading PSS/E JSON file: " + filepath + ": " + e.getMessage());
        }
        return builder.getNetwork();
    }

    private void parseJsonObject(JsonObject root) throws InterpssException {
        JsonObject network = root.has("network") ? root.getAsJsonObject("network") : root;

        // Parse case ID / system data
        parseCaseData(network);

        // Parse buses
        parseFieldData(network, "bus", this::parseBusRow);

        // Parse loads
        parseFieldData(network, "load", this::parseLoadRow);

        // Parse fixed shunts
        parseFieldData(network, "fixshunt", this::parseFixedShuntRow);

        // Parse switched shunts (v35+ RAWX: swshunt)
        parseFieldData(network, "swshunt", this::parseSwitchedShuntRow);

        // Parse generators
        parseFieldData(network, "generator", this::parseGenRow);

        // Parse AC lines
        parseFieldData(network, "acline", this::parseAcLineRow);

        // Parse transformers
        parseFieldData(network, "transformer", this::parseXfrRow);

        // Parse areas
        parseFieldData(network, "area", this::parseAreaRow);

        // Parse zones
        parseFieldData(network, "zone", this::parseZoneRow);

        // Parse owners
        parseFieldData(network, "owner", this::parseOwnerRow);

        // Node-breaker overlay (flat RAWX tables sub / subnode / subswd / subterm)
        new PSSESubstationImporter(builder).parseRawx(network);

        builder.finalizeNetwork();
    }

    // ==================== Parsing Framework ====================

    @FunctionalInterface
    private interface RowProcessor {
        void process(Map<String, JsonElement> row) throws InterpssException;
    }

    private void parseFieldData(JsonObject network, String sectionName, RowProcessor processor) throws InterpssException {
        if (!network.has(sectionName)) return;
        JsonObject section = network.getAsJsonObject(sectionName);
        if (!section.has("fields") || !section.has("data")) return;

        JsonArray fields = section.getAsJsonArray("fields");
        JsonArray data = section.getAsJsonArray("data");

        String[] fieldNames = new String[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            fieldNames[i] = fields.get(i).getAsString().toLowerCase();
        }

        for (JsonElement rowElem : data) {
            JsonArray row = rowElem.getAsJsonArray();
            Map<String, JsonElement> rowMap = new HashMap<>();
            for (int i = 0; i < Math.min(fieldNames.length, row.size()); i++) {
                rowMap.put(fieldNames[i], row.get(i));
            }
            if (processor != null) processor.process(rowMap);
        }
    }

    private double getDouble(Map<String, JsonElement> row, String field, double defaultVal) {
        JsonElement e = row.get(field);
        if (e == null || e.isJsonNull()) return defaultVal;
        try { return e.getAsDouble(); }
        catch (Exception ex) { return defaultVal; }
    }

    private int getInt(Map<String, JsonElement> row, String field, int defaultVal) {
        JsonElement e = row.get(field);
        if (e == null || e.isJsonNull()) return defaultVal;
        try { return e.getAsInt(); }
        catch (Exception ex) { return defaultVal; }
    }

    private String getString(Map<String, JsonElement> row, String field, String defaultVal) {
        JsonElement e = row.get(field);
        if (e == null || e.isJsonNull()) return defaultVal;
        return e.getAsString();
    }

    // ==================== Case Data ====================

    private void parseCaseData(JsonObject network) throws InterpssException {
        if (network.has("caseid")) {
            JsonObject caseid = network.getAsJsonObject("caseid");
            if (caseid.has("fields") && caseid.has("data")) {
                JsonArray fields = caseid.getAsJsonArray("fields");
                JsonArray data = caseid.getAsJsonArray("data");
                if (data.size() > 0) {
                    JsonArray row;
                    if (data.get(0).isJsonArray()) {
                        row = data.get(0).getAsJsonArray();
                    } else {
                        row = data;
                    }
                    Map<String, JsonElement> rowMap = new HashMap<>();
                    for (int i = 0; i < Math.min(fields.size(), row.size()); i++) {
                        rowMap.put(fields.get(i).getAsString().toLowerCase(), row.get(i));
                    }
                    baseMva = getDouble(rowMap, "sbase", 100.0);
                }
            }
        }

        builder.setNetworkInfo("Base_Case_from_PSS_E_format", "PSS/E JSON Case",
                baseMva * 1000.0, OriginalDataFormat.PSSE);
    }

    // ==================== Bus ====================

    private void parseBusRow(Map<String, JsonElement> row) throws InterpssException {
        int busNum = getInt(row, "ibus", 0);
        if (busNum == 0) return;
        String busId = BUS_ID_PREFIX + busNum;
        String name = getString(row, "name", "");
        double baseKv = getDouble(row, "baskv", 0.0);
        int ide = getInt(row, "ide", 1);
        int areaNum = getInt(row, "area", 0);
        int zoneNum = getInt(row, "zone", 0);
        int ownerNum = getInt(row, "owner", 0);
        double vm = getDouble(row, "vm", 1.0);
        double va = getDouble(row, "va", 0.0);

        String areaId = areaNum > 0 ? String.valueOf(areaNum) : null;
        String zoneId = zoneNum > 0 ? String.valueOf(zoneNum) : null;
        String ownerId = ownerNum > 0 ? String.valueOf(ownerNum) : null;

        if (areaId != null) builder.addArea(areaId, "Area " + areaNum, null);
        if (zoneId != null) builder.addZone(zoneId, "Zone " + zoneNum, null);
        if (ownerId != null) builder.addOwner(ownerId, "Owner " + ownerNum);

        BaseAclfBus bus = builder.addBus(busId, name, busNum, baseKv * 1000.0,
                vm, Math.toRadians(va), areaId, zoneId, ownerId);

        if (ide == 4) bus.setStatus(false);
        if (ide == 3) {
            builder.setSwingBus(busId, vm, Math.toRadians(va));
        } else if (ide == 2) {
            bus.setGenCode(AclfGenCode.GEN_PV);
        } else {
            bus.setGenCode(AclfGenCode.NON_GEN);
        }
    }

    // ==================== Load ====================

    private void parseLoadRow(Map<String, JsonElement> row) throws InterpssException {
        int busNum = getInt(row, "ibus", 0);
        String busId = BUS_ID_PREFIX + busNum;
        String loadId = getString(row, "loadid", "1").trim();
        int status = getInt(row, "stat", 1);

        double pl = getDouble(row, "pl", 0.0);
        double ql = getDouble(row, "ql", 0.0);
        double ip = getDouble(row, "ip", 0.0);
        double iq = getDouble(row, "iq", 0.0);
        double yp = getDouble(row, "yp", 0.0);
        double yq = getDouble(row, "yq", 0.0);

        Complex constP = (pl != 0.0 || ql != 0.0) ? new Complex(pl / baseMva, ql / baseMva) : null;
        Complex constI = (ip != 0.0 || iq != 0.0) ? new Complex(ip / baseMva, iq / baseMva) : null;
        Complex constZ = (yp != 0.0 || yq != 0.0) ? new Complex(yp / baseMva, -yq / baseMva) : null;

        builder.addContributeLoad(busId, loadId, status == 1, constP, constI, constZ, null, false);
    }

    // ==================== Fixed Shunt ====================

    private void parseFixedShuntRow(Map<String, JsonElement> row) throws InterpssException {
        int busNum = getInt(row, "ibus", 0);
        String busId = BUS_ID_PREFIX + busNum;
        String id = getString(row, "shntid", "1").trim();
        if (id.isEmpty()) {
            id = "1";
        }
        int status = getInt(row, "stat", 1);
        double gl = getDouble(row, "gl", 0.0);
        double bl = getDouble(row, "bl", 0.0);
        String name = getString(row, "name", "").trim();

        builder.addFixedShunt(busId, id, status == 1, gl / baseMva, bl / baseMva, name);
    }

    // ==================== Switched Shunt ====================

    /**
     * RAWX {@code swshunt} table (PSS/E v35+ field names). Blocks are {@code sN,nN,bN}
     * for N=1..8 when present.
     */
    private void parseSwitchedShuntRow(Map<String, JsonElement> row) throws InterpssException {
        int busNum = getInt(row, "ibus", 0);
        if (busNum == 0) {
            return;
        }
        String busId = BUS_ID_PREFIX + busNum;
        String shuntId = getString(row, "shntid", "1").trim();
        if (shuntId.isEmpty()) {
            shuntId = "1";
        }
        int modsw = getInt(row, "modsw", 1);
        int stat = getInt(row, "stat", 1);
        double vswhi = getDouble(row, "vswhi", 1.0);
        double vswlo = getDouble(row, "vswlo", 1.0);
        int swreg = getInt(row, "swreg", 0);
        double binit = getDouble(row, "binit", 0.0);

        AclfAdjustControlMode mode;
        if (modsw == 2 || modsw == 4 || modsw == 6) {
            mode = AclfAdjustControlMode.DISCRETE;
        } else if (modsw == 1 || modsw == 3 || modsw == 5) {
            mode = AclfAdjustControlMode.CONTINUOUS;
        } else {
            mode = AclfAdjustControlMode.FIXED;
        }

        String remoteBusId = (swreg > 0 && swreg != busNum) ? BUS_ID_PREFIX + swreg : null;

        List<ShuntBlock> blocks = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            int s = getInt(row, "s" + i, 0);
            int n = getInt(row, "n" + i, 0);
            double bVal = getDouble(row, "b" + i, 0.0);
            if (n > 0 || bVal != 0.0) {
                blocks.add(new ShuntBlock(n, bVal, s == 1));
            }
        }

        builder.addSwitchedShunt(busId, shuntId, stat == 1,
                mode, AclfAdjustControlType.RANGE_CONTROL,
                binit / baseMva, vswhi, vswlo, remoteBusId, blocks);
    }

    // ==================== Generator ====================

    private void parseGenRow(Map<String, JsonElement> row) throws InterpssException {
        int busNum = getInt(row, "ibus", 0);
        String busId = BUS_ID_PREFIX + busNum;
        String genId = getString(row, "machid", "1").trim();

        double pg = getDouble(row, "pg", 0.0);
        double qg = getDouble(row, "qg", 0.0);
        double qt = getDouble(row, "qt", 0.0);
        double qb = getDouble(row, "qb", 0.0);
        double vs = getDouble(row, "vs", 1.0);
        int ireg = getInt(row, "ireg", 0);
        double mbase = getDouble(row, "mbase", baseMva);
        double zr = getDouble(row, "zr", 0.0);
        double zx = getDouble(row, "zx", 0.0);
        double rt = getDouble(row, "rt", 0.0);
        double xt = getDouble(row, "xt", 0.0);
        double gtap = getDouble(row, "gtap", 1.0);
        int stat = getInt(row, "stat", 1);
        double rmpct = getDouble(row, "rmpct", 100.0);
        double pt = getDouble(row, "pt", 0.0);
        double pb = getDouble(row, "pb", 0.0);

        if (mbase == 0.0) mbase = baseMva;

        BaseAclfBus bus = (BaseAclfBus) builder.getNetwork().getBus(busId);
        if (bus == null) return;

        boolean genStatus = (stat == 1);
        if (bus.getGenCode() == AclfGenCode.NON_GEN) genStatus = false;

        String remoteBusId = (ireg > 0 && ireg != busNum) ? BUS_ID_PREFIX + ireg : null;
        Complex sourceZ = (zr != 0.0 || zx != 0.0) ? new Complex(zr, zx) : null;
        Complex xfrZ = (rt != 0.0 || xt != 0.0) ? new Complex(rt, xt) : null;

        builder.addContributeGen(busId, genId, genStatus,
                pg / baseMva, qg / baseMva, mbase, vs,
                qt / baseMva, qb / baseMva, pt / baseMva, pb / baseMva,
                sourceZ, xfrZ, gtap, remoteBusId, rmpct * 0.01, 1.0);

        if (bus.getGenCode() == AclfGenCode.SWING) {
            builder.setSwingBus(busId, vs, bus.getVoltageAng());
            bus.setGenP(pg / baseMva);
        } else if (bus.getGenCode() == AclfGenCode.GEN_PV && genStatus) {
            if (qt == qb) {
                builder.setPQBus(busId, pg / baseMva, qg / baseMva, 0.0, 0.0);
            } else if (remoteBusId == null || remoteBusId.equals(busId)) {
                builder.setPVBus(busId, pg / baseMva, vs, qt / baseMva, qb / baseMva, true);
            } else {
                builder.setPQBus(busId, pg / baseMva, qg / baseMva, 0.0, 0.0);
            }
        }
    }

    // ==================== AC Line ====================

    private void parseAcLineRow(Map<String, JsonElement> row) throws InterpssException {
        int fromNum = getInt(row, "ibus", 0);
        int toNum = getInt(row, "jbus", 0);
        String ckt = getString(row, "ckt", "1").trim();

        double r = getDouble(row, "rpu", 0.0);
        double x = getDouble(row, "xpu", 0.0);
        double b = getDouble(row, "bpu", 0.0);
        double rate1 = getDouble(row, "rate1", 0.0);
        double rate2 = getDouble(row, "rate2", 0.0);
        double rate3 = getDouble(row, "rate3", 0.0);
        double gi = getDouble(row, "gi", 0.0);
        double bi = getDouble(row, "bi", 0.0);
        double gj = getDouble(row, "gj", 0.0);
        double bj = getDouble(row, "bj", 0.0);
        int stat = getInt(row, "stat", 1);

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        Complex fromShuntY = (gi != 0.0 || bi != 0.0) ? new Complex(gi, bi) : null;
        Complex toShuntY = (gj != 0.0 || bj != 0.0) ? new Complex(gj, bj) : null;

        builder.addLine(fromBusId, toBusId, ckt,
                new Complex(r, x), new Complex(0.0, b * 0.5),
                fromShuntY, toShuntY, rate1, rate2, rate3, stat == 1);
    }

    // ==================== Transformer ====================

    private void parseXfrRow(Map<String, JsonElement> row) throws InterpssException {
        // Flat RAWX transformer row: same field names as RAW (kbus != 0 ⇒ 3W)

        int fromNum = getInt(row, "ibus", 0);
        int toNum = getInt(row, "jbus", 0);
        int tertNum = getInt(row, "kbus", 0);
        String ckt = getString(row, "ckt", "1").trim();
        int stat = getInt(row, "stat", 1);

        double r12 = getDouble(row, "r1_2", 0.0);
        double x12 = getDouble(row, "x1_2", 0.0);
        double sbase12 = getDouble(row, "sbase1_2", baseMva);
        double windv1 = getDouble(row, "windv1", 1.0);
        double windv2 = getDouble(row, "windv2", 1.0);
        double ang1 = getDouble(row, "ang1", 0.0);
        double rate1 = getDouble(row, "wdg1rate1", 0.0);
        double rate2 = getDouble(row, "wdg1rate2", 0.0);
        double rate3 = getDouble(row, "wdg1rate3", 0.0);
        double mag1 = getDouble(row, "mag1", 0.0);
        double mag2 = getDouble(row, "mag2", 0.0);
        int cw = getInt(row, "cw", 1);
        int cz = getInt(row, "cz", 1);
        int cm = getInt(row, "cm", 1);
        int tab1 = getInt(row, "tab1", 0);

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        BaseAclfBus fromBus = (BaseAclfBus) builder.getNetwork().getBus(fromBusId);
        BaseAclfBus toBus = (BaseAclfBus) builder.getNetwork().getBus(toBusId);
        if (fromBus == null || toBus == null) return;

        if (tertNum != 0) {
            parse3WXfrRow(row, fromBusId, toBusId, BUS_ID_PREFIX + tertNum, ckt,
                    stat, cw, cz, cm, sbase12, mag1, mag2);
            return;
        }

        double zRatio = 1.0;
        if (cz == 2 && sbase12 > 0 && sbase12 != baseMva) {
            zRatio = baseMva / sbase12;
        }

        double fromTap = windv1;
        double toTap = windv2;
        if (cw == 2) {
            fromTap = windv1 * 1000.0 / fromBus.getBaseVoltage();
            toTap = windv2 * 1000.0 / toBus.getBaseVoltage();
        }

        double zr = r12 * zRatio * toTap * toTap;
        double zx = x12 * zRatio * toTap * toTap;
        double effFromTap = fromTap / toTap;

        Complex magY = (mag1 != 0.0 || mag2 != 0.0) ? new Complex(mag1, mag2) : null;

        if (ang1 != 0.0) {
            builder.addPsXformer(fromBusId, toBusId, ckt,
                    new Complex(zr, zx), effFromTap, 1.0, ang1, 0.0,
                    magY, null, rate1, rate2, rate3, tab1, stat == 1);
        } else {
            builder.addXformer2W(fromBusId, toBusId, ckt,
                    new Complex(zr, zx), effFromTap, 1.0,
                    magY, null, rate1, rate2, rate3, tab1, stat == 1);
        }
    }

    private void parse3WXfrRow(Map<String, JsonElement> row,
            String fromBusId, String toBusId, String tertBusId, String ckt,
            int stat, int cw, int cz, int cm, double sbase12,
            double mag1, double mag2) throws InterpssException {
        BaseAclfBus tertBus = (BaseAclfBus) builder.getNetwork().getBus(tertBusId);
        if (tertBus == null) {
            return;
        }

        double r12 = getDouble(row, "r1_2", 0.0);
        double x12 = getDouble(row, "x1_2", 0.0);
        sbase12 = getDouble(row, "sbase1_2", sbase12);
        double r23 = getDouble(row, "r2_3", 0.0);
        double x23 = getDouble(row, "x2_3", 0.0);
        double sbase23 = getDouble(row, "sbase2_3", baseMva);
        double r31 = getDouble(row, "r3_1", 0.0);
        double x31 = getDouble(row, "x3_1", 0.0);
        double sbase31 = getDouble(row, "sbase3_1", baseMva);
        double starVMag = getDouble(row, "vmstar", 1.0);
        double starVAng = getDouble(row, "anstar", 0.0);

        double windv1 = getDouble(row, "windv1", 1.0);
        double nomv1 = getDouble(row, "nomv1", 0.0);
        double ang1 = getDouble(row, "ang1", 0.0);
        double windv2 = getDouble(row, "windv2", 1.0);
        double nomv2 = getDouble(row, "nomv2", 0.0);
        double ang2 = getDouble(row, "ang2", 0.0);
        double windv3 = getDouble(row, "windv3", 1.0);
        double nomv3 = getDouble(row, "nomv3", 0.0);
        double ang3 = getDouble(row, "ang3", 0.0);
        int tab1 = getInt(row, "tab1", 0);
        int tab2 = getInt(row, "tab2", 0);
        int tab3 = getInt(row, "tab3", 0);

        BaseAclfBus fromBus = (BaseAclfBus) builder.getNetwork().getBus(fromBusId);
        BaseAclfBus toBus = (BaseAclfBus) builder.getNetwork().getBus(toBusId);
        double fromBaseV = fromBus != null ? fromBus.getBaseVoltage() : 1000.0;
        double toBaseV = toBus != null ? toBus.getBaseVoltage() : 1000.0;
        double tertBaseV = tertBus.getBaseVoltage();

        if (nomv1 == 0.0) nomv1 = fromBaseV / 1000.0;
        if (nomv2 == 0.0) nomv2 = toBaseV / 1000.0;
        if (nomv3 == 0.0) nomv3 = tertBaseV / 1000.0;

        Complex z12PU = convertZ(cz, r12, x12, sbase12);
        Complex z23PU = convertZ(cz, r23, x23, sbase23);
        Complex z31PU = convertZ(cz, r31, x31, sbase31);

        double fromTap = convertTap(cw, windv1, nomv1, fromBaseV);
        double toTap = convertTap(cw, windv2, nomv2, toBaseV);
        double tertTap = convertTap(cw, windv3, nomv3, tertBaseV);

        Complex magY = convertMagY(cm, mag1, mag2, nomv1, sbase12, fromBaseV);

        boolean isPhaseShifting = (ang1 != 0.0 || ang2 != 0.0 || ang3 != 0.0);
        // PSS/E 3W STAT: 0=out, 1=in, 2=winding2 out, 3=winding3 out, 4=winding1 out
        boolean inService = stat != 0;
        boolean wind1OffLine = (stat == 4);
        boolean wind2OffLine = (stat == 2);
        boolean wind3OffLine = (stat == 3);

        Aclf3WBranch branch3W = builder.addXformer3W(fromBusId, toBusId, tertBusId, ckt,
                z12PU, z23PU, z31PU,
                fromTap, toTap, tertTap,
                magY, starVMag, starVAng,
                wind1OffLine, wind2OffLine, wind3OffLine,
                isPhaseShifting, ang1, ang2, ang3,
                inService);

        if (branch3W != null) {
            if (tab1 > 0) branch3W.getFromAclfBranch().setXfrZTableNumber(tab1);
            if (tab2 > 0) branch3W.getToAclfBranch().setXfrZTableNumber(tab2);
            if (tab3 > 0) branch3W.getTertAclfBranch().setXfrZTableNumber(tab3);
        }
    }

    private Complex convertZ(int cz, double r, double x, double sbase) {
        if (cz == 2) {
            double ratio = (sbase > 0 && sbase != baseMva) ? baseMva / sbase : 1.0;
            return new Complex(r * ratio, x * ratio);
        } else if (cz == 3) {
            double zpu = x * baseMva / sbase;
            double rpu = r * 1.0E-6 * baseMva / (sbase * sbase);
            double xpu = Math.sqrt(Math.max(zpu * zpu - rpu * rpu, 0.0));
            return new Complex(rpu, xpu);
        }
        return new Complex(r, x);
    }

    private double convertTap(int cw, double windv, double nomvKv, double busBaseV) {
        if (cw == 2) {
            return windv * 1000.0 / busBaseV;
        } else if (cw == 3) {
            return windv * nomvKv * 1000.0 / busBaseV;
        }
        return windv;
    }

    private Complex convertMagY(int cm, double mag1, double mag2,
            double nomv1Kv, double sbase12, double fromBaseV) {
        if (mag1 == 0.0 && mag2 == 0.0) {
            return null;
        }
        if (cm == 1) {
            return new Complex(mag1, mag2);
        }
        double fromBaseKv = fromBaseV / 1000.0;
        double ybase = baseMva / (fromBaseKv * fromBaseKv);
        double g_rv = mag1 / (nomv1Kv * nomv1Kv) * 1.0E-6;
        double g_pu = g_rv / ybase;
        double ybase_w12 = sbase12 / (nomv1Kv * nomv1Kv);
        double b_rv = -mag2 * ybase_w12;
        double b_pu = b_rv / ybase;
        return new Complex(g_pu, b_pu);
    }

    // ==================== Area / Zone / Owner ====================

    private void parseAreaRow(Map<String, JsonElement> row) throws InterpssException {
        int areaNum = getInt(row, "iarea", 0);
        String name = getString(row, "arnam", "Area " + areaNum);
        if (areaNum > 0) builder.addArea(String.valueOf(areaNum), name, null);
    }

    private void parseZoneRow(Map<String, JsonElement> row) throws InterpssException {
        int zoneNum = getInt(row, "izone", 0);
        String name = getString(row, "zonam", "Zone " + zoneNum);
        if (zoneNum > 0) builder.addZone(String.valueOf(zoneNum), name, null);
    }

    private void parseOwnerRow(Map<String, JsonElement> row) throws InterpssException {
        int ownerNum = getInt(row, "iowner", 0);
        String name = getString(row, "ownam", "Owner " + ownerNum);
        if (ownerNum > 0) builder.addOwner(String.valueOf(ownerNum), name);
    }
}
