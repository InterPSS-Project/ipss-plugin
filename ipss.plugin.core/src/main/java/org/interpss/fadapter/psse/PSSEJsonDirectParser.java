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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder.ShuntBlock;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.datatype.XfrZCorrection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.net.NameTag;
import com.interpss.core.net.NetFactory;
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
        try {
            JsonObject root = JsonParser.parseString(readRawxJson(filepath)).getAsJsonObject();
            parseJsonObject(root);
        } catch (IOException e) {
            throw new InterpssException("Error reading PSS/E JSON file: " + filepath + ": " + e.getMessage());
        }
        return builder.getNetwork();
    }

    private String readRawxJson(String filepath) throws IOException {
        String json = Files.readString(Path.of(filepath));
        return json.replaceAll("\\\\U([0-9A-Fa-f]{4})", "\\\\u$1");
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
        PSSEGeneratorReactivePower.finalizeBusTypes(builder);

        // Parse AC lines
        parseFieldData(network, "acline", this::parseAcLineRow);

        // Parse transformers
        parseFieldData(network, "transformer", this::parseXfrRow);

        // System switching devices (ZBR / breaker / disconnect)
        parseFieldData(network, "sysswd", this::parseSysSwdRow);

        // Two-terminal LCC / VSC and multi-terminal DC
        parseFieldData(network, "twotermdc", this::parseTwoTermDcRow);
        parseFieldData(network, "vscdc", this::parseVscDcRow);
        parseMultiTerminalDc(network);

        // Transformer impedance correction tables
        parseImpcor(network);

        // Parse areas
        parseFieldData(network, "area", this::parseAreaRow);

        // Parse zones
        parseFieldData(network, "zone", this::parseZoneRow);

        // Parse owners
        parseFieldData(network, "owner", this::parseOwnerRow);

        // FACTS / SVC
        parseFieldData(network, "facts", this::parseFactsRow);

        // Induction machines (NB name tags only)
        parseFieldData(network, "indmach", this::parseIndMachRow);

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
        // YQ is negative for inductive in PSS/E; negate to match convention
        Complex constZ = (yp != 0.0 || yq != 0.0) ? new Complex(yp / baseMva, -yq / baseMva) : null;

        Complex dgenPower = null;
        boolean dgenStatus = false;
        double dgenp = getDouble(row, "dgenp", 0.0);
        double dgenq = getDouble(row, "dgenq", 0.0);
        int dgenm = getInt(row, "dgenm", 0);
        if (dgenp != 0.0 || dgenq != 0.0) {
            dgenPower = new Complex(dgenp / baseMva, dgenq / baseMva);
            dgenStatus = (dgenm == 1);
        }

        builder.addContributeLoad(busId, loadId, status == 1, constP, constI, constZ, dgenPower, dgenStatus);
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

        AclfAdjustControlMode mode = PSSEDirectParser.switchedShuntControlMode(modsw);

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
        int wmod = getInt(row, "wmod", 0);
        double wpf = getDouble(row, "wpf", 1.0);

        if (mbase == 0.0) mbase = baseMva;

        BaseAclfBus bus = (BaseAclfBus) builder.getNetwork().getBus(busId);
        if (bus == null) return;

        if (bus.getGenCode() != AclfGenCode.SWING) {
            PSSEGeneratorReactivePower.Data reactiveData =
                    PSSEGeneratorReactivePower.resolve(pg, qg, qt, qb, wmod, wpf);
            qg = reactiveData.qGen();
            qt = reactiveData.qMax();
            qb = reactiveData.qMin();
        }

		boolean genStatus = (stat == 1);
        if (!bus.isActive()) genStatus = false;

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
        } else if (bus.getGenCode() == AclfGenCode.GEN_PV
                && genStatus && qt != qb) {
            builder.setPVBus(busId, pg / baseMva, vs,
                    qt / baseMva, qb / baseMva, true);
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

        double nomv1 = getDouble(row, "nomv1", 0.0);
        double nomv2 = getDouble(row, "nomv2", 0.0);
        double fromBaseV = fromBus.getBaseVoltage();
        double toBaseV = toBus.getBaseVoltage();
        if (nomv1 == 0.0) nomv1 = fromBaseV / 1000.0;
        if (nomv2 == 0.0) nomv2 = toBaseV / 1000.0;

        Complex zPU = convertZ(cz, r12, x12, sbase12);
        double fromTap = convertTap(cw, windv1, nomv1, fromBaseV);
        double toTap = convertTap(cw, windv2, nomv2, toBaseV);
        Complex magY = convertMagY(cm, mag1, mag2, nomv1, sbase12, fromBaseV);

        int cod1 = getInt(row, "cod1", 0);
        int cont1 = getInt(row, "cont1", 0);
        double rma1 = getDouble(row, "rma1", 1.1);
        double rmi1 = getDouble(row, "rmi1", 0.9);
        double vma1 = getDouble(row, "vma1", 1.1);
        double vmi1 = getDouble(row, "vmi1", 0.9);
        int ntp1 = getInt(row, "ntp1", 33);

        double tapMax = convertTap(cw, rma1, nomv1, fromBaseV);
        double tapMin = convertTap(cw, rmi1, nomv1, fromBaseV);
        Double tapStepSize = ntp1 > 1 ? (tapMax - tapMin) / (ntp1 - 1) : null;

        boolean isPhaseShifter = ang1 != 0.0 || Math.abs(cod1) == 3;
        AclfBranch branch;
        if (isPhaseShifter) {
            branch = builder.addPsXformer(fromBusId, toBusId, ckt,
                    zPU, fromTap, toTap, ang1, 0.0,
                    magY, null, rate1, rate2, rate3, tab1, stat == 1);
        } else {
            branch = builder.addXformer2W(fromBusId, toBusId, ckt,
                    zPU, fromTap, toTap,
                    magY, null, rate1, rate2, rate3, tab1, stat == 1);
        }

        if (branch == null) {
            return;
        }
        String branchId = branch.getId();
        if (Math.abs(cod1) == 1 && cont1 != 0) {
            String vcBusId = BUS_ID_PREFIX + Math.abs(cont1);
            builder.addTapVoltageRangeControl(branchId, vcBusId, cod1 > 0,
                    vma1, vmi1, tapMax, tapMin,
                    true, cont1 < 0, tapStepSize,
                    ntp1 > 0 ? ntp1 : null);
        } else if (Math.abs(cod1) == 1) {
            builder.addTapVoltageRangeControl(branchId, fromBusId, cod1 > 0,
                    vma1, vmi1, tapMax, tapMin,
                    true, true, tapStepSize, ntp1 > 0 ? ntp1 : null);
        } else if (Math.abs(cod1) == 3) {
            int nonMeteredEnd = getInt(row, "nmet", 2);
            builder.addPsXfrAngleRangeControl(branchId, cod1 > 0,
                    vma1 / baseMva, vmi1 / baseMva,
                    (vma1 + vmi1) / 2.0, UnitType.mW,
                    rma1, rmi1,
                    true, true, nonMeteredEnd == 1);
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

    // ==================== System switching device ====================

    private void parseSysSwdRow(Map<String, JsonElement> row) throws InterpssException {
        int fromNum = Math.abs(getInt(row, "ibus", 0));
        int toNum = Math.abs(getInt(row, "jbus", 0));
        if (fromNum <= 0 || toNum <= 0) {
            return;
        }
        String ckt = getString(row, "ckt", "1").trim();
        if (ckt.isEmpty()) {
            ckt = "1";
        }
        double x = getDouble(row, "xpu", 0.0001);
        int status = getInt(row, "stat", 1);
        int stype = getInt(row, "stype", 2);
        String name = getString(row, "name", "").trim();

        AclfBranch bra = builder.addBreaker(BUS_ID_PREFIX + fromNum, BUS_ID_PREFIX + toNum, ckt,
                new Complex(0.0, x), status == 1, AclfBranchCode.BREAKER);
        if (bra != null && !name.isEmpty()) {
            bra.setName(name);
        }
        if (bra != null) {
            bra.setDesc("SystemSWD:stype=" + stype);
        }
    }

    // ==================== Two-terminal LCC HVDC ====================

    private void parseTwoTermDcRow(Map<String, JsonElement> row) throws InterpssException {
        String name = getString(row, "name", "").trim();
        int mdc = getInt(row, "mdc", 0);
        double rdc = getDouble(row, "rdc", 0.0);
        double setvl = getDouble(row, "setvl", 0.0);
        double vschd = getDouble(row, "vschd", 0.0);
        double rcomp = getDouble(row, "rcomp", 0.0);
        double delti = getDouble(row, "delti", 0.0);
        String meter = getString(row, "met", "I");

        HvdcControlMode controlMode = mdc == 1 ? HvdcControlMode.DC_POWER
                : mdc == 2 ? HvdcControlMode.DC_CURRENT : HvdcControlMode.BLOCKED;
        if (Math.abs(setvl) < 1.0E-3) {
            controlMode = HvdcControlMode.BLOCKED;
        }

        int recBusNum = getInt(row, "ipr", 0);
        int invBusNum = getInt(row, "ipi", 0);
        if (recBusNum <= 0 || invBusNum <= 0) {
            return;
        }

        String fromBusId = BUS_ID_PREFIX + recBusNum;
        String toBusId = BUS_ID_PREFIX + invBusNum;
        String dcLineId = name.isEmpty() ? fromBusId + "_" + toBusId : name;
        boolean controlOnRec = setvl > 0.0;
        boolean meterOnRec = meter.toUpperCase().startsWith("R");

        try {
            HvdcLine2TLCC<AclfBus> lcc = builder.addHvdcLine2TLCC(
                    dcLineId, name, fromBusId, toBusId,
                    mdc != 0, false,
                    controlMode, HvdcOperationMode.REC1_INV1,
                    rdc, setvl, setvl,
                    controlOnRec, vschd, rcomp, delti,
                    meterOnRec ? ConverterType.RECTIFIER : ConverterType.INVERTER);

            builder.setLCCRectifier(lcc,
                    getInt(row, "nbr", 1),
                    getDouble(row, "anmnr", 0.0),
                    getDouble(row, "anmxr", 0.0),
                    getDouble(row, "rcr", 0.0),
                    getDouble(row, "xcr", 0.0),
                    getDouble(row, "ebasr", 0.0),
                    getDouble(row, "trr", 1.0),
                    getDouble(row, "tapr", 1.0),
                    getDouble(row, "tmxr", 1.5),
                    getDouble(row, "tmnr", 0.51),
                    getDouble(row, "stpr", 0.00625),
                    getDouble(row, "xcapr", 0.0),
                    null);

            builder.setLCCInverter(lcc,
                    getInt(row, "nbi", 1),
                    getDouble(row, "anmni", 0.0),
                    getDouble(row, "anmxi", 0.0),
                    getDouble(row, "rci", 0.0),
                    getDouble(row, "xci", 0.0),
                    getDouble(row, "ebasi", 0.0),
                    getDouble(row, "tri", 1.0),
                    getDouble(row, "tapi", 1.0),
                    getDouble(row, "tmxi", 1.5),
                    getDouble(row, "tmni", 0.51),
                    getDouble(row, "stpi", 0.00625),
                    getDouble(row, "xcapi", 0.0),
                    null);
        } catch (Exception e) {
            log.error("Error parsing RAWX twotermdc {}: {}", name, e.getMessage());
        }
    }

    // ==================== Two-terminal VSC HVDC ====================

    private void parseVscDcRow(Map<String, JsonElement> row) throws InterpssException {
        String name = getString(row, "name", "").trim();
        int mdc = getInt(row, "mdc", 1);
        double rdc = getDouble(row, "rdc", 0.0);

        int bus1Num = getInt(row, "ibus1", 0);
        int type1 = getInt(row, "type1", 1);
        int mode1 = getInt(row, "mode1", 1);
        double dcSet1 = getDouble(row, "dcset1", 0.0);
        double acSet1 = getDouble(row, "acset1", 1.0);
        double smax1 = getDouble(row, "smax1", 0.0);
        double maxQ1 = getDouble(row, "maxq1", 9999.0);
        double minQ1 = getDouble(row, "minq1", -9999.0);
        int remot1 = getInt(row, "vsreg1", 0);
        double rmpct1 = getDouble(row, "rmpct1", 100.0);

        int bus2Num = getInt(row, "ibus2", 0);
        int type2 = getInt(row, "type2", 1);
        int mode2 = getInt(row, "mode2", 1);
        double dcSet2 = getDouble(row, "dcset2", 0.0);
        double acSet2 = getDouble(row, "acset2", 1.0);
        double smax2 = getDouble(row, "smax2", 0.0);
        double maxQ2 = getDouble(row, "maxq2", 9999.0);
        double minQ2 = getDouble(row, "minq2", -9999.0);
        int remot2 = getInt(row, "vsreg2", 0);
        double rmpct2 = getDouble(row, "rmpct2", 100.0);

        if (bus1Num <= 0 || bus2Num <= 0) {
            return;
        }

        boolean isConv1Rec = (type1 == 2 && dcSet1 < 0) || (type2 == 2 && dcSet2 > 0);
        int recBusNum = isConv1Rec ? bus1Num : bus2Num;
        int invBusNum = isConv1Rec ? bus2Num : bus1Num;
        String fromBusId = BUS_ID_PREFIX + recBusNum;
        String toBusId = BUS_ID_PREFIX + invBusNum;
        String vscId = name.isEmpty() ? fromBusId + "_" + toBusId : name;

        try {
            HvdcLine2TVSC<AclfBus> vsc = builder.addHvdcLine2TVSC(
                    vscId, name, fromBusId, toBusId, mdc != 0, rdc, 0.0);

            VSCConverter recConv = (VSCConverter) vsc.getRecConverter();
            recConv.setId("VSC Rec_" + fromBusId);
            configVSCConverter(recConv, recBusNum,
                    isConv1Rec ? type1 : type2, isConv1Rec ? mode1 : mode2,
                    isConv1Rec ? dcSet1 : dcSet2, isConv1Rec ? acSet1 : acSet2,
                    isConv1Rec ? smax1 : smax2,
                    isConv1Rec ? maxQ1 : maxQ2, isConv1Rec ? minQ1 : minQ2,
                    isConv1Rec ? remot1 : remot2, isConv1Rec ? rmpct1 : rmpct2);

            VSCConverter invConv = (VSCConverter) vsc.getInvConverter();
            invConv.setId("VSC Inv_" + toBusId);
            configVSCConverter(invConv, invBusNum,
                    isConv1Rec ? type2 : type1, isConv1Rec ? mode2 : mode1,
                    isConv1Rec ? dcSet2 : dcSet1, isConv1Rec ? acSet2 : acSet1,
                    isConv1Rec ? smax2 : smax1,
                    isConv1Rec ? maxQ2 : maxQ1, isConv1Rec ? minQ2 : minQ1,
                    isConv1Rec ? remot2 : remot1, isConv1Rec ? rmpct2 : rmpct1);
        } catch (Exception e) {
            log.error("Error parsing RAWX vscdc {}: {}", name, e.getMessage());
        }
    }

    private void configVSCConverter(VSCConverter converter, int busNum,
            int type, int mode, double dcSet, double acSet,
            double smax, double maxQ, double minQ,
            int remoteBusNum, double rmpct) {
        HvdcControlMode dcCtrl = type == 0 ? HvdcControlMode.BLOCKED
                : type == 1 ? HvdcControlMode.DC_VOLTAGE : HvdcControlMode.DC_POWER;
        VSCAcControlMode acCtrl = mode == 1 ? VSCAcControlMode.AC_VOLTAGE
                : VSCAcControlMode.AC_POWER_FACTOR;
        String remoteBusId = (remoteBusNum > 0 && remoteBusNum != busNum)
                ? BUS_ID_PREFIX + remoteBusNum : null;
        builder.setVSCConverter(converter, BUS_ID_PREFIX + busNum, dcCtrl, Math.abs(dcSet),
                acCtrl, acSet, smax, maxQ, minQ, remoteBusId, rmpct);
    }

    // ==================== Multi-terminal DC ====================

    private void parseMultiTerminalDc(JsonObject network) throws InterpssException {
        Map<String, HvdcLineMT> byName = new HashMap<>();

        parseFieldData(network, "ntermdc", row -> {
            String name = getString(row, "name", "").trim();
            if (name.isEmpty() || "0".equals(name)) {
                return;
            }
            int mdc = getInt(row, "mdc", 0);
            int vconv = getInt(row, "vconv", 0);
            double vcmod = getDouble(row, "vcmod", 0.0);
            int vconvn = getInt(row, "vconvn", 0);
            HvdcControlMode controlMode = mdc == 1 ? HvdcControlMode.DC_POWER
                    : mdc == 2 ? HvdcControlMode.DC_CURRENT : HvdcControlMode.BLOCKED;
            String vConvBusId = vconv > 0 ? BUS_ID_PREFIX + vconv : "";
            String vConvNBusId = vconvn > 0 ? BUS_ID_PREFIX + vconvn : "";
            HvdcLineMT mtLine = builder.addHvdcLineMT(
                    name, controlMode, vConvBusId, vConvNBusId, vcmod, mdc != 0);
            byName.put(name, mtLine);
            builder.registerNamedEquipment(name, mtLine);
            builder.registerNamedEquipment("N|" + name, mtLine);
        });

        parseFieldData(network, "ntermdcconv", row -> {
            String name = getString(row, "name", "").trim();
            HvdcLineMT mtLine = byName.get(name);
            if (mtLine == null) {
                return;
            }
            int ib = getInt(row, "ib", 0);
            if (ib <= 0) {
                return;
            }
            builder.addHvdcMTConverter(mtLine, BUS_ID_PREFIX + ib,
                    getInt(row, "nbrdg", 1),
                    getDouble(row, "angmx", 0.0),
                    getDouble(row, "angmn", 0.0),
                    getDouble(row, "rc", 0.0),
                    getDouble(row, "xc", 0.0),
                    getDouble(row, "ebas", 0.0),
                    getDouble(row, "tr", 1.0),
                    getDouble(row, "tap", 1.0),
                    getDouble(row, "tpmx", 1.5),
                    getDouble(row, "tpmn", 0.51),
                    getDouble(row, "tstp", 0.00625),
                    getDouble(row, "setvl", 0.0),
                    getDouble(row, "dcpf", 1.0),
                    getDouble(row, "marg", 0.0),
                    getInt(row, "cnvcod", 1));
        });

        parseFieldData(network, "ntermdcbus", row -> {
            String name = getString(row, "name", "").trim();
            HvdcLineMT mtLine = byName.get(name);
            if (mtLine == null) {
                return;
            }
            int idc = getInt(row, "idc", 0);
            int ib = getInt(row, "ib", 0);
            String acBusId = ib > 0 ? BUS_ID_PREFIX + ib : "";
            builder.addHvdcMTDcBus(mtLine, idc, acBusId,
                    getInt(row, "area", 1),
                    getInt(row, "zone", 1),
                    getString(row, "dcname", "").trim(),
                    getInt(row, "idc2", 0),
                    getDouble(row, "rgrnd", 0.0),
                    getInt(row, "owner", 1));
        });

        parseFieldData(network, "ntermdclink", row -> {
            String name = getString(row, "name", "").trim();
            HvdcLineMT mtLine = byName.get(name);
            if (mtLine == null) {
                return;
            }
            builder.addHvdcMTDcLink(mtLine,
                    getInt(row, "idc", 0),
                    getInt(row, "jdc", 0),
                    getString(row, "dcckt", "1").trim(),
                    getInt(row, "met", 1),
                    getDouble(row, "rdc", 0.0),
                    getDouble(row, "ldc", 0.0));
        });

        for (HvdcLineMT mtLine : byName.values()) {
            String topoErr = mtLine.validateTopology();
            if (topoErr != null) {
                log.warn(topoErr);
            }
        }
    }

    // ==================== Impedance correction ====================

    private void parseImpcor(JsonObject network) throws InterpssException {
        Map<Integer, List<XfrZCorrection>> tables = new HashMap<>();
        parseFieldData(network, "impcor", row -> {
            int tableNum = getInt(row, "itable", 0);
            if (tableNum <= 0) {
                return;
            }
            double tap = getDouble(row, "tap", 0.0);
            double reF = getDouble(row, "refact", 0.0);
            double imF = getDouble(row, "imfact", 0.0);
            if (tap == 0.0 && reF == 0.0 && imF == 0.0) {
                return;
            }
            tables.computeIfAbsent(tableNum, k -> new ArrayList<>())
                    .add(new XfrZCorrection(tap, new Complex(reF, imF)));
        });
        for (Map.Entry<Integer, List<XfrZCorrection>> e : tables.entrySet()) {
            if (!e.getValue().isEmpty()) {
                builder.addXfrZTableEntry(e.getKey(), e.getValue());
            }
        }
    }

    // ==================== FACTS / SVC ====================

    private void parseFactsRow(Map<String, JsonElement> row) throws InterpssException {
        String name = getString(row, "name", "").trim();
        int busNum = getInt(row, "ibus", 0);
        int jBus = getInt(row, "jbus", 0);
        int mode = getInt(row, "mode", 1);
        double qdes = getDouble(row, "qdes", 0.0);
        double vset = getDouble(row, "vset", 1.0);
        double shmx = getDouble(row, "shmx", 9999.0);
        double linx = getDouble(row, "linx", 0.05);
        double rmpct = getDouble(row, "rmpct", 100.0);
        int fcreg = getInt(row, "fcreg", 0);
        if (busNum <= 0) {
            return;
        }
        String busId = BUS_ID_PREFIX + busNum;

        if (jBus == 0) {
            double qMaxPU = Math.abs(shmx) / baseMva;
            double qMinPU = -qMaxPU;
            String remoteBusId = null;
            if (fcreg > 0 && fcreg != busNum) {
                remoteBusId = BUS_ID_PREFIX + fcreg;
            }
            StaticVarCompensator svc = builder.addSVC(busId, name, mode != 0,
                    qMaxPU, qMinPU, vset, remoteBusId, rmpct);
            BaseAclfBus bus = builder.getBus(busId);
            if (svc != null && bus != null && bus.getVoltageMag() != 0.0) {
                double voltage = bus.getVoltageMag();
                svc.setBInit(qdes / baseMva / (voltage * voltage));
            }
        } else {
            String toBusId = BUS_ID_PREFIX + jBus;
            double pdes = getDouble(row, "pdes", 0.0);
            double set1 = getDouble(row, "set1", 0.0);
            double set2 = getDouble(row, "set2", 0.0);
            double qMaxPU = shmx / baseMva;
            String remoteBusId = null;
            if (fcreg > 0 && fcreg != busNum) {
                remoteBusId = BUS_ID_PREFIX + fcreg;
            }
            Complex targetPQ = new Complex(pdes / baseMva, qdes / baseMva);
            try {
                AclfBranch factsBra = builder.addFactsDevice(busId, toBusId,
                        name.isEmpty() ? "FD" : name,
                        mode, linx, set1, set2,
                        qMaxPU, 0.0, vset,
                        remoteBusId, rmpct, targetPQ, mode != 0);
                if (factsBra != null && !name.isEmpty()) {
                    factsBra.setName(name);
                }
            } catch (Exception e) {
                log.error("Error parsing RAWX facts {}: {}", name, e.getMessage());
            }
        }
    }

    // ==================== Induction machine (NB tags) ====================

    private void parseIndMachRow(Map<String, JsonElement> row) {
        int busNum = getInt(row, "ibus", 0);
        if (busNum <= 0) {
            return;
        }
        String id = getString(row, "imid", "1").trim();
        if (id.isEmpty()) {
            id = "1";
        }
        String name = getString(row, "name", "").trim();
        String busId = BUS_ID_PREFIX + busNum;
        NameTag tag = NetFactory.eINSTANCE.createNameTag();
        tag.setId(id);
        tag.setName(name.isEmpty() ? id : name);
        builder.registerNamedEquipment("I|" + busId + "|" + id, tag);
    }

    // ==================== Area / Zone / Owner ====================

    private void parseAreaRow(Map<String, JsonElement> row) throws InterpssException {
        int areaNum = getInt(row, "iarea", 0);
        String name = getString(row, "arname", getString(row, "arnam", "Area " + areaNum));
        if (areaNum > 0) builder.addArea(String.valueOf(areaNum), name, null);
    }

    private void parseZoneRow(Map<String, JsonElement> row) throws InterpssException {
        int zoneNum = getInt(row, "izone", 0);
        String name = getString(row, "zoname", getString(row, "zonam", "Zone " + zoneNum));
        if (zoneNum > 0) builder.addZone(String.valueOf(zoneNum), name, null);
    }

    private void parseOwnerRow(Map<String, JsonElement> row) throws InterpssException {
        int ownerNum = getInt(row, "iowner", 0);
        String name = getString(row, "owname", getString(row, "ownam", "Owner " + ownerNum));
        if (ownerNum > 0) builder.addOwner(String.valueOf(ownerNum), name);
    }
}
