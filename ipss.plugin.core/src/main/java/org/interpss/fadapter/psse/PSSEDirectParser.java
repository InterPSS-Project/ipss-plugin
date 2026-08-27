/*
 * @(#)PSSEDirectParser.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.psse;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkObjectFactory;
import org.interpss.fadapter.builder.AclfNetworkBuilder.ShuntBlock;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.datatype.XfrZCorrection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.NameTag;
import com.interpss.core.net.NetFactory;
import com.interpss.core.net.OriginalDataFormat;

/**
 * Direct PSS/E RAW file parser that bypasses the ODM XML intermediate layer.
 * Reads PSS/E RAW files (v29-v36) and populates AclfNetwork via AclfNetworkBuilder.
 *
 * By default, the PSS/E revision ({@code REV} on the IC/SBASE/REV header line) is
 * auto-detected. Constructors that take an {@code int version} force that layout
 * as an override (useful for tests and deliberate mis-version experiments).
 *
 * The section-by-section parsing order mirrors PSSELFRawAdapter in ipss-odm.
 * Field extraction logic is ported from the individual PSSExxxDataRawParser classes.
 *
 * <p>For v34+ files, system-wide load-flow records are retained as
 * {@link PsseLoadflowSolutionSettings} on network extra info. Parsing does not
 * unconditionally impose their solver activity flags: the run layer first
 * chooses whether saved-solution replay is intended, then explicit JSON
 * configuration may override the imported setting.</p>
 *
 * <p>Version-specific switched-shunt fields preserve remote participation and
 * FACTS supervision. Phase-shifter limits are normalized before builder calls
 * because source records do not guarantee the two range fields arrive in
 * numerical max/min order.</p>
 */
public class PSSEDirectParser {
    private static final Logger log = LoggerFactory.getLogger(PSSEDirectParser.class);
    private static final String BUS_ID_PREFIX = "Bus";
    /** Non-null forces section layout; null means resolve from header REV. */
    private final Integer versionOverride;
    /** Resolved after {@link #parseHeader}; used for all version gates. */
    private int version;
    private final AclfNetworkBuilder builder;
    private final Map<String, Complex> rawType3BusVoltages = new HashMap<>();
    private double baseMva = 100.0;
    private PsseLoadflowSolutionSettings solutionSettings;

    /** Auto-detect REV from the RAW header. */
    public PSSEDirectParser() {
        this.versionOverride = null;
        this.builder = new AclfNetworkBuilder();
    }

    /** Auto-detect REV; populate the given network. */
    public PSSEDirectParser(BaseAclfNetwork<?,?> network) {
        this.versionOverride = null;
        this.builder = new AclfNetworkBuilder(network);
    }

    /** Auto-detect REV; populate the given network with a custom object factory. */
    public PSSEDirectParser(BaseAclfNetwork<?,?> network,
            AclfNetworkObjectFactory objectFactory) {
        this.versionOverride = null;
        this.builder = new AclfNetworkBuilder(network, objectFactory);
    }

    /** Force section layout to {@code version} (override header REV). */
    public PSSEDirectParser(int version) {
        this.versionOverride = version;
        this.builder = new AclfNetworkBuilder();
    }

    /** Force section layout to {@code version}; populate the given network. */
    public PSSEDirectParser(int version, BaseAclfNetwork<?,?> network) {
        this.versionOverride = version;
        this.builder = new AclfNetworkBuilder(network);
    }

    /** Force section layout to {@code version}; populate with a custom object factory. */
    public PSSEDirectParser(int version, BaseAclfNetwork<?,?> network,
            AclfNetworkObjectFactory objectFactory) {
        this.versionOverride = version;
        this.builder = new AclfNetworkBuilder(network, objectFactory);
    }

    /** PSS/E revision used for section layout after parse (override or header REV). */
    public int getVersion() {
        return version;
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        parseInto(filepath);
        return builder.getNetwork();
    }

    /**
     * Parse a PSS/E file and populate the pre-configured network.
     * Unlike parse(), this does not cast to AclfNetwork, so it works
     * when the builder holds an AcscNetwork or DStabilityNetwork.
     */
    public void parseInto(String filepath) throws InterpssException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            parseFromReaderInternal(reader);
        } catch (IOException e) {
            throw new InterpssException("Error reading PSS/E file: " + filepath + ": " + e.getMessage());
        }
    }

    public AclfNetwork parseFromReader(BufferedReader reader) throws InterpssException {
        parseFromReaderInternal(reader);
        return builder.getNetwork();
    }

    /** Return the preserved v34+ system-wide load-flow solution profile. */
    public PsseLoadflowSolutionSettings getSolutionSettings() {
        return solutionSettings;
    }

    private void parseFromReaderInternal(BufferedReader reader) throws InterpssException {
        try {
            rawType3BusVoltages.clear();
            parseHeader(reader);
            parseSection(reader, this::parseBusLine);
            parseSection(reader, this::parseLoadLine);
            if (version >= 31) collectFixedShunts(reader);
            if (version >= 36) parseSection(reader, null);
            parseSection(reader, this::parseGenLine);
            PSSEGeneratorReactivePower.finalizeBusTypes(builder);
            if (version >= 36) parseSection(reader, null);
            parseSection(reader, this::parseLineLine);
            if (version >= 34) parseSection(reader, this::parseSystemSwitchingDeviceLine);
            parseXfrSection(reader);
            parseSection(reader, this::parseAreaLine);
            parseMultiLineSection(reader, 3, this::parseHvdc2TLCCLines);
            parseMultiLineSection(reader, 3, this::parseHvdc2TVSCLines);

            if (version <= 30) parseSection(reader, this::parseSwitchedShuntLine);

            parseXfrZCorrSection(reader);
            parseMultiTerminalDcSection(reader);
            parseSection(reader, null); // Multi-section line
            parseSection(reader, this::parseZoneLine);
            parseSection(reader, null);
            parseSection(reader, this::parseOwnerLine);
            parseSection(reader, this::parseFACTSLine);

            if (version >= 31) parseSection(reader, this::parseSwitchedShuntLine);
            if (version >= 33) parseSection(reader, null); // GNE
            if (version >= 33) parseSection(reader, this::parseInductionMachineLine);
            // v36+: Load Type, Interface, Interface Element precede Substation Data
            if (version >= 36) parseSection(reader, null); // Load Type
            if (version >= 36) parseSection(reader, null); // Interface
            if (version >= 36) parseSection(reader, null); // Interface Element

            if (version >= 33) {
                new PSSESubstationImporter(builder).parse(reader);
            }

            builder.finalizeNetwork();
            rawType3BusVoltages.forEach((busId, voltage) ->
                    builder.getBus(busId).setVoltage(voltage));
        } catch (IOException e) {
            throw new InterpssException("Error parsing PSS/E data: " + e.getMessage());
        }
    }

    // ==================== Header ====================

    private void parseHeader(BufferedReader reader) throws IOException, InterpssException {
        String line1 = reader.readLine();
        if (line1 == null) throw new InterpssException("Empty PSS/E file");
        while (line1 != null && line1.startsWith("@!")) {
            line1 = reader.readLine();
        }
        if (line1 == null) throw new InterpssException("Empty PSS/E file after comments");
        String line2 = reader.readLine();
        String line3 = reader.readLine();

        PSSEDataRec rec = new PSSEDataRec(line1);
        // IC, SBASE, REV, XFRRAT, NXFRAT, BASFRQ
        baseMva = rec.getDouble(1, 100.0);
        this.version = versionOverride != null
                ? versionOverride
                : PsseRev.fromHeaderLine(line1);

        builder.setNetworkInfo("Base_Case_from_PSS_E_format",
                (line2 != null ? line2.trim() : "PSS/E Case"),
                baseMva * 1000.0, // convert MVA to kVA
                OriginalDataFormat.PSSE);

        // For v34+, skip system-wide data section
        if (version >= 34) {
            /*
             * Preserve the complete solved-case settings block, including
             * THRSHZ. PSS/E uses THRSHZ when deciding whether an eligible
             * non-transformer branch represents one electrical node. Using an
             * unrelated default can leave artificial mismatches across RAW bus
             * ties. PsseLoadflowSolutionSettings parses THRSHZ below and the
             * validated value is applied to the imported network after the
             * entire block has been read.
             */
            PsseLoadflowSolutionSettings.Builder settingsBuilder =
                    PsseLoadflowSolutionSettings.builder(version);
            String line;
            while ((line = reader.readLine()) != null) {
                if (PSSEDataRec.isEndRec(line)) break;
                settingsBuilder.addLine(line);
            }
            solutionSettings = settingsBuilder.build();
            builder.getNetwork().getExtraInfo().put(
                    com.interpss.core.algo.LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY,
                    solutionSettings);
            if (solutionSettings.general().thrshz() != null
                    && Double.isFinite(solutionSettings.general().thrshz())
                    && solutionSettings.general().thrshz() >= 0.0) {
                builder.getNetwork().setZeroZBranchThreshold(
                        solutionSettings.general().thrshz());
            }
            if (solutionSettings.general().pqbrak() != null
                    && Double.isFinite(solutionSettings.general().pqbrak())
                    && solutionSettings.general().pqbrak() >= 0.0) {
                builder.getNetwork().getBusLoadLowVoltConfig().setVConstPMin(
                        solutionSettings.general().pqbrak());
            }
        } else {
            solutionSettings = null;
        }
    }

    // ==================== Section Parsing Framework ====================

    @FunctionalInterface
    private interface LineProcessor {
        void process(PSSEDataRec rec) throws InterpssException;
    }

    @FunctionalInterface
    private interface MultiLineProcessor {
        void process(List<String> lines) throws InterpssException;
    }

    private void parseSection(BufferedReader reader, LineProcessor processor) throws IOException, InterpssException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("//") || line.startsWith("@!")) continue;
            if (PSSEDataRec.isEndRec(line)) break;
            if (processor != null) {
                PSSEDataRec rec = new PSSEDataRec(line);
                processor.process(rec);
            }
        }
    }

    private void parseMultiLineSection(BufferedReader reader, int linesPerRecord,
                                       MultiLineProcessor processor) throws IOException, InterpssException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("//") || line.startsWith("@!")) continue;
            if (PSSEDataRec.isEndRec(line)) break;
            List<String> lines = new ArrayList<>();
            lines.add(line);
            for (int i = 1; i < linesPerRecord; i++) {
                String next = reader.readLine();
                if (next != null) lines.add(next);
            }
            if (processor != null) processor.process(lines);
        }
    }

    // ==================== Bus ====================

    private void parseBusLine(PSSEDataRec rec) throws InterpssException {
        int busNum = rec.getInt(0);
        String busId = BUS_ID_PREFIX + busNum;
        // Bus number 0 is never valid PSS/E bus data (section terminators / misaligned system-wide lines)
        if (busNum <= 0) {
            log.warn("Skipping invalid bus record with bus number: " + busNum);
            return;
        }
        String name = rec.getString(1);
        double baseKv = rec.getDouble(2);
        if (baseKv == 0.0) {
            baseKv = 1.0;
        }

        int ide;
        double vm, va;
        int areaNum, zoneNum, ownerNum;

        if (version >= 31) {
            // V31+: I, NAME, BASKV, IDE, AREA, ZONE, OWNER, VM, VA [, NVHI, NVLO, EVHI, EVLO]
            ide = rec.getInt(3, 1);
            areaNum = rec.getInt(4, 0);
            zoneNum = rec.getInt(5, 0);
            ownerNum = rec.getInt(6, 0);
            vm = rec.getDouble(7, 1.0);
            va = rec.getDouble(8, 0.0);
        } else {
            // V29-30: I, NAME, BASKV, IDE, GL, BL, AREA, ZONE, VM, VA, OWNER
            ide = rec.getInt(3, 1);
            areaNum = rec.getInt(6, 0);
            zoneNum = rec.getInt(7, 0);
            vm = rec.getDouble(8, 1.0);
            va = rec.getDouble(9, 0.0);
            ownerNum = rec.getInt(10, 0);
        }

        String areaId = areaNum > 0 ? String.valueOf(areaNum) : null;
        String zoneId = zoneNum > 0 ? String.valueOf(zoneNum) : null;
        String ownerId = ownerNum > 0 ? String.valueOf(ownerNum) : null;

        if (areaId != null) builder.addArea(areaId, "Area " + areaNum, null);
        if (zoneId != null) builder.addZone(zoneId, "Zone " + zoneNum, null);
        if (ownerId != null) builder.addOwner(ownerId, "Owner " + ownerNum);

        BaseAclfBus bus = builder.addBus(busId, name, busNum, baseKv * 1000.0,
                vm, Math.toRadians(va), areaId, zoneId, ownerId);
        applyNameTagMetadata(rec, bus);

        if (ide == 4) bus.setStatus(false);

        // Bus gen code (initial, will be refined by generator records)
        if (ide == 3) {
            rawType3BusVoltages.put(busId, bus.getVoltage());
            builder.setSwingBus(busId, vm, Math.toRadians(va));
        } else if (ide == 2) {
            // PV initially - will be set properly by gen record
            bus.setGenCode(AclfGenCode.GEN_PV);
        } else {
            bus.setGenCode(AclfGenCode.NON_GEN);
        }

        // For v29-30, fixed shunt (GL, BL) is in the bus record
        if (version <= 30) {
            double gl = rec.getDouble(4, 0.0);
            double bl = rec.getDouble(5, 0.0);
            if (gl != 0.0 || bl != 0.0) {
                // GL, BL entered in MW at 1.0 pu voltage -> convert to PU on system base
                builder.setBusShuntY(busId, new Complex(gl / baseMva, bl / baseMva));
            }
        }
    }

    // ==================== Load ====================

    private void parseLoadLine(PSSEDataRec rec) throws InterpssException {
        // I, ID, STATUS, AREA, ZONE, PL, QL, IP, IQ, YP, YQ, OWNER [, SCALE, INTRPT, DGENP, DGENQ, DGENM]
        int busNum = rec.getInt(0);
        String busId = BUS_ID_PREFIX + busNum;
        String loadId = rec.getString(1, "1").trim();
        int status = rec.getInt(2, 1);

        double pl = rec.getDouble(5, 0.0);
        double ql = rec.getDouble(6, 0.0);
        double ip = rec.getDouble(7, 0.0);
        double iq = rec.getDouble(8, 0.0);
        double yp = rec.getDouble(9, 0.0);
        double yq = rec.getDouble(10, 0.0);

        // Convert MW/Mvar to PU
        Complex constP = (pl != 0.0 || ql != 0.0) ? new Complex(pl / baseMva, ql / baseMva) : null;
        Complex constI = (ip != 0.0 || iq != 0.0) ? new Complex(ip / baseMva, iq / baseMva) : null;
        // YQ is negative for inductive in PSS/E; negate to match convention
        Complex constZ = (yp != 0.0 || yq != 0.0) ? new Complex(yp / baseMva, -yq / baseMva) : null;

        Complex dgenPower = null;
        boolean dgenStatus = false;
        if (version >= 34) {
            double dgenp = rec.getDouble(14, 0.0);
            double dgenq = rec.getDouble(15, 0.0);
            int dgenm = rec.getInt(16, 0);
            if (dgenp != 0.0 || dgenq != 0.0) {
                dgenPower = new Complex(dgenp / baseMva, dgenq / baseMva);
                dgenStatus = (dgenm == 1);
            }
        }

        applyNameTagMetadata(rec, builder.addContributeLoad(busId, loadId, status == 1,
                constP, constI, constZ, dgenPower, dgenStatus));
    }

    // ==================== Generator ====================

    private void parseGenLine(PSSEDataRec rec) throws InterpssException {
        // I, ID, PG, QG, QT, QB, VS, IREG, MBASE, ZR, ZX, RT, XT, GTAP, STAT, RMPCT, PT, PB, O1..F4 [, WMOD, WPF]
        int busNum = rec.getInt(0);
        String busId = BUS_ID_PREFIX + busNum;
        String genId = rec.getString(1, "1").trim();

        double pg, qg, qt, qb, vs;
        int ireg, stat, wmod = 0;
        double mbase, zr, zx, rt, xt, gtap, rmpct, pt, pb;
        double wpf = 1.0;

        if (version >= 35) {
            // V35+: I, ID, PG, QG, QT, QB, VS, IREG, NREG, MBASE, ZR, ZX, RT, XT, GTAP, STAT, RMPCT, PT, PB, BASELOAD...
            pg = rec.getDouble(2, 0.0);
            qg = rec.getDouble(3, 0.0);
            qt = rec.getDouble(4, 0.0);
            qb = rec.getDouble(5, 0.0);
            vs = rec.getDouble(6, 1.0);
            ireg = rec.getInt(7, 0);
            // NREG at index 8
            mbase = rec.getDouble(9, 0.0);
            zr = rec.getDouble(10, 0.0);
            zx = rec.getDouble(11, 0.0);
            rt = rec.getDouble(12, 0.0);
            xt = rec.getDouble(13, 0.0);
            gtap = rec.getDouble(14, 1.0);
            stat = rec.getInt(15, 1);
            rmpct = rec.getDouble(16, 100.0);
            pt = rec.getDouble(17, 0.0);
            pb = rec.getDouble(18, 0.0);
            wmod = rec.getInt(28, 0);
            wpf = rec.getDouble(29, 1.0);
        } else {
            // V29-34: I, ID, PG, QG, QT, QB, VS, IREG, MBASE, ZR, ZX, RT, XT, GTAP, STAT, RMPCT, PT, PB...
            pg = rec.getDouble(2, 0.0);
            qg = rec.getDouble(3, 0.0);
            qt = rec.getDouble(4, 0.0);
            qb = rec.getDouble(5, 0.0);
            vs = rec.getDouble(6, 1.0);
            ireg = rec.getInt(7, 0);
            mbase = rec.getDouble(8, 0.0);
            zr = rec.getDouble(9, 0.0);
            zx = rec.getDouble(10, 0.0);
            rt = rec.getDouble(11, 0.0);
            xt = rec.getDouble(12, 0.0);
            gtap = rec.getDouble(13, 1.0);
            stat = rec.getInt(14, 1);
            rmpct = rec.getDouble(15, 100.0);
            pt = rec.getDouble(16, 0.0);
            pb = rec.getDouble(17, 0.0);
            if (version >= 32) {
                wmod = rec.getInt(26, 0);
                wpf = rec.getDouble(27, 1.0);
            }
        }

        if (mbase == 0.0) mbase = baseMva;

        BaseAclfBus bus = builder.getBus(busId);
        if (bus == null) {
            log.error("Bus " + busId + " not found for generator " + genId);
            return;
        }

        if (bus.getGenCode() != AclfGenCode.SWING) {
            PSSEGeneratorReactivePower.Data reactiveData =
                    PSSEGeneratorReactivePower.resolve(pg, qg, qt, qb, wmod, wpf);
            qg = reactiveData.qGen();
            qt = reactiveData.qMax();
            qb = reactiveData.qMin();
        }

        boolean genStatus = (stat == 1);
        if (!bus.isActive()) genStatus = false;

        String remoteBusId = null;
        if (ireg > 0 && ireg != busNum) {
            String candidate = BUS_ID_PREFIX + ireg;
            if (builder.getBus(candidate) != null) {
                remoteBusId = candidate;
            } else {
                log.warn("Generator " + genId + " at " + busId + " has IREG=" + ireg
                        + " pointing to non-existent bus; using local voltage control");
            }
        }
        Complex sourceZ = (zr != 0.0 || zx != 0.0) ? new Complex(zr, zx) : null;
        Complex xfrZ = (rt != 0.0 || xt != 0.0) ? new Complex(rt, xt) : null;

        applyNameTagMetadata(rec, builder.addContributeGen(busId, genId, genStatus,
                pg / baseMva, qg / baseMva, mbase,
                vs,
                qt / baseMva, qb / baseMva,
                pt / baseMva, pb / baseMva,
                sourceZ, xfrZ, gtap,
                remoteBusId,
                rmpct * 0.01, 1.0));

        // Refine bus gen code based on generator data
        if (bus.getGenCode() == AclfGenCode.SWING) {
            builder.setSwingBus(busId, vs, bus.getVoltageAng());
            bus.setGenP(pg / baseMva);
        } else if (bus.getGenCode() == AclfGenCode.GEN_PV
                && genStatus && qt != qb) {
            // Keep the existing PV/remote-Q initialization path for every
            // machine that can regulate voltage. Fixed-Q machines are
            // classified only after all generators at the bus are parsed.
            builder.setPVBus(busId, pg / baseMva, vs,
                    qt / baseMva, qb / baseMva, true);
        }
    }

    // ==================== Fixed Shunt (v31+, separate section) ====================

    /**
     * Read fixed-shunt records and create named {@link com.interpss.core.aclf.ShuntCompensator}s
     * immediately (before substation import so NB type-F terminals can resolve).
     */
    private void collectFixedShunts(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("//") || line.startsWith("@!")) continue;
            if (PSSEDataRec.isEndRec(line)) break;
            PSSEDataRec rec = new PSSEDataRec(line);
            int busNum = rec.getInt(0);
            String busId = BUS_ID_PREFIX + busNum;
            String id = rec.getString(1, "1").trim();
            int status = rec.getInt(2, 1);
            double gl = rec.getDouble(3, 0.0);
            double bl = rec.getDouble(4, 0.0);
            String name = rec.getString(5, "").trim();
            builder.addFixedShunt(busId, id, status == 1, gl / baseMva, bl / baseMva, name);
        }
    }

    // ==================== System Switching Device (v34+) ====================

    /**
     * PSS/E System Switching Device: I, J, CKT, X, [RSETNAM|RATE…], STAT, NSTAT, MET, STYPE, NAME.
     * Sample v36 uses rating-set name form (no RATE1–12).
     */
    private void parseSystemSwitchingDeviceLine(PSSEDataRec rec) throws InterpssException {
        int fromNum = Math.abs(rec.getInt(0));
        int toNum = Math.abs(rec.getInt(1));
        String ckt = rec.getString(2, "1").trim();
        if (ckt.isEmpty()) {
            ckt = "1";
        }
        double x = rec.getDouble(3, 0.0001);
        int status;
        int stype;
        String name;
        // New form: I,J,CKT,X,RSETNAM,STAT,NSTAT,MET,STYPE,NAME
        // Legacy form packs RATE1..RATE12 before STAT (STAT at index 16).
        String maybeRset = rec.getString(4, "").trim();
        if (!maybeRset.isEmpty() && !isNumericToken(maybeRset)) {
            status = rec.getInt(5, 1);
            stype = rec.getInt(8, 2);
            name = rec.getString(9, "").trim();
        } else {
            status = rec.getInt(16, 1);
            stype = rec.getInt(19, 2);
            name = rec.getString(20, "").trim();
        }

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;
        // STYPE 1=ZBR, 2=breaker, 3=disconnect — all modeled as BREAKER for LF
        AclfBranch bra = builder.addBreaker(fromBusId, toBusId, ckt,
                new Complex(0.0, x), status == 1, AclfBranchCode.BREAKER);
        if (bra != null && !name.isEmpty()) {
            bra.setName(name);
        }
        if (bra != null) {
            bra.setDesc("SystemSWD:stype=" + stype);
        }
    }

    private static boolean isNumericToken(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== Line/Branch ====================

    private void parseLineLine(PSSEDataRec rec) throws InterpssException {
        int fromNum = Math.abs(rec.getInt(0));
        int toNum = Math.abs(rec.getInt(1));
        String ckt = rec.getString(2, "1").trim();
        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        double r, x, b;
        double ratea, rateb, ratec;
        double gi, bi, gj, bj;
        int st;

        if (version >= 34) {
            // V34+: I, J, CKT, R, X, B, NAME, RATE1..RATE12, GI, BI, GJ, BJ, ST, [BP], MET, LEN...
            r = rec.getDouble(3);
            x = rec.getDouble(4);
            b = rec.getDouble(5);
            // NAME at 6
            ratea = rec.getDouble(7, 0.0);
            rateb = rec.getDouble(8, 0.0);
            ratec = rec.getDouble(9, 0.0);
            int giIdx = version >= 36 ? 19 : 19;
            gi = rec.getDouble(giIdx, 0.0);
            bi = rec.getDouble(giIdx + 1, 0.0);
            gj = rec.getDouble(giIdx + 2, 0.0);
            bj = rec.getDouble(giIdx + 3, 0.0);
            st = rec.getInt(giIdx + 4, 1);
        } else if (version >= 31) {
            // V31-33: I, J, CKT, R, X, B, RATEA, RATEB, RATEC, GI, BI, GJ, BJ, ST, MET, LEN...
            r = rec.getDouble(3);
            x = rec.getDouble(4);
            b = rec.getDouble(5);
            ratea = rec.getDouble(6, 0.0);
            rateb = rec.getDouble(7, 0.0);
            ratec = rec.getDouble(8, 0.0);
            gi = rec.getDouble(9, 0.0);
            bi = rec.getDouble(10, 0.0);
            gj = rec.getDouble(11, 0.0);
            bj = rec.getDouble(12, 0.0);
            st = rec.getInt(13, 1);
        } else {
            // V29-30: I, J, CKT, R, X, B, RATEA, RATEB, RATEC, GI, BI, GJ, BJ, ST, LEN...
            r = rec.getDouble(3);
            x = rec.getDouble(4);
            b = rec.getDouble(5);
            ratea = rec.getDouble(6, 0.0);
            rateb = rec.getDouble(7, 0.0);
            ratec = rec.getDouble(8, 0.0);
            gi = rec.getDouble(9, 0.0);
            bi = rec.getDouble(10, 0.0);
            gj = rec.getDouble(11, 0.0);
            bj = rec.getDouble(12, 0.0);
            st = rec.getInt(13, 1);
        }

        Complex zPU = new Complex(r, x);
        Complex halfBPU = new Complex(0.0, b * 0.5);
        Complex fromShuntY = (gi != 0.0 || bi != 0.0) ? new Complex(gi, bi) : null;
        Complex toShuntY = (gj != 0.0 || bj != 0.0) ? new Complex(gj, bj) : null;

        applyNameTagMetadata(rec, builder.addLine(fromBusId, toBusId, ckt,
                zPU, halfBPU, fromShuntY, toShuntY,
                ratea, rateb, ratec, st == 1));
    }

    // ==================== Transformer (multi-line) ====================

    private void parseXfrSection(BufferedReader reader) throws IOException, InterpssException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("//") || line.startsWith("@!")) continue;
            if (PSSEDataRec.isEndRec(line)) break;

            PSSEDataRec line1 = new PSSEDataRec(line);
            String line2Str = reader.readLine();
            String line3Str = reader.readLine();
            String line4Str = reader.readLine();
            String line5Str = null;

            boolean is3W = line1.is3WXfr();
            if (is3W) {
                line5Str = reader.readLine();
            }

            try {
                parseXfrRecord(line1, line2Str, line3Str, line4Str, line5Str, is3W);
            } catch (Exception e) {
                log.error("Error parsing transformer record: " + e.getMessage());
            }
        }
    }

    private void parseXfrRecord(PSSEDataRec line1, String line2Str, String line3Str,
                                String line4Str, String line5Str, boolean is3W) throws InterpssException {
        int fromNum = Math.abs(line1.getInt(0));
        int toNum = Math.abs(line1.getInt(1));
        int tertNum = Math.abs(line1.getInt(2));
        String ckt = line1.getString(3, "1").trim();
        int cw = line1.getInt(4, 1);
        int cz = line1.getInt(5, 1);
        int cm = line1.getInt(6, 1);
        double mag1 = line1.getDouble(7, 0.0);
        double mag2 = line1.getDouble(8, 0.0);
        int nonMeteredEnd = line1.getInt(9, 2);
        int stat = line1.getInt(11, 1);

        String fromBusId = BUS_ID_PREFIX + fromNum;
        String toBusId = BUS_ID_PREFIX + toNum;

        PSSEDataRec line2 = new PSSEDataRec(line2Str);
        double r12 = line2.getDouble(0);
        double x12 = line2.getDouble(1);
        double sbase12 = line2.getDouble(2, baseMva);

        PSSEDataRec line3 = new PSSEDataRec(line3Str);
        double windv1 = line3.getDouble(0, 1.0);
        double nomv1 = line3.getDouble(1, 0.0);
        double ang1 = line3.getDouble(2, 0.0);

        // Version-dependent field indices for line 3
        int codIdx, contIdx, rmaIdx, rmiIdx, vmaIdx, vmiIdx, ntpIdx, tabIdx;
        double rata1, ratb1, ratc1;
        if (version >= 34) {
            rata1 = line3.getDouble(3, 0.0);
            ratb1 = line3.getDouble(4, 0.0);
            ratc1 = line3.getDouble(5, 0.0);
            codIdx = 15; contIdx = 16;
            if (version >= 35) {
                rmaIdx = 18; rmiIdx = 19; vmaIdx = 20; vmiIdx = 21;
                ntpIdx = 22; tabIdx = 23;
            } else {
                rmaIdx = 17; rmiIdx = 18; vmaIdx = 19; vmiIdx = 20;
                ntpIdx = 21; tabIdx = 22;
            }
        } else {
            rata1 = line3.getDouble(3, 0.0);
            ratb1 = line3.getDouble(4, 0.0);
            ratc1 = line3.getDouble(5, 0.0);
            codIdx = 6; contIdx = 7; rmaIdx = 8; rmiIdx = 9;
            vmaIdx = 10; vmiIdx = 11; ntpIdx = 12; tabIdx = 13;
        }
        int cod1 = line3.getInt(codIdx, 0);
        int cont1 = line3.getInt(contIdx, 0);
        double rma1 = line3.getDouble(rmaIdx, 1.1);
        double rmi1 = line3.getDouble(rmiIdx, 0.9);
        double vma1 = line3.getDouble(vmaIdx, 1.1);
        double vmi1 = line3.getDouble(vmiIdx, 0.9);
        int ntp1 = line3.getInt(ntpIdx, 33);
        int tab1 = line3.getInt(tabIdx, 0);

        PSSEDataRec line4 = new PSSEDataRec(line4Str);
        double windv2 = line4.getDouble(0, 1.0);
        double nomv2 = line4.getDouble(1, 0.0);
        PSSEDataRec line5 = line5Str != null ? new PSSEDataRec(line5Str) : null;
        PSSEDataRec metadata = firstNameTagMetadata(line1, line2, line3, line4, line5);

        BaseAclfBus fromBus = builder.getBus(fromBusId);
        BaseAclfBus toBus = builder.getBus(toBusId);
        if (fromBus == null || toBus == null) {
            log.error("Xfr bus not found: " + fromBusId + " or " + toBusId);
            return;
        }
        double fromBaseV = fromBus.getBaseVoltage();
        double toBaseV = toBus.getBaseVoltage();

        double nomv1Kv = (nomv1 == 0.0) ? fromBaseV / 1000.0 : nomv1;
        double nomv2Kv = (nomv2 == 0.0) ? toBaseV / 1000.0 : nomv2;

        Complex zPU = convertZ(cz, r12, x12, sbase12);

        double fromTap = convertTap(cw, windv1, nomv1Kv, fromBaseV);
        double toTap = convertTap(cw, windv2, nomv2Kv, toBaseV);
		double tapMax = convertTap(cw, rma1, nomv1Kv, fromBaseV);
		double tapMin = convertTap(cw, rmi1, nomv1Kv, fromBaseV);
		Double tapStepSize = ntp1 > 1 ? (tapMax - tapMin) / (ntp1 - 1) : null;

        Complex magY = convertMagY(cm, mag1, mag2, nomv1Kv, sbase12, fromBaseV);

        if (is3W) {
            Aclf3WBranch branch3W = parse3WXfr(line1, line2, line3, line4, line5Str,
                    fromBusId, toBusId, BUS_ID_PREFIX + tertNum, ckt,
                    stat, cw, cz, cm, sbase12, nomv1Kv);
            applyNameTagMetadata(metadata, branch3W);
        } else {
            boolean isPhaseShifter = ang1 != 0.0 || Math.abs(cod1) == 3;
            AclfBranch branch;
            if (isPhaseShifter) {
                branch = builder.addPsXformer(fromBusId, toBusId, ckt,
                        zPU, fromTap, toTap,
                        ang1, 0.0,
                        magY, null,
                        rata1, ratb1, ratc1,
                        tab1, stat == 1);
            } else {
                branch = builder.addXformer2W(fromBusId, toBusId, ckt,
                        zPU, fromTap, toTap,
                        magY, null,
                        rata1, ratb1, ratc1,
                        tab1, stat == 1);
            }
            applyNameTagMetadata(metadata, branch);

            String branchId = branch.getId();
            // CONT=0 means "control own bus" in PSS/E; do not build vcBusId "Bus0"
            if (Math.abs(cod1) == 1 && cont1 != 0) {
                String vcBusId = BUS_ID_PREFIX + Math.abs(cont1);
                // PSS/E uses the sign of CONT to define the response side; it is
                // not determined by whether |CONT| happens to equal a transformer
                // terminal. Positive means winding 2/3, negative means winding 1.
                builder.addTapVoltageRangeControl(branchId, vcBusId, cod1 > 0,
                        vma1, vmi1, tapMax, tapMin,
                        true, cont1 < 0, tapStepSize,
                        ntp1 > 0 ? ntp1 : null);
            } else if (Math.abs(cod1) == 1) {
                // Local voltage control (CONT=0): control the from-bus
                builder.addTapVoltageRangeControl(branchId, fromBusId, cod1 > 0,
                        vma1, vmi1, tapMax, tapMin,
                        true, true, tapStepSize, ntp1 > 0 ? ntp1 : null);
            } else if (Math.abs(cod1) == 3) {
                double pRangeMax = Math.max(vma1, vmi1) / baseMva;
                double pRangeMin = Math.min(vma1, vmi1) / baseMva;
                builder.addPsXfrAngleRangeControl(branchId, cod1 > 0,
                        pRangeMax, pRangeMin,
                        (vma1 + vmi1) / 2.0, UnitType.mW,
                        rma1, rmi1,
                        true, true, nonMeteredEnd == 1);
            }
        }
    }

    private Complex convertZ(int cz, double r, double x, double sbase) {
        if (cz == 1) {
            return new Complex(r, x);
        } else if (cz == 2) {
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
        if (cw == 1) {
            return windv;
        } else if (cw == 2) {
            return windv * 1000.0 / busBaseV;
        } else if (cw == 3) {
            return windv * nomvKv * 1000.0 / busBaseV;
        }
        return windv;
    }

    private Complex convertMagY(int cm, double mag1, double mag2,
                                double nomv1Kv, double sbase12, double fromBaseV) {
        if (mag1 == 0.0 && mag2 == 0.0) return null;
        if (cm == 1) {
            return new Complex(mag1, mag2);
        } else {
            double fromBaseKv = fromBaseV / 1000.0;
            double ybase = baseMva / (fromBaseKv * fromBaseKv);
            double g_rv = mag1 / (nomv1Kv * nomv1Kv) * 1.0E-6;
            double g_pu = g_rv / ybase;
            double ybase_w12 = sbase12 / (nomv1Kv * nomv1Kv);
            double b_rv = -mag2 * ybase_w12;
            double b_pu = b_rv / ybase;
            return new Complex(g_pu, b_pu);
        }
    }

    private Aclf3WBranch parse3WXfr(PSSEDataRec line1, PSSEDataRec line2, PSSEDataRec line3,
                            PSSEDataRec line4, String line5Str,
                            String fromBusId, String toBusId, String tertBusId, String ckt,
                            int stat, int cw, int cz, int cm,
                            double sbase12, double nomv1Kv) throws InterpssException {
        double r12 = line2.getDouble(0);
        double x12 = line2.getDouble(1);
        sbase12 = line2.getDouble(2, baseMva);
        double r23 = line2.getDouble(3, 0.0);
        double x23 = line2.getDouble(4, 0.0);
        double sbase23 = line2.getDouble(5, baseMva);
        double r31 = line2.getDouble(6, 0.0);
        double x31 = line2.getDouble(7, 0.0);
        double sbase31 = line2.getDouble(8, baseMva);
        double starVMag = line2.getDouble(9, 1.0);
        double starVAng = line2.getDouble(10, 0.0);

        double windv1 = line3.getDouble(0, 1.0);
        double nomv1_3w = line3.getDouble(1, 0.0);
        double ang1 = line3.getDouble(2, 0.0);

        double windv2 = line4.getDouble(0, 1.0);
        double nomv2_3w = line4.getDouble(1, 0.0);
        double ang2 = line4.getDouble(2, 0.0);

        PSSEDataRec line5 = line5Str != null ? new PSSEDataRec(line5Str) : null;
        double windv3 = line5 != null ? line5.getDouble(0, 1.0) : 1.0;
        double nomv3_3w = line5 != null ? line5.getDouble(1, 0.0) : 0.0;
        double ang3 = line5 != null ? line5.getDouble(2, 0.0) : 0.0;

        BaseAclfBus fromBus = builder.getBus(fromBusId);
        BaseAclfBus toBus = builder.getBus(toBusId);
        BaseAclfBus tertBus = builder.getBus(tertBusId);
        double fromBaseV = fromBus != null ? fromBus.getBaseVoltage() : 1000.0;
        double toBaseV = toBus != null ? toBus.getBaseVoltage() : 1000.0;
        double tertBaseV = tertBus != null ? tertBus.getBaseVoltage() : 1000.0;

        if (nomv1_3w == 0.0) nomv1_3w = fromBaseV / 1000.0;
        if (nomv2_3w == 0.0) nomv2_3w = toBaseV / 1000.0;
        if (nomv3_3w == 0.0) nomv3_3w = tertBaseV / 1000.0;

        Complex z12PU = convertZ(cz, r12, x12, sbase12);
        Complex z23PU = convertZ(cz, r23, x23, sbase23);
        Complex z31PU = convertZ(cz, r31, x31, sbase31);

        double fromTap = convertTap(cw, windv1, nomv1_3w, fromBaseV);
        double toTap = convertTap(cw, windv2, nomv2_3w, toBaseV);
        double tertTap = convertTap(cw, windv3, nomv3_3w, tertBaseV);

        double mag1 = line1.getDouble(7, 0.0);
        double mag2 = line1.getDouble(8, 0.0);
        Complex magY = convertMagY(cm, mag1, mag2, nomv1_3w, sbase12, fromBaseV);

        // Read TAB numbers from lines 3, 4, 5 (version-dependent index)
        int tabIdx = version >= 35 ? 23 : version == 34 ? 22 : 13;
        int tab1 = line3.getInt(tabIdx, 0);
        int tab2 = line4.getInt(tabIdx, 0);
        int tab3 = line5 != null ? line5.getInt(tabIdx, 0) : 0;

        boolean isPhaseShifting = (ang1 != 0.0 || ang2 != 0.0 || ang3 != 0.0);
        // PSS/E 3W STAT: 0=out, 1=in, 2=winding2 out, 3=winding3 out, 4=winding1 out
        boolean inService = stat != 0;
        boolean wind1OffLine = !inService || stat == 4;
        boolean wind2OffLine = !inService || stat == 2;
        boolean wind3OffLine = !inService || stat == 3;

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
        return branch3W;
    }

    // ==================== Area ====================

    private void parseAreaLine(PSSEDataRec rec) throws InterpssException {
        // I, ISW, PDES, PTOL, ARNAME
        int areaNum = rec.getInt(0);
        int swingBusNum = rec.getInt(1, 0);
        double desiredPowerMW = rec.getDouble(2, 0.0);
        double toleranceMW = rec.getDouble(3, 10.0);
        String name = rec.getString(4, "Area " + areaNum);
        applyNameTagMetadata(rec, builder.addArea(String.valueOf(areaNum), name, null));
        if (swingBusNum > 0) {
            String swingBusId = BUS_ID_PREFIX + swingBusNum;
            BaseAclfBus areaSwingBus = builder.getBus(swingBusId);
            // PSS/E does not enforce area interchange for the area containing
            // the type-3 system swing bus. ISW remains useful area metadata,
            // but it must not become an active interchange controller.
            if (areaSwingBus != null && !areaSwingBus.isSwing()) {
                builder.addAreaInterchangeControl(areaNum, name,
                        swingBusId, desiredPowerMW, toleranceMW);
            }
        }
    }

    // ==================== Zone ====================

    private void parseZoneLine(PSSEDataRec rec) throws InterpssException {
        // I, ZONAME
        int zoneNum = rec.getInt(0);
        String name = rec.getString(1, "Zone " + zoneNum);
        applyNameTagMetadata(rec, builder.addZone(String.valueOf(zoneNum), name, null));
    }

    // ==================== Owner ====================

    private void parseOwnerLine(PSSEDataRec rec) throws InterpssException {
        // I, OWNAME
        int ownerNum = rec.getInt(0);
        String name = rec.getString(1, "Owner " + ownerNum);
        builder.addOwner(String.valueOf(ownerNum), name);
    }

    // ==================== Switched Shunt ====================

    private void parseSwitchedShuntLine(PSSEDataRec rec) throws InterpssException {
        int busNum = rec.getInt(0);
        String busId = BUS_ID_PREFIX + busNum;

        int modsw, stat;
        double vswhi, vswlo, rmpct, binit;
        int swreg;
        String shuntId = "1";
        String remoteDeviceId;

        if (version >= 35) {
            // v35+: I, ID, MODSW, ADJM, ST, VSWHI, VSWLO, SWREG, NREG, RMPCT, RMIDNT, BINIT, [NAME for v36], S1, N1, B1, ...
            shuntId = rec.getString(1, "1").trim();
            if (shuntId.isEmpty()) shuntId = "1";
            modsw = rec.getInt(2, 1);
            stat = rec.getInt(4, 1);
            vswhi = rec.getDouble(5, 1.0);
            vswlo = rec.getDouble(6, 1.0);
            swreg = rec.getInt(7, 0);
            rmpct = rec.getDouble(9, 100.0);
            remoteDeviceId = rec.getString(10, "").trim();
            binit = rec.getDouble(11, 0.0);
        } else if (version >= 33) {
            // v33-34: I, MODSW, ADJM, ST, VSWHI, VSWLO, SWREG, RMPCT, RMIDNT, BINIT, N1, B1, ...
            modsw = rec.getInt(1, 1);
            stat = rec.getInt(3, 1);
            vswhi = rec.getDouble(4, 1.0);
            vswlo = rec.getDouble(5, 1.0);
            swreg = rec.getInt(6, 0);
            rmpct = rec.getDouble(7, 100.0);
            remoteDeviceId = rec.getString(8, "").trim();
            binit = rec.getDouble(9, 0.0);
        } else {
            // v30: I, MODSW, VSWHI, VSWLO, SWREM, RMPCT, RMIDNT, BINIT, N1, B1, ...
            modsw = rec.getInt(1, 1);
            stat = 1;
            vswhi = rec.getDouble(2, 1.0);
            vswlo = rec.getDouble(3, 1.0);
            swreg = rec.getInt(4, 0);
            rmpct = rec.getDouble(5, 100.0);
            remoteDeviceId = rec.getString(6, "").trim();
            binit = rec.getDouble(7, 0.0);
        }

        AclfAdjustControlMode mode = switchedShuntControlMode(modsw);

        String remoteBusId = (swreg > 0 && swreg != busNum) ? BUS_ID_PREFIX + swreg : null;

        List<ShuntBlock> blocks = new ArrayList<>();
        if (version >= 35) {
            // v35+: S, N, B triples; v36 has NAME field before blocks
            int blockStartIdx = version >= 36 ? 13 : 12;
            for (int i = 0; i < 8; i++) {
                int idx = blockStartIdx + i * 3;
                if (idx + 2 >= rec.size()) break;
                int s = rec.getInt(idx, 0);
                int n = rec.getInt(idx + 1, 0);
                double bVal = rec.getDouble(idx + 2, 0.0);
                if (n > 0 || bVal != 0.0) {
                    blocks.add(new ShuntBlock(n, bVal, s == 1));
                }
            }
        } else {
            // v30-34: N, B pairs
            int blockStartIdx = version >= 33 ? 10 : 8;
            for (int i = 0; i < 8; i++) {
                int n = rec.getInt(blockStartIdx + i * 2, 0);
                double bVal = rec.getDouble(blockStartIdx + i * 2 + 1, 0.0);
                if (n > 0 || bVal != 0.0) {
                    blocks.add(new ShuntBlock(n, bVal, true));
                }
            }
        }

        double bInitPU = binit / baseMva;

        SwitchedShunt switchedShunt = builder.addSwitchedShunt(busId, shuntId, stat == 1,
                mode, AclfAdjustControlType.RANGE_CONTROL,
                bInitPU, vswhi, vswlo, remoteBusId, rmpct, blocks);
        if (modsw == 6 && switchedShunt != null) {
            // PSS/E MODSW=6 regulates the reactive output of the named FACTS
            // shunt element. Preserve that association for the coordinated
            // outer controller; VSWHI/VSWLO are fractions of FACTS Q range,
            // not bus-voltage limits.
            switchedShunt.setRemoteControlGroupId(remoteDeviceId);
        }
        applyNameTagMetadata(rec, switchedShunt);
    }

    static AclfAdjustControlMode switchedShuntControlMode(int modsw) {
        // Only MODSW=1/2 regulate voltage. MODSW=3..6 are supervisory
        // controls for another device's reactive output (plant, VSC dc,
        // switched shunt, or FACTS shunt element). Mode 6 is represented by
        // the named FACTS-output outer control; preserve the solved BINIT for
        // the other unsupported modes. Treating their VSWHI/VSWLO fields as a
        // voltage band creates a conflicting voltage controller.
        if (modsw == 1) return AclfAdjustControlMode.DISCRETE;
        if (modsw == 2) return AclfAdjustControlMode.CONTINUOUS;
        if (modsw == 6) return AclfAdjustControlMode.DISCRETE;
        return AclfAdjustControlMode.FIXED;
    }

    private static PSSEDataRec firstNameTagMetadata(PSSEDataRec... records) {
        for (PSSEDataRec record : records) {
            if (record != null && record.hasNameTagMetadata()) return record;
        }
        return null;
    }

    private static <T extends NameTag> T applyNameTagMetadata(PSSEDataRec record, T device) {
        if (record == null || device == null || !record.hasNameTagMetadata()) return device;
        device.setDesc(record.getNameTagDescription());
        if (record.getExternalUid() != null) device.setExtUID(record.getExternalUid());
        return device;
    }

    // ==================== HVDC 2T LCC ====================

    @SuppressWarnings("unchecked")
    private void parseHvdc2TLCCLines(List<String> lines) throws InterpssException {
        if (lines.size() < 3) return;

        PSSEDataRec line1 = new PSSEDataRec(lines.get(0));
        PSSEDataRec line2 = new PSSEDataRec(lines.get(1));
        PSSEDataRec line3 = new PSSEDataRec(lines.get(2));

        // Line 1: NAME, MDC, RDC, SETVL, VSCHD, VCMOD, RCOMP, DELTI, METER, DCVMIN, CCCITMX, CCCACC
        String name = line1.getString(0, "");
        int mdc = line1.getInt(1, 0); // 0=blocked, 1=power, 2=current
        double rdc = line1.getDouble(2, 0.0);
        double setvl = line1.getDouble(3, 0.0);
        double vschd = line1.getDouble(4, 0.0);
        double rcomp = line1.getDouble(6, 0.0);
        double delti = line1.getDouble(7, 0.0);
        String meter = line1.getString(8, "I");

        HvdcControlMode controlMode = mdc == 1 ? HvdcControlMode.DC_POWER :
                mdc == 2 ? HvdcControlMode.DC_CURRENT : HvdcControlMode.BLOCKED;
        // when SETVL == 0, the dc line operates as blocked (matches PSS/E behavior)
        if (Math.abs(setvl) < 1.0E-3)
            controlMode = HvdcControlMode.BLOCKED;

        // v30-33 place XCAP at field 16; v34 appends NDR after XCAP. In
        // v35+, NDR moves before IFR and shifts XCAP to field 17.
        int xcapIndex = version >= 35 ? 17 : 16;

        // Line 2 (v35+): IPR, NBR, ANMXR, ANMNR, RCR, XCR, EBASR, TRR, TAPR,
        // TMXR, TMNR, STPR, ICR, NDR, IFR, ITR, IDR, XCAPR
        int recBusNum = line2.getInt(0);
        int nbr = line2.getInt(1);
        double anmxr = line2.getDouble(2, 0.0);
        double anmnr = line2.getDouble(3, 0.0);
        double rcr = line2.getDouble(4, 0.0);
        double xcr = line2.getDouble(5, 0.0);
        double ebasr = line2.getDouble(6, 0.0);
        double trr = line2.getDouble(7, 1.0);
        double tapr = line2.getDouble(8, 1.0);
        double tmxr = line2.getDouble(9, 1.5);
        double tmnr = line2.getDouble(10, 0.51);
        double stpr = line2.getDouble(11, 0.00625);
        double xcapr = line2.getDouble(xcapIndex, 0.0);

        // Line 3 (v35+): IPI, NBI, ANMXI, ANMNI, RCI, XCI, EBASI, TRI, TAPI,
        // TMXI, TMNI, STPI, ICI, NDI, IFI, ITI, IDI, XCAPI
        int invBusNum = line3.getInt(0);
        int nbi = line3.getInt(1);
        double anmxi = line3.getDouble(2, 0.0);
        double anmni = line3.getDouble(3, 0.0);
        double rci = line3.getDouble(4, 0.0);
        double xci = line3.getDouble(5, 0.0);
        double ebasi = line3.getDouble(6, 0.0);
        double tri = line3.getDouble(7, 1.0);
        double tapi = line3.getDouble(8, 1.0);
        double tmxi = line3.getDouble(9, 1.5);
        double tmni = line3.getDouble(10, 0.51);
        double stpi = line3.getDouble(11, 0.00625);
        double xcapi = line3.getDouble(xcapIndex, 0.0);

        String fromBusId = BUS_ID_PREFIX + recBusNum;
        String toBusId = BUS_ID_PREFIX + invBusNum;
        String dcLineId = name.trim().isEmpty() ? fromBusId + "_" + toBusId : name.trim();

        // when MDC = 1, a positive SETVL specifies desired power at the rectifier
        // and a negative value specifies desired inverter power
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

            builder.setLCCRectifier(lcc, nbr, anmnr, anmxr,
                    rcr, xcr, ebasr, trr, tapr, tmxr, tmnr, stpr, xcapr, null);

            builder.setLCCInverter(lcc, nbi, anmni, anmxi,
                    rci, xci, ebasi, tri, tapi, tmxi, tmni, stpi, xcapi, null);
        } catch (Exception e) {
            log.error("Error parsing HVDC 2T LCC record: " + e.getMessage());
        }
    }

    // ==================== HVDC 2T VSC ====================

    private void parseHvdc2TVSCLines(List<String> lines) throws InterpssException {
        if (lines.size() < 3) return;

        PSSEDataRec line1 = new PSSEDataRec(lines.get(0));
        PSSEDataRec line2 = new PSSEDataRec(lines.get(1));
        PSSEDataRec line3 = new PSSEDataRec(lines.get(2));

        // Line 1: NAME, MDC, RDC, O1, F1, ..., O4, F4
        String name = line1.getString(0, "");
        int mdc = line1.getInt(1, 1);
        double rdc = line1.getDouble(2, 0.0);

        // Line 2/3: IBUS, TYPE, MODE, DCSET, ACSET, ALOSS, BLOSS, MINLOSS, SMAX, IMAX, PWF,
        //           MAXQ, MINQ, REMOT, RMPCT              (version < 34)
        //           MAXQ, MINQ, VSREG, NREG, RMPCT        (version >= 34)
        // TYPE: dc control - 0 = blocked, 1 = dc voltage control, 2 = dc power (MW) control
        // MODE: ac control - 1 = ac voltage control, 2 = fixed ac power factor
        int rmpctIdx = version >= 34 ? 15 : 14;

        int bus1Num = line2.getInt(0);
        int type1 = line2.getInt(1, 1);
        int mode1 = line2.getInt(2, 1);
        double dcSet1 = line2.getDouble(3, 0.0);
        double acSet1 = line2.getDouble(4, 1.0);
		double lossA1 = line2.getDouble(5, 0.0);
		double lossB1 = line2.getDouble(6, 0.0);
		double minLoss1 = line2.getDouble(7, 0.0);
        double smax1 = line2.getDouble(8, 0.0);
		double imax1 = line2.getDouble(9, 0.0);
        double maxQ1 = line2.getDouble(11, 9999.0);
        double minQ1 = line2.getDouble(12, -9999.0);
        int remot1 = line2.getInt(13, 0);
        double rmpct1 = line2.getDouble(rmpctIdx, 100.0);

        int bus2Num = line3.getInt(0);
        int type2 = line3.getInt(1, 1);
        int mode2 = line3.getInt(2, 1);
        double dcSet2 = line3.getDouble(3, 0.0);
        double acSet2 = line3.getDouble(4, 1.0);
		double lossA2 = line3.getDouble(5, 0.0);
		double lossB2 = line3.getDouble(6, 0.0);
		double minLoss2 = line3.getDouble(7, 0.0);
        double smax2 = line3.getDouble(8, 0.0);
		double imax2 = line3.getDouble(9, 0.0);
        double maxQ2 = line3.getDouble(11, 9999.0);
        double minQ2 = line3.getDouble(12, -9999.0);
        int remot2 = line3.getInt(13, 0);
        double rmpct2 = line3.getDouble(rmpctIdx, 100.0);

        // Determine which converter is the rectifier. For dc power (MW) control,
        // DCSET is positive when power flows from the converter into the ac network,
        // i.e. a negative DCSET identifies the rectifier side.
        boolean isConv1Rec = (type1 == 2 && dcSet1 < 0) || (type2 == 2 && dcSet2 > 0);

        int recBusNum = isConv1Rec ? bus1Num : bus2Num;
        int invBusNum = isConv1Rec ? bus2Num : bus1Num;

        String fromBusId = BUS_ID_PREFIX + recBusNum;
        String toBusId = BUS_ID_PREFIX + invBusNum;
        String vscId = name.trim().isEmpty() ? fromBusId + "_" + toBusId : name.trim();

        try {
            HvdcLine2TVSC<AclfBus> vsc = builder.addHvdcLine2TVSC(
                    vscId, name, fromBusId, toBusId, mdc != 0, rdc, 0.0);

            VSCConverter recConv = (VSCConverter) vsc.getRecConverter();
            recConv.setId("VSC Rec_" + fromBusId);
            configVSCConverter(recConv, recBusNum,
                    isConv1Rec ? type1 : type2, isConv1Rec ? mode1 : mode2,
                    isConv1Rec ? dcSet1 : dcSet2, isConv1Rec ? acSet1 : acSet2,
					isConv1Rec ? lossA1 : lossA2, isConv1Rec ? lossB1 : lossB2,
					isConv1Rec ? minLoss1 : minLoss2, isConv1Rec ? imax1 : imax2,
                    isConv1Rec ? smax1 : smax2,
                    isConv1Rec ? maxQ1 : maxQ2, isConv1Rec ? minQ1 : minQ2,
                    isConv1Rec ? remot1 : remot2, isConv1Rec ? rmpct1 : rmpct2);

            VSCConverter invConv = (VSCConverter) vsc.getInvConverter();
            invConv.setId("VSC Inv_" + toBusId);
            configVSCConverter(invConv, invBusNum,
                    isConv1Rec ? type2 : type1, isConv1Rec ? mode2 : mode1,
                    isConv1Rec ? dcSet2 : dcSet1, isConv1Rec ? acSet2 : acSet1,
					isConv1Rec ? lossA2 : lossA1, isConv1Rec ? lossB2 : lossB1,
					isConv1Rec ? minLoss2 : minLoss1, isConv1Rec ? imax2 : imax1,
                    isConv1Rec ? smax2 : smax1,
                    isConv1Rec ? maxQ2 : maxQ1, isConv1Rec ? minQ2 : minQ1,
                    isConv1Rec ? remot2 : remot1, isConv1Rec ? rmpct2 : rmpct1);
        } catch (Exception e) {
            log.error("Error parsing VSC HVDC record: " + e.getMessage());
        }
    }

    private void configVSCConverter(VSCConverter converter, int busNum,
                                    int type, int mode, double dcSet, double acSet,
									double lossA, double lossB, double minimumLoss,
									double maximumAcCurrent,
                                    double smax, double maxQ, double minQ,
                                    int remoteBusNum, double rmpct) {
        HvdcControlMode dcCtrl = type == 0 ? HvdcControlMode.BLOCKED :
                type == 1 ? HvdcControlMode.DC_VOLTAGE : HvdcControlMode.DC_POWER;
        VSCAcControlMode acCtrl = mode == 1 ? VSCAcControlMode.AC_VOLTAGE :
                VSCAcControlMode.AC_POWER_FACTOR;
        String remoteBusId = (remoteBusNum > 0 && remoteBusNum != busNum)
                ? BUS_ID_PREFIX + remoteBusNum : null;
        // dc set points are stored as positive values; the converter type defines the direction
        builder.setVSCConverter(converter, BUS_ID_PREFIX + busNum, dcCtrl, Math.abs(dcSet),
                acCtrl, acSet, smax, maxQ, minQ, remoteBusId, rmpct);
		converter.setLossA(lossA);
		converter.setLossB(lossB);
		converter.setMinimumLoss(minimumLoss);
		converter.setAcCurrentRating(maximumAcCurrent);
    }

    // ==================== FACTS Device / SVC ====================

    private void parseFACTSLine(PSSEDataRec rec) throws InterpssException {
        // v30: N, I, J, MODE, PDES, QDES, VSET, SHMX, TRMX, VTMN, VTMX, VSMX, IMX, LINX, RMPCT, OWNER, SET1, SET2, VSREF
        // v31-33: NAME, I, J, MODE, PDES, QDES, VSET, SHMX, TRMX, VTMN, VTMX, VSMX, IMX, LINX, RMPCT, OWNER, SET1, SET2, VSREF, FCREG, MNAME
        // v35: NAME, I, J, MODE, PDES, QDES, VSET, SHMX, TRMX, VTMN, VTMX, VSMX, IMX, LINX, RMPCT, OWNER, SET1, SET2, VSREF, FCREG, NREG, MNAME
        int nameOffset = (version <= 30) ? 0 : 0;
        String name = rec.getString(0, "").trim();
        int busNum = rec.getInt(1);
        int jBus = rec.getInt(2, 0);
        int mode = rec.getInt(3, 1);
        double qdes = rec.getDouble(5, 0.0);
        double vset = rec.getDouble(6, 1.0);
        double shmx = rec.getDouble(7, 9999.0);
        double linx = rec.getDouble(13, 0.05);
        double rmpct = rec.getDouble(14, 100.0);

        int fcreg = 0;
        if (version >= 31 && version < 35) {
            fcreg = rec.getInt(19, 0);
        } else if (version >= 35) {
            fcreg = rec.getInt(19, 0);
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
                // PSS/E QDES is the STATCON's saved reactive output.
                double voltage = bus.getVoltageMag();
                svc.setBInit(qdes / baseMva / (voltage * voltage));
            }
        } else {
            String toBusId = BUS_ID_PREFIX + jBus;
            double pdes = rec.getDouble(4, 0.0);
            double set1 = rec.getDouble(16, 0.0);
            double set2 = rec.getDouble(17, 0.0);

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
                log.error("Error parsing FACTS device: " + e.getMessage());
            }
        }
    }

    // ==================== Multi-Terminal DC ====================

    /**
     * PSS/E multi-terminal DC: for each system, one header + NCONV converters +
     * NDCBS DC buses + NDCLN DC links. Builds {@link HvdcLineMT} on the network
     * MT list and registers the line for NB type-N terminal lookup.
     */
    private void parseMultiTerminalDcSection(BufferedReader reader) throws IOException {
        String line;
        while ((line = readPsseDataLine(reader)) != null) {
            if (PSSEDataRec.isEndRec(line)) break;

            PSSEDataRec header = new PSSEDataRec(line);
            String name = header.getString(0, "").trim();
            if (name.isEmpty() || "0".equals(name)) break;

            int nconv = header.getInt(1, 0);
            int ndcbs = header.getInt(2, 0);
            int ndcln = header.getInt(3, 0);
            int mdc = header.getInt(4, 0);
            int vconv = header.getInt(5, 0);
            double vcmod = header.getDouble(6, 0.0);
            int vconvn = header.getInt(7, 0);

            HvdcControlMode controlMode = mdc == 1 ? HvdcControlMode.DC_POWER
                    : mdc == 2 ? HvdcControlMode.DC_CURRENT : HvdcControlMode.BLOCKED;
            String vConvBusId = vconv > 0 ? BUS_ID_PREFIX + vconv : "";
            String vConvNBusId = vconvn > 0 ? BUS_ID_PREFIX + vconvn : "";

            HvdcLineMT mtLine = builder.addHvdcLineMT(
                    name, controlMode, vConvBusId, vConvNBusId, vcmod, mdc != 0);

            for (int i = 0; i < nconv; i++) {
                line = readPsseDataLine(reader);
                if (line == null || PSSEDataRec.isEndRec(line)) {
                    log.error("MTDC {}: unexpected end while reading converters ({}/{})",
                            name, i, nconv);
                    return;
                }
                parseMultiTerminalDcConverter(mtLine, new PSSEDataRec(line));
            }
            for (int i = 0; i < ndcbs; i++) {
                line = readPsseDataLine(reader);
                if (line == null || PSSEDataRec.isEndRec(line)) {
                    log.error("MTDC {}: unexpected end while reading DC buses ({}/{})",
                            name, i, ndcbs);
                    return;
                }
                parseMultiTerminalDcBus(mtLine, new PSSEDataRec(line));
            }
            for (int i = 0; i < ndcln; i++) {
                line = readPsseDataLine(reader);
                if (line == null || PSSEDataRec.isEndRec(line)) {
                    log.error("MTDC {}: unexpected end while reading DC links ({}/{})",
                            name, i, ndcln);
                    return;
                }
                parseMultiTerminalDcLink(mtLine, new PSSEDataRec(line));
            }

            // NB type-N terminals resolve by MTDC system name
            builder.registerNamedEquipment(name, mtLine);
            builder.registerNamedEquipment("N|" + name, mtLine);

            String topoErr = mtLine.validateTopology();
            if (topoErr != null) {
                log.warn(topoErr);
            }
        }
    }

    private void parseMultiTerminalDcConverter(HvdcLineMT mtLine, PSSEDataRec rec) {
        int ib = rec.getInt(0);
        if (ib <= 0) {
            log.warn("MTDC {}: converter record missing AC bus IB", mtLine.getId());
            return;
        }
        String acBusId = BUS_ID_PREFIX + ib;
        builder.addHvdcMTConverter(mtLine, acBusId,
                rec.getInt(1, 1),
                rec.getDouble(2, 0.0),
                rec.getDouble(3, 0.0),
                rec.getDouble(4, 0.0),
                rec.getDouble(5, 0.0),
                rec.getDouble(6, 0.0),
                rec.getDouble(7, 1.0),
                rec.getDouble(8, 1.0),
                rec.getDouble(9, 1.5),
                rec.getDouble(10, 0.51),
                rec.getDouble(11, 0.00625),
                rec.getDouble(12, 0.0),
                rec.getDouble(13, 1.0),
                rec.getDouble(14, 0.0),
                rec.getInt(15, 1));
    }

    private void parseMultiTerminalDcBus(HvdcLineMT mtLine, PSSEDataRec rec) {
        int idc = rec.getInt(0);
        int ib = rec.getInt(1, 0);
        String acBusId = ib > 0 ? BUS_ID_PREFIX + ib : "";
        builder.addHvdcMTDcBus(mtLine, idc, acBusId,
                rec.getInt(2, 1),
                rec.getInt(3, 1),
                rec.getString(4, "").trim(),
                rec.getInt(5, 0),
                rec.getDouble(6, 0.0),
                rec.getInt(7, 1));
    }

    /**
     * DC link: v33+ is {@code IDC,JDC,CKT,MET,RDC,LDC}; older RAW (e.g. v30)
     * omits MET ({@code IDC,JDC,CKT,RDC,LDC}).
     */
    private void parseMultiTerminalDcLink(HvdcLineMT mtLine, PSSEDataRec rec) {
        int fromIdc = rec.getInt(0);
        int toIdc = rec.getInt(1);
        String ckt = rec.getString(2, "1").trim();
        int met;
        double rdc;
        double ldc;
        if (rec.size() >= 6) {
            met = rec.getInt(3, 1);
            rdc = rec.getDouble(4, 0.0);
            ldc = rec.getDouble(5, 0.0);
        } else {
            met = 1;
            rdc = rec.getDouble(3, 0.0);
            ldc = rec.getDouble(4, 0.0);
        }
        builder.addHvdcMTDcLink(mtLine, fromIdc, toIdc, ckt, met, rdc, ldc);
    }

    private static String readPsseDataLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("//") || line.startsWith("@!")) continue;
            return line;
        }
        return null;
    }

    // ==================== Induction Machine ====================

    /**
     * Register induction machines as NameTags for NB type-I terminals.
     * Full IM ACLF model is not built here (avoids colliding with load id "1" on the same bus).
     */
    private void parseInductionMachineLine(PSSEDataRec rec) {
        int busNum = rec.getInt(0);
        String id = rec.getString(1, "1").trim();
        if (id.isEmpty()) {
            id = "1";
        }
        String name = rec.getString(8, "").trim();
        String busId = BUS_ID_PREFIX + busNum;

        NameTag tag = NetFactory.eINSTANCE.createNameTag();
        tag.setId(id);
        tag.setName(name.isEmpty() ? id : name);
        builder.registerNamedEquipment("I|" + busId + "|" + id, tag);
    }

    // ==================== Xfr Z Correction Table ====================

    private void parseXfrZCorrSection(BufferedReader reader) throws IOException, InterpssException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("//") || line.startsWith("@!")) continue;
            if (PSSEDataRec.isEndRec(line)) break;

            StringBuilder recordStr = new StringBuilder(line);
            if (version >= 34) {
                while (!isZCorrComplete(recordStr.toString())) {
                    String next = reader.readLine();
                    if (next == null) break;
                    recordStr.append(",").append(next.trim());
                }
            }
            parseXfrZCorrLine(recordStr.toString());
        }
    }

    private void parseXfrZCorrLine(String lineStr) {
        PSSEDataRec rec = new PSSEDataRec(lineStr);
        int tableNum = rec.getInt(0);
        if (tableNum <= 0) return;

        List<XfrZCorrection> points = new ArrayList<>();
        if (version >= 34) {
            for (int i = 1; i + 2 < rec.size(); i += 3) {
                double t = rec.getDouble(i, 0.0);
                double reF = rec.getDouble(i + 1, 0.0);
                double imF = rec.getDouble(i + 2, 0.0);
                if (t == 0.0 && reF == 0.0 && imF == 0.0) break;
                points.add(new XfrZCorrection(t, new Complex(reF, imF)));
            }
        } else {
            for (int i = 1; i + 1 < rec.size(); i += 2) {
                double t = rec.getDouble(i, 0.0);
                double f = rec.getDouble(i + 1, 0.0);
                if (t == 0.0 && f == 0.0) break;
                points.add(new XfrZCorrection(t, f));
            }
        }

        if (!points.isEmpty()) {
            builder.addXfrZTableEntry(tableNum, points);
        }
    }

    private boolean isZCorrComplete(String lineStr) {
        String[] vals = lineStr.trim().split(",");
        if (vals.length < 3) return false;
        try {
            double secondLast = Double.parseDouble(vals[vals.length - 2].trim());
            double last = Double.parseDouble(vals[vals.length - 1].trim());
            return Math.abs(secondLast) < 0.00001 && Math.abs(last) < 0.00001;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
