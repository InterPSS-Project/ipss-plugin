/*
 * @(#)AclfNetworkBuilder.java
 *
 * Copyright (C) 2006-2025 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * @Author Mike Zhou
 * @Version 2.0
 * @Date 07/15/2025
 */

package org.interpss.fadapter.builder;

import java.util.List;
import java.util.Optional;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.datatype.XfrZCorrection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.HvdcObjectFactory;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.core.aclf.ShuntCompensatorType;
import com.interpss.core.aclf.XfrZTableEntry;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.BusBranchControlType;
import com.interpss.core.aclf.adj.PQBusLimit;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adpter.Aclf3WPSXformerAdapter;
import com.interpss.core.aclf.adpter.Aclf3WXformerAdapter;
import com.interpss.core.aclf.adpter.AclfLineAdapter;
import com.interpss.core.aclf.adpter.AclfPQGenBusAdapter;
import com.interpss.core.aclf.adpter.AclfPSXformerAdapter;
import com.interpss.core.aclf.adpter.AclfPVGenBusAdapter;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.aclf.adpter.AclfXformerAdapter;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.aclf.flow.FlowInterface;
import com.interpss.core.aclf.flow.FlowInterfaceBranch;
import com.interpss.core.aclf.flow.FlowInterfaceLimit;
import com.interpss.core.aclf.flow.FlowInterfaceType;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcControlSide;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.ThyConverter;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.net.Area;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.core.net.Owner;
import com.interpss.core.net.Zone;

/**
 * Shared builder for constructing AclfNetwork objects directly from parsed file data,
 * bypassing the ODM XML intermediate representation.
 *
 * All parameters use primitive/simple Java types (double, String, Complex, enums)
 * with no dependency on org.ieee.odm schema types.
 *
 * The model-construction logic is extracted from AclfBusDataHelper, AclfBranchDataHelper,
 * AclfHvdcDataHelper, and AbstractODMAclfNetMapper.
 */
public class AclfNetworkBuilder {
    private static final Logger log = LoggerFactory.getLogger(AclfNetworkBuilder.class);

    private final AclfNetwork network;

    public AclfNetworkBuilder() {
        this.network = CoreObjectFactory.createAclfNetwork();
    }

    public AclfNetwork getNetwork() {
        return network;
    }

    // ==================== Network Level ====================

    /**
     * Set network-level metadata.
     */
    public void setNetworkInfo(String id, String name, double baseKva, OriginalDataFormat format) {
        network.setId(id);
        network.setName(name);
        network.setBaseKva(baseKva);
        network.setOriginalDataFormat(format);
        if (format == OriginalDataFormat.IEEECDF) {
            network.setContributeGenLoadModel(true);
        }
    }

    public void setDefaultVoltageLimit(double vMax, double vMin) {
        network.setDefaultVoltageLimit(new LimitType(vMax, vMin));
    }

    public void ensureCapacity(int busCnt, int branchCnt) {
        network.ensureLookupCapacity(busCnt, branchCnt);
    }

    // ==================== Area / Zone / Owner ====================

    public Area addArea(String id, String name, String desc) {
        Area area = network.getArea(id);
        if (area == null) {
            area = CoreObjectFactory.createArea(id, network);
        }
        area.setName(name != null ? name : "Area");
        area.setDesc(desc != null ? desc : "Area Desc");
        return area;
    }

    public Zone addZone(String id, String name, String desc) {
        Zone zone = network.getZone(id);
        if (zone == null) {
            zone = CoreObjectFactory.createZone(id, network);
        }
        zone.setName(name != null ? name : "Zone");
        zone.setDesc(desc != null ? desc : "Zone Desc");
        return zone;
    }

    public Owner addOwner(String id, String name) {
        Owner owner = network.getOwner(id);
        if (owner == null) {
            owner = CoreObjectFactory.createOwner(id, network);
        }
        if (name != null) owner.setName(name);
        return owner;
    }

    // ==================== Xfr Z Correction Table ====================

    /**
     * Add an impedance correction table entry.
     * @param number table number
     * @param adjustSide FROM_SIDE or TO_SIDE
     * @param points list of (turnRatioOrAngle, scaleFactor) pairs
     */
    public void setXfrZTableAdjustSide(BranchBusSide side) {
        network.setXfrZAdjustSide(side);
    }

    public void addXfrZTableEntry(int number, List<XfrZCorrection> points) {
        Optional<XfrZTableEntry> elemOpt = CoreObjectFactory.createXfrZTableEntry(number, network);
        if (elemOpt.isPresent()) {
            XfrZTableEntry elem = elemOpt.get();
            for (XfrZCorrection pt : points) {
                elem.getPointSet().getPoints().add(pt);
            }
        }
    }

    // ==================== Bus ====================

    /**
     * Add a bus to the network.
     * @param id bus ID
     * @param name bus name
     * @param number bus number
     * @param baseVoltageV base voltage in volts
     * @param voltagePU initial voltage magnitude in PU
     * @param angleRad initial voltage angle in radians
     * @param areaId area ID (null if none)
     * @param zoneId zone ID (null if none)
     * @param ownerId owner ID (null if none)
     * @return the created bus
     */
    public AclfBus addBus(String id, String name, long number, double baseVoltageV,
                          double voltagePU, double angleRad,
                          String areaId, String zoneId, String ownerId) throws InterpssException {
        Optional<AclfBus> busOpt = CoreObjectFactory.createAclfBus(id, network);
        if (!busOpt.isPresent()) {
            throw new InterpssException("Failed to create bus: " + id);
        }
        AclfBus bus = busOpt.get();
        if (name != null) bus.setName(name);
        bus.setNumber(number);
        bus.setBaseVoltage(baseVoltageV);
        bus.setVoltage(voltagePU, angleRad);

        if (areaId != null) {
            Area area = network.getArea(areaId);
            if (area != null) bus.setArea(area);
        }
        if (zoneId != null) {
            Zone zone = network.getZone(zoneId);
            if (zone != null) bus.setZone(zone);
        }
        if (ownerId != null) {
            Owner owner = network.getOwner(ownerId);
            if (owner != null) bus.setOwner(owner);
        }
        return bus;
    }

    /**
     * Set voltage limits on a bus.
     */
    public void setBusVoltageLimit(String busId, double vMaxPU, double vMinPU) {
        AclfBus bus = network.getBus(busId);
        if (bus != null) {
            bus.setVLimit(new LimitType(vMaxPU, vMinPU));
        }
    }

    // ==================== Generator ====================

    /**
     * Configure bus-level gen code for a swing bus.
     * @param busId bus ID
     * @param desiredVPU desired voltage magnitude in PU
     * @param desiredAngRad desired voltage angle in radians
     */
    public void setSwingBus(String busId, double desiredVPU, double desiredAngRad) {
        AclfBus bus = network.getBus(busId);
        if (bus == null) return;
        bus.setGenCode(AclfGenCode.SWING);
        AclfSwingBusAdapter swing = bus.toSwingBus();
        swing.setDesiredVoltMag(desiredVPU, UnitType.PU);
        swing.setDesiredVoltAng(desiredAngRad, UnitType.Rad);
    }

    /**
     * Configure bus-level gen code for a PV bus.
     * @param busId bus ID
     * @param pGenPU active power generation in PU
     * @param desiredVPU desired voltage magnitude in PU
     * @param qMaxPU max reactive power in PU (for Q limits)
     * @param qMinPU min reactive power in PU (for Q limits)
     * @param qLimitActive whether Q limit is active
     */
    public void setPVBus(String busId, double pGenPU, double desiredVPU,
                         double qMaxPU, double qMinPU, boolean qLimitActive) {
        AclfBus bus = network.getBus(busId);
        if (bus == null) return;
        bus.setGenCode(AclfGenCode.GEN_PV);
        AclfPVGenBusAdapter pvBus = bus.toPVBus();
        pvBus.setGenP(pGenPU, UnitType.PU);
        pvBus.setDesiredVoltMag(desiredVPU, UnitType.PU);

        if (qMaxPU != 0.0 || qMinPU != 0.0) {
            PVBusLimit pvLimit = AclfAdjustObjectFactory.createPVBusLimit(bus);
            pvLimit.setQLimit(new LimitType(qMaxPU, qMinPU), UnitType.PU);
            pvLimit.setStatus(qLimitActive);
        }
    }

    /**
     * Configure bus-level gen code for a PQ bus.
     */
    public void setPQBus(String busId, double pGenPU, double qGenPU,
                         double vMaxPU, double vMinPU) {
        AclfBus bus = network.getBus(busId);
        if (bus == null) return;
        bus.setGenCode(AclfGenCode.GEN_PQ);
        AclfPQGenBusAdapter pqBus = bus.toPQBus();
        pqBus.setGen(new Complex(pGenPU, qGenPU), UnitType.PU);
        if (vMaxPU != 0.0 || vMinPU != 0.0) {
            PQBusLimit pqLimit = AclfAdjustObjectFactory.createPQBusLimit(bus).get();
            pqLimit.setVLimit(new LimitType(vMaxPU, vMinPU), UnitType.PU);
        }
    }

    /**
     * Set bus gen code to non-gen.
     */
    public void setNonGenBus(String busId) {
        AclfBus bus = network.getBus(busId);
        if (bus != null) bus.setGenCode(AclfGenCode.NON_GEN);
    }

    /**
     * Add a contributing generator to a bus. All values in PU on system MVA base.
     */
    public AclfGen addContributeGen(String busId, String genId, boolean status,
                                    double pGenPU, double qGenPU, double mvaBase,
                                    double desiredVPU,
                                    double qMaxPU, double qMinPU,
                                    double pMaxPU, double pMinPU,
                                    Complex sourceZ, Complex xfrZ, double xfrTap,
                                    String remoteVControlBusId,
                                    double mvarPFactor, double mwPFactor) {
        AclfBus bus = network.getBus(busId);
        if (bus == null) return null;

        AclfGen gen = CoreObjectFactory.createAclfGen(genId);
        bus.getContributeGenList().add(gen);

        gen.setStatus(status);
        gen.setMvaBase(mvaBase);
        gen.setGen(new Complex(pGenPU, qGenPU));
        gen.setDesiredVoltMag(desiredVPU);

        if (qMaxPU != 0.0 || qMinPU != 0.0) {
            gen.setQGenLimit(new LimitType(qMaxPU, qMinPU));
        }
        if (pMaxPU != 0.0 || pMinPU != 0.0) {
            gen.setPGenLimit(new LimitType(pMaxPU, pMinPU));
        }

        if (sourceZ != null) gen.setSourceZ(sourceZ);
        if (xfrZ != null && (xfrZ.getReal() != 0.0 || xfrZ.getImaginary() != 0.0)) {
            gen.setXfrZ(xfrZ);
            gen.setXfrTap(xfrTap != 0.0 ? xfrTap : 1.0);
        }

        if (remoteVControlBusId != null && !remoteVControlBusId.isEmpty()) {
            gen.setRemoteVControlBusId(remoteVControlBusId);
        }

        gen.setMvarControlPFactor(mvarPFactor);
        gen.setMwControlPFactor(mwPFactor);

        return gen;
    }

    // ==================== Load ====================

    /**
     * Set bus load code to non-load.
     */
    public void setNonLoadBus(String busId) {
        AclfBus bus = network.getBus(busId);
        if (bus != null) bus.setLoadCode(AclfLoadCode.NON_LOAD);
    }

    /**
     * Add a contributing load to a bus. All power values in PU on system MVA base.
     */
    public AclfLoad addContributeLoad(String busId, String loadId, boolean status,
                                      Complex constPPU, Complex constIPU, Complex constZPU,
                                      Complex distGenPowerPU, boolean distGenStatus) {
        AclfBus bus = network.getBus(busId);
        if (bus == null) return null;

        AclfLoad load = CoreObjectFactory.createAclfLoad(loadId);
        bus.getContributeLoadList().add(load);
        load.setStatus(status);
        bus.setLoadCode(AclfLoadCode.CONST_P);

        AclfLoadCode code = AclfLoadCode.NON_LOAD;
        if (constPPU != null && constPPU.abs() > 0) {
            code = code == AclfLoadCode.NON_LOAD ? AclfLoadCode.CONST_P : AclfLoadCode.ZIP;
            load.setLoadCP(constPPU);
        }
        if (constIPU != null && constIPU.abs() > 0) {
            code = code == AclfLoadCode.NON_LOAD ? AclfLoadCode.CONST_I : AclfLoadCode.ZIP;
            load.setLoadCI(constIPU);
        }
        if (constZPU != null && constZPU.abs() > 0) {
            code = code == AclfLoadCode.NON_LOAD ? AclfLoadCode.CONST_Z : AclfLoadCode.ZIP;
            load.setLoadCZ(constZPU);
        }
        load.setCode(code);

        if (distGenPowerPU != null && distGenPowerPU.abs() > 0) {
            load.setDistGenPower(distGenPowerPU);
            load.setDistGenStatus(distGenStatus);
        }

        return load;
    }

    // ==================== Fixed Shunt ====================

    /**
     * Set the equivalent shunt admittance on a bus (in PU on system base).
     */
    public void setBusShuntY(String busId, Complex yShuntPU) {
        AclfBus bus = network.getBus(busId);
        if (bus != null && yShuntPU != null) {
            bus.setShuntY(yShuntPU);
        }
    }

    /**
     * Add to the equivalent shunt admittance on a bus (in PU on system base).
     */
    public void addToBusShuntY(String busId, Complex yShuntPU) {
        AclfBus bus = network.getBus(busId);
        if (bus != null && yShuntPU != null) {
            Complex existing = bus.getShuntY();
            bus.setShuntY(existing.add(yShuntPU));
        }
    }

    // ==================== Switched Shunt ====================

    /**
     * Add a switched shunt to a bus.
     * @param busId bus ID
     * @param shuntId shunt ID
     * @param status on/off
     * @param mode CONTINUOUS, DISCRETE, or FIXED
     * @param controlType POINT_CONTROL or RANGE_CONTROL
     * @param bInitPU initial B in PU
     * @param vHiPU voltage high limit in PU (for range control)
     * @param vLoPU voltage low limit in PU (for range control)
     * @param remoteBusId remote controlled bus ID (null = local)
     * @param blocks list of (steps, qMvar) pairs for shunt blocks
     */
    public SwitchedShunt addSwitchedShunt(String busId, String shuntId, boolean status,
                                          AclfAdjustControlMode mode, AclfAdjustControlType controlType,
                                          double bInitPU,
                                          double vHiPU, double vLoPU,
                                          String remoteBusId,
                                          List<ShuntBlock> blocks) {
        AclfBus bus = network.getBus(busId);
        if (bus == null) return null;

        SwitchedShunt swchShunt = AclfAdjustObjectFactory.createSwitchedShunt(bus);
        swchShunt.setId(shuntId != null && !shuntId.isEmpty() ? shuntId : "1");
        swchShunt.setStatus(status);
        swchShunt.setBInit(bInitPU);
        swchShunt.setControlMode(mode);
        swchShunt.setAdjControlType(controlType);

        if (vHiPU != 0.0 || vLoPU != 0.0) {
            swchShunt.setDesiredControlRange(new LimitType(vHiPU, vLoPU));
        }

        double bmin = 0, bmax = 0;
        int i = 1;
        if (blocks != null) {
            for (ShuntBlock block : blocks) {
                ShuntCompensator varBank = CoreObjectFactory.createShuntCompensator(
                        "QBank-" + i++, ShuntCompensatorType.CAPACITOR);
                swchShunt.getShuntCompensatorList().add(varBank);
                varBank.setSteps(block.steps());
                varBank.setUnitQMvar(block.qMvar());
                varBank.setStatus(block.active());

                if (varBank.isActive()) {
                    if (block.qMvar() < 0) {
                        bmin += block.steps() * block.qMvar() / 100.0;
                    } else {
                        bmax += block.steps() * block.qMvar() / 100.0;
                    }
                }
                varBank.calTotalB(network.getBaseKva());
            }
        }
        swchShunt.setBLimit(new LimitType(bmax, bmin));

        if (remoteBusId != null && !remoteBusId.isEmpty()) {
            swchShunt.setRemoteBusBranchId(remoteBusId);
        } else {
            swchShunt.setRemoteBusBranchId(busId);
            swchShunt.setRemoteBus(bus);
        }

        return swchShunt;
    }

    /**
     * Immutable record for a switched shunt block.
     * @param steps number of steps
     * @param qMvar MVAr per step (positive=capacitive, negative=inductive)
     * @param active whether the block is active
     */
    public record ShuntBlock(int steps, double qMvar, boolean active) {}

    // ==================== SVC / STATCOM ====================

    /**
     * Add a Static Var Compensator to a bus.
     * @param busId bus ID
     * @param svcId SVC ID
     * @param status on/off
     * @param qMaxPU max reactive power limit (capacitive, positive) in PU
     * @param qMinPU min reactive power limit (inductive, negative) in PU
     * @param vSetpointPU voltage setpoint in PU
     * @param remoteBusId remote controlled bus ID (null = local)
     * @param remoteControlPercent remote control percentage (100 = default)
     */
    public StaticVarCompensator addSVC(String busId, String svcId, boolean status,
                                       double qMaxPU, double qMinPU,
                                       double vSetpointPU,
                                       String remoteBusId, double remoteControlPercent) {
        AclfBus bus = network.getBus(busId);
        if (bus == null) return null;

        Optional<StaticVarCompensator> svcOpt = AclfAdjustObjectFactory.createStaticVarCompensator(bus);
        if (!svcOpt.isPresent()) return null;

        StaticVarCompensator svc = svcOpt.get();
        svc.setId(svcId);
        svc.setStatus(status);
        svc.setBLimit(new LimitType(qMaxPU, qMinPU));
        svc.setControlMode(AclfAdjustControlMode.CONTINUOUS);
        svc.setRemoteQControlType(BusBranchControlType.BUS_VOLTAGE);
        svc.setVSpecified(vSetpointPU, UnitType.PU);
        svc.setDesiredControlRange(new LimitType(vSetpointPU + 0.0001, vSetpointPU - 0.0001));

        if (remoteBusId != null && !remoteBusId.isEmpty() && !remoteBusId.equals(busId)) {
            svc.setRemoteBusBranchId(remoteBusId);
            if (bus.getGenCode() != AclfGenCode.GEN_PQ) {
                bus.setGenCode(AclfGenCode.GEN_PQ);
            }
        } else {
            svc.setRemoteBusBranchId(busId);
            svc.setRemoteBus(bus);
            if (bus.getGenCode() != AclfGenCode.GEN_PV) {
                bus.setGenCode(AclfGenCode.GEN_PV);
            }
        }
        svc.setRemoteControlPercentage(remoteControlPercent > 0 ? remoteControlPercent : 100.0);
        return svc;
    }

    // ==================== Line Branch ====================

    /**
     * Add a transmission line branch.
     * @param fromBusId from bus ID
     * @param toBusId to bus ID
     * @param cirId circuit ID
     * @param zPU series impedance R+jX in PU
     * @param halfShuntYPU half of total charging B/2 in PU (imaginary part)
     * @param fromShuntYPU from-side shunt admittance in PU (null if none)
     * @param toShuntYPU to-side shunt admittance in PU (null if none)
     * @param ratingMva1 MVA rating 1
     * @param ratingMva2 MVA rating 2
     * @param ratingMva3 MVA rating 3
     * @param status on/off
     * @return the created branch
     */
    public AclfBranch addLine(String fromBusId, String toBusId, String cirId,
                              Complex zPU, Complex halfShuntYPU,
                              Complex fromShuntYPU, Complex toShuntYPU,
                              double ratingMva1, double ratingMva2, double ratingMva3,
                              boolean status) throws InterpssException {
        AclfBranch bra = CoreObjectFactory.createAclfBranch();
        network.addBranch(bra, fromBusId, toBusId, cirId);
        bra.setStatus(status);
        bra.setBranchCode(AclfBranchCode.LINE);

        AclfLineAdapter line = bra.toLine();
        bra.setZ(zPU);
        if (halfShuntYPU != null) {
            bra.setHShuntY(halfShuntYPU);
        }
        if (fromShuntYPU != null) {
            bra.setFromShuntY(fromShuntYPU);
        }
        if (toShuntYPU != null) {
            bra.setToShuntY(toShuntYPU);
        }

        bra.setRatingMva1(ratingMva1);
        bra.setRatingMva2(ratingMva2);
        bra.setRatingMva3(ratingMva3);
        return bra;
    }

    /**
     * Add a breaker or zero-impedance branch.
     */
    public AclfBranch addBreaker(String fromBusId, String toBusId, String cirId,
                                 Complex zPU, boolean status, AclfBranchCode code) throws InterpssException {
        AclfBranch bra = CoreObjectFactory.createAclfBranch();
        network.addBranch(bra, fromBusId, toBusId, cirId);
        bra.setStatus(status);
        bra.setBranchCode(code);
        bra.setZ(zPU);
        return bra;
    }

    // ==================== 2-Winding Transformer ====================

    /**
     * Add a 2-winding transformer.
     *
     * @param fromBusId from bus ID
     * @param toBusId to bus ID
     * @param cirId circuit ID
     * @param zPU impedance in PU on system base (already adjusted for rated power if needed)
     * @param fromTapPU from-side tap ratio in PU
     * @param toTapPU to-side tap ratio in PU
     * @param magYFromSide magnetizing admittance on from side (null if none), in PU
     * @param magYToSide magnetizing admittance on to side (null if none), in PU
     * @param ratingMva1 MVA rating 1
     * @param ratingMva2 MVA rating 2
     * @param ratingMva3 MVA rating 3
     * @param zTableNumber xfr Z correction table number (0 = none)
     * @param status on/off
     * @return the created branch
     */
    public AclfBranch addXformer2W(String fromBusId, String toBusId, String cirId,
                                   Complex zPU, double fromTapPU, double toTapPU,
                                   Complex magYFromSide, Complex magYToSide,
                                   double ratingMva1, double ratingMva2, double ratingMva3,
                                   int zTableNumber, boolean status) throws InterpssException {
        AclfBranch bra = CoreObjectFactory.createAclfBranch();
        network.addBranch(bra, fromBusId, toBusId, cirId);
        bra.setStatus(status);
        bra.setBranchCode(AclfBranchCode.XFORMER);
        bra.setNetwork(network);

        AclfXformerAdapter xfr = bra.toXfr();
        bra.setZ(zPU);
        xfr.setFromTurnRatio(fromTapPU, UnitType.PU);
        xfr.setToTurnRatio(toTapPU, UnitType.PU);

        if (magYFromSide != null) bra.setFromShuntY(magYFromSide);
        if (magYToSide != null) bra.setToShuntY(magYToSide);

        bra.setRatingMva1(ratingMva1);
        bra.setRatingMva2(ratingMva2);
        bra.setRatingMva3(ratingMva3);

        if (zTableNumber > 0) bra.setXfrZTableNumber(zTableNumber);
        return bra;
    }

    // ==================== Tap Control ====================

    /**
     * Add voltage-controlling tap adjustment to a transformer branch.
     */
    public TapControl addTapVoltageControl(String branchId, AclfAdjustControlType controlType,
                                           String vcBusId, boolean status,
                                           double desiredV, UnitType vUnit,
                                           double tapMax, double tapMin,
                                           boolean controlOnFromSide, boolean vcBusOnFromSide,
                                           Double stepSize, Integer steps) {
        AclfBranch bra = network.getBranch(branchId);
        if (bra == null) return null;

        Optional<TapControl> tapOpt = AclfAdjustObjectFactory.createTapVControlBusVoltage(
                bra, controlType, network, vcBusId);
        if (!tapOpt.isPresent()) return null;

        TapControl tap = tapOpt.get();
        tap.setStatus(status);
        if (controlType == AclfAdjustControlType.POINT_CONTROL) {
            tap.setVSpecified(desiredV, vUnit);
        } else {
            tap.setDesiredControlRange(new LimitType(desiredV, 0)); // overridden below for range
        }
        tap.setTurnRatioLimit(new LimitType(tapMax, tapMin));
        tap.setControlOnFromSide(controlOnFromSide);
        tap.setVcBusOnFromSide(vcBusOnFromSide);
        if (stepSize != null) tap.setTapStepSize(stepSize);
        if (steps != null) tap.setTapSteps(steps);
        return tap;
    }

    /**
     * Add voltage-controlling tap with range control.
     */
    public TapControl addTapVoltageRangeControl(String branchId, String vcBusId, boolean status,
                                                double rangeMax, double rangeMin,
                                                double tapMax, double tapMin,
                                                boolean controlOnFromSide, boolean vcBusOnFromSide,
                                                Double stepSize, Integer steps) {
        AclfBranch bra = network.getBranch(branchId);
        if (bra == null) return null;

        Optional<TapControl> tapOpt = AclfAdjustObjectFactory.createTapVControlBusVoltage(
                bra, AclfAdjustControlType.RANGE_CONTROL, network, vcBusId);
        if (!tapOpt.isPresent()) return null;

        TapControl tap = tapOpt.get();
        tap.setStatus(status);
        tap.setDesiredControlRange(new LimitType(rangeMax, rangeMin));
        tap.setTurnRatioLimit(new LimitType(tapMax, tapMin));
        tap.setControlOnFromSide(controlOnFromSide);
        tap.setVcBusOnFromSide(vcBusOnFromSide);
        if (stepSize != null) tap.setTapStepSize(stepSize);
        if (steps != null) tap.setTapSteps(steps);
        return tap;
    }

    /**
     * Add Mvar-flow controlling tap adjustment.
     */
    public TapControl addTapMvarControl(String branchId, AclfAdjustControlType controlType,
                                        boolean status, double desiredMvar, UnitType mvarUnit,
                                        double tapMax, double tapMin,
                                        boolean controlOnFromSide, boolean meteredOnFromSide,
                                        Double stepSize, Integer steps) {
        AclfBranch bra = network.getBranch(branchId);
        if (bra == null) return null;

        Optional<TapControl> tapOpt = AclfAdjustObjectFactory.createTapVControlMvarFlow(bra, controlType);
        if (!tapOpt.isPresent()) return null;

        TapControl tap = tapOpt.get();
        tap.setStatus(status);
        tap.setMvarSpecified(desiredMvar, mvarUnit, network.getBaseKva());
        tap.setTurnRatioLimit(new LimitType(tapMax, tapMin));
        tap.setControlOnFromSide(controlOnFromSide);
        tap.setMeteredOnFromSide(meteredOnFromSide);
        if (stepSize != null) tap.setTapStepSize(stepSize);
        if (steps != null) tap.setTapSteps(steps);
        return tap;
    }

    // ==================== Phase-Shifting Transformer ====================

    /**
     * Add a phase-shifting transformer (extends 2W xfr with phase angle).
     */
    public AclfBranch addPsXformer(String fromBusId, String toBusId, String cirId,
                                   Complex zPU, double fromTapPU, double toTapPU,
                                   double fromAngleDeg, double toAngleDeg,
                                   Complex magYFromSide, Complex magYToSide,
                                   double ratingMva1, double ratingMva2, double ratingMva3,
                                   int zTableNumber, boolean status) throws InterpssException {
        AclfBranch bra = CoreObjectFactory.createAclfBranch();
        network.addBranch(bra, fromBusId, toBusId, cirId);
        bra.setStatus(status);
        bra.setBranchCode(AclfBranchCode.PS_XFORMER);
        bra.setNetwork(network);

        AclfXformerAdapter xfr = bra.toXfr();
        bra.setZ(zPU);
        xfr.setFromTurnRatio(fromTapPU, UnitType.PU);
        xfr.setToTurnRatio(toTapPU, UnitType.PU);

        AclfPSXformerAdapter psXfr = bra.toPSXfr();
        if (fromAngleDeg != 0.0) psXfr.setFromAngle(fromAngleDeg, UnitType.Deg);
        if (toAngleDeg != 0.0) psXfr.setToAngle(toAngleDeg, UnitType.Deg);

        if (magYFromSide != null) bra.setFromShuntY(magYFromSide);
        if (magYToSide != null) bra.setToShuntY(magYToSide);

        bra.setRatingMva1(ratingMva1);
        bra.setRatingMva2(ratingMva2);
        bra.setRatingMva3(ratingMva3);

        if (zTableNumber > 0) bra.setXfrZTableNumber(zTableNumber);
        return bra;
    }

    /**
     * Add phase-shifter angle control to an existing PS transformer branch.
     */
    public PSXfrPControl addPsXfrAngleControl(String branchId, AclfAdjustControlType controlType,
                                              boolean status, double pSpecified, UnitType pUnit,
                                              double angMaxDeg, double angMinDeg,
                                              boolean controlOnFromSide, boolean flowFrom2To,
                                              boolean meteredOnFromSide) {
        AclfBranch bra = network.getBranch(branchId);
        if (bra == null) return null;

        Optional<PSXfrPControl> ctrlOpt = AclfAdjustObjectFactory.createPSXfrPControl(bra, controlType);
        if (!ctrlOpt.isPresent()) return null;

        PSXfrPControl ctrl = ctrlOpt.get();
        ctrl.setStatus(status);
        ctrl.setPSpecified(pSpecified, pUnit, network.getBaseKva());
        ctrl.setAngLimit(new LimitType(angMaxDeg, angMinDeg), UnitType.Deg);
        ctrl.setControlOnFromSide(controlOnFromSide);
        ctrl.setFlowFrom2To(flowFrom2To);
        ctrl.setMeteredOnFromSide(meteredOnFromSide);
        return ctrl;
    }

    /**
     * Add phase-shifter angle control with range control.
     */
    public PSXfrPControl addPsXfrAngleRangeControl(String branchId, boolean status,
                                                   double rangeMaxPU, double rangeMinPU,
                                                   double pSpecified, UnitType pUnit,
                                                   double angMaxDeg, double angMinDeg,
                                                   boolean controlOnFromSide, boolean flowFrom2To,
                                                   boolean meteredOnFromSide) {
        AclfBranch bra = network.getBranch(branchId);
        if (bra == null) return null;

        Optional<PSXfrPControl> ctrlOpt = AclfAdjustObjectFactory.createPSXfrPControl(
                bra, AclfAdjustControlType.RANGE_CONTROL);
        if (!ctrlOpt.isPresent()) return null;

        PSXfrPControl ctrl = ctrlOpt.get();
        ctrl.setStatus(status);
        ctrl.setDesiredControlRange(new LimitType(rangeMaxPU, rangeMinPU));
        ctrl.setPSpecified(pSpecified, pUnit, network.getBaseKva());
        ctrl.setAngLimit(new LimitType(angMaxDeg, angMinDeg), UnitType.Deg);
        ctrl.setControlOnFromSide(controlOnFromSide);
        ctrl.setFlowFrom2To(flowFrom2To);
        ctrl.setMeteredOnFromSide(meteredOnFromSide);
        return ctrl;
    }

    // ==================== 3-Winding Transformer ====================

    /**
     * Add a 3-winding transformer.
     * @return the created 3W branch
     */
    public Aclf3WBranch addXformer3W(String fromBusId, String toBusId, String tertBusId, String cirId,
                                     Complex z12PU, Complex z23PU, Complex z31PU,
                                     double fromTapPU, double toTapPU, double tertTapPU,
                                     Complex magYPU,
                                     double starVMagPU, double starVAngDeg,
                                     boolean wind1OffLine, boolean wind2OffLine, boolean wind3OffLine,
                                     boolean isPhaseShifting,
                                     double fromAngleDeg, double toAngleDeg, double tertAngleDeg,
                                     boolean status) throws InterpssException {
        Aclf3WBranch branch3W = CoreObjectFactory.createAclf3WBranch();
        branch3W.setCircuitNumber(cirId);
        network.add3WXfr(branch3W, fromBusId, toBusId, tertBusId);
        branch3W.setNetwork(network);
        branch3W.setStatus(status);

        if (isPhaseShifting) {
            branch3W.setBranchCode(AclfBranchCode.W3_PS_XFORMER);
            branch3W.create2WBranches(AclfBranchCode.PS_XFORMER);
        } else {
            branch3W.setBranchCode(AclfBranchCode.W3_XFORMER);
            branch3W.create2WBranches(AclfBranchCode.XFORMER);
        }

        branch3W.getFromAclfBranch().setStatus(status && !wind1OffLine);
        branch3W.getToAclfBranch().setStatus(status && !wind2OffLine);
        branch3W.getTertAclfBranch().setStatus(status && !wind3OffLine);

        if (isPhaseShifting) {
            Aclf3WPSXformerAdapter psXfr3W = branch3W.toPS3WXfr();
            double baseV = Math.max(Math.max(
                    branch3W.getFromBus().getBaseVoltage(),
                    branch3W.getToBus().getBaseVoltage()),
                    branch3W.getTertiaryBus().getBaseVoltage());
            psXfr3W.setZ(z12PU, z31PU, z23PU, UnitType.PU, baseV);
            psXfr3W.setFromTurnRatio(fromTapPU == 0.0 ? 1.0 : fromTapPU);
            psXfr3W.setToTurnRatio(toTapPU == 0.0 ? 1.0 : toTapPU);
            psXfr3W.setTertTurnRatio(tertTapPU == 0.0 ? 1.0 : tertTapPU);
            if (fromAngleDeg != 0.0) psXfr3W.setFromAngle(fromAngleDeg, UnitType.Deg);
            if (toAngleDeg != 0.0) psXfr3W.setToAngle(toAngleDeg, UnitType.Deg);
            if (tertAngleDeg != 0.0) psXfr3W.setTertAngle(tertAngleDeg, UnitType.Deg);
        } else {
            Aclf3WXformerAdapter xfr3W = branch3W.to3WXfr();
            double baseV = Math.max(Math.max(
                    branch3W.getFromBus().getBaseVoltage(),
                    branch3W.getToBus().getBaseVoltage()),
                    branch3W.getTertiaryBus().getBaseVoltage());
            xfr3W.setZ(z12PU, z31PU, z23PU, UnitType.PU, baseV);
            xfr3W.setFromTurnRatio(fromTapPU == 0.0 ? 1.0 : fromTapPU);
            xfr3W.setToTurnRatio(toTapPU == 0.0 ? 1.0 : toTapPU);
            xfr3W.setTertTurnRatio(tertTapPU == 0.0 ? 1.0 : tertTapPU);
        }

        if (magYPU != null) {
            branch3W.getFromAclfBranch().setFromShuntY(magYPU);
        }

        if (starVMagPU != 0.0) {
            BaseAclfBus<?, ?> starBus = (BaseAclfBus<?, ?>) branch3W.getStarBus();
            starBus.setVoltage(starVMagPU, Math.toRadians(starVAngDeg));
            branch3W.setVoltageStarBus(starBus.getVoltage());
        }

        return branch3W;
    }

    // ==================== HVDC 2-Terminal LCC ====================

    /**
     * Add a 2-terminal LCC HVDC line.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public HvdcLine2TLCC<AclfBus> addHvdcLine2TLCC(
            String id, String name, String fromBusId, String toBusId,
            boolean status, boolean puBasedAlgo,
            HvdcControlMode controlMode, HvdcOperationMode opMode,
            double rdcOhm, double powerDemandMW, double currentDemandA,
            boolean controlOnRectifier,
            double scheduledDCVoltageKV, double compoundROhm,
            double powerCurrentMargin,
            ConverterType meterEnd) throws InterpssException {

        HvdcLine2TLCC lcc = (HvdcLine2TLCC) HvdcObjectFactory.createHvdcLine2TLCC(
                HvdcOperationMode.REC1_INV1, id, fromBusId, toBusId);
        network.addHvdcLine2T(lcc, fromBusId, toBusId, id);
        lcc.setNetwork(network);
        lcc.setName(name != null ? name : "");
        lcc.setStatus(status);
        lcc.setPuBasedPowerFlowAlgo(puBasedAlgo);
        lcc.setDcLineControlMode(controlMode);
        lcc.setOperationMode(opMode);
        lcc.setRdc(rdcOhm, UnitType.Ohm);

        if (controlMode == HvdcControlMode.DC_CURRENT) {
            lcc.setCurrentDemand(currentDemandA);
        } else if (controlMode == HvdcControlMode.DC_POWER) {
            lcc.setControlSide(controlOnRectifier ? HvdcControlSide.RECTIFIER : HvdcControlSide.INVERTER);
            lcc.setPowerDemand(Math.abs(powerDemandMW), UnitType.mW);
        } else {
            lcc.setStatus(false);
        }

        lcc.setRectifierControlMode(HvdcControlMode.DC_CURRENT);
        lcc.setInverterControlMode(HvdcControlMode.DC_VOLTAGE);
        lcc.setScheduledDCVoltage(scheduledDCVoltageKV, UnitType.kV);
        lcc.setCompondR(compoundROhm, UnitType.Ohm);
        lcc.setPowerCurrentMargin(powerCurrentMargin);
        lcc.setMeterEnd(meterEnd);

        return lcc;
    }

    /**
     * Set rectifier data on an LCC HVDC line.
     */
    @SuppressWarnings("unchecked")
    public ThyConverter<AclfBus> setLCCRectifier(HvdcLine2TLCC<AclfBus> lcc,
            int nBridges, double minFiringAngDeg, double maxFiringAngDeg,
            double rcOhm, double xcOhm,
            double acRatedVoltageKV, double xfrRatio, double tapSetting,
            double tapMax, double tapMin, double tapStepSize,
            double commutingCapacitor, Double firingAngDeg) {
        ThyConverter<AclfBus> rec = HvdcObjectFactory.createThyConverter((AclfBus) lcc.getFromBus());
        rec.setConverterType(ConverterType.RECTIFIER);
        lcc.setRectifier(rec);

        rec.setNBridges(nBridges);
        rec.setFiringAngLimit(new LimitType(maxFiringAngDeg, minFiringAngDeg), UnitType.Deg);
        rec.setCommutingZ(new Complex(rcOhm, xcOhm));
        rec.setAcRatedVoltage(acRatedVoltageKV);
        rec.setXformerRatio(xfrRatio);
        rec.setXformerTapSetting(tapSetting);
        rec.setXformerTapLimit(new LimitType(tapMax, tapMin));
        rec.setXformerTapStepSize(tapStepSize);
        rec.setCommutingCapacitor(commutingCapacitor);
        if (firingAngDeg != null) rec.setFiringAng(firingAngDeg);
        return rec;
    }

    /**
     * Set inverter data on an LCC HVDC line.
     */
    @SuppressWarnings("unchecked")
    public ThyConverter<AclfBus> setLCCInverter(HvdcLine2TLCC<AclfBus> lcc,
            int nBridges, double minFiringAngDeg, double maxFiringAngDeg,
            double rcOhm, double xcOhm,
            double acRatedVoltageKV, double xfrRatio, double tapSetting,
            double tapMax, double tapMin, double tapStepSize,
            double commutingCapacitor, Double firingAngDeg) {
        ThyConverter<AclfBus> inv = HvdcObjectFactory.createThyConverter((AclfBus) lcc.getToBus());
        inv.setConverterType(ConverterType.INVERTER);
        lcc.setInverter(inv);

        inv.setNBridges(nBridges);
        inv.setFiringAngLimit(new LimitType(maxFiringAngDeg, minFiringAngDeg), UnitType.Deg);
        inv.setCommutingZ(new Complex(rcOhm, xcOhm));
        inv.setAcRatedVoltage(acRatedVoltageKV);
        inv.setXformerRatio(xfrRatio);
        inv.setXformerTapSetting(tapSetting);
        inv.setXformerTapLimit(new LimitType(tapMax, tapMin));
        inv.setXformerTapStepSize(tapStepSize);
        inv.setCommutingCapacitor(commutingCapacitor);
        if (firingAngDeg != null) inv.setFiringAng(firingAngDeg);
        return inv;
    }

    // ==================== HVDC 2-Terminal VSC ====================

    /**
     * Add a 2-terminal VSC HVDC line.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public HvdcLine2TVSC<AclfBus> addHvdcLine2TVSC(
            String id, String name, String fromBusId, String toBusId,
            boolean status, double rdcOhm, double mvaRating) throws InterpssException {

        HvdcLine2TVSC vsc = (HvdcLine2TVSC) HvdcObjectFactory.createHvdc2TVSC();
        vsc.setId(id);
        vsc.setName(name != null ? name : "");
        network.addHvdcLine2T(vsc, fromBusId, toBusId, id);
        vsc.setNetwork(network);
        vsc.setStatus(status);
        vsc.setRdc(rdcOhm, UnitType.Ohm);
        vsc.setMvaRating(mvaRating);
        return vsc;
    }

    /**
     * Configure a VSC converter (rectifier or inverter).
     */
    public void setVSCConverter(VSCConverter converter, String busId,
                                HvdcControlMode dcMode, double dcSetPoint,
                                VSCAcControlMode acMode, double acSetPoint,
                                double mvaRating, double qMaxMvar, double qMinMvar,
                                String remoteCtrlBusId, double remoteCtrlPercent) {
        AclfBus bus = network.getBus(busId);
        if (bus != null) converter.setBus(bus);
        converter.setDcControlMode(dcMode);
        converter.setDcSetPoint(dcSetPoint);
        converter.setAcControlMode(acMode);
        converter.setAcSetPoint(acSetPoint);
        converter.setMvaRating(mvaRating);
        converter.setQMvarLimit(new LimitType(qMaxMvar, qMinMvar));
        if (remoteCtrlBusId != null && !remoteCtrlBusId.isEmpty()) {
            converter.setRemoteControlBusId(remoteCtrlBusId);
            converter.setRemoteControlPercent(remoteCtrlPercent);
        }
    }

    // ==================== FACTS Device ====================

    /**
     * Add a FACTS device as a series branch with optional SVC equivalent at terminal.
     */
    public AclfBranch addFactsDevice(String fromBusId, String toBusId, String cirId,
                                     int mode, double linxPU,
                                     double rPU, double xPU,
                                     double svcQMaxPU, double svcQMinPU, double svcVSetpointPU,
                                     String regulatedBusId, double remoteCtrlPercent,
                                     Complex targetPQPU,
                                     boolean status) throws InterpssException {
        AclfBranch bra = CoreObjectFactory.createAclfBranch();
        network.addBranch(bra, fromBusId, toBusId, cirId);
        bra.setStatus(status);
        bra.setBranchCode(AclfBranchCode.LINE);
        bra.setNetwork(network);

        if (mode == 0) {
            bra.setZ(new Complex(0.0, linxPU));
        } else if (mode == 1) {
            bra.setZ(new Complex(0.0, linxPU));
            if (svcQMaxPU != 0.0 || svcQMinPU != 0.0) {
                addSVC(fromBusId, "SVC@" + cirId, status, svcQMaxPU, svcQMinPU,
                       svcVSetpointPU, regulatedBusId, remoteCtrlPercent);
            }
            if (targetPQPU != null && targetPQPU.abs() > 0) {
                addFixedPowerLoad(fromBusId, cirId + "_from", targetPQPU);
                addFixedPowerLoad(toBusId, cirId + "_to", targetPQPU.negate());
            }
            bra.setStatus(false);
        } else if (mode == 2) {
            bra.setZ(new Complex(0.0, 0.0));
            if (svcQMaxPU != 0.0 || svcQMinPU != 0.0) {
                addSVC(fromBusId, "SVC@" + cirId, status, svcQMaxPU, svcQMinPU,
                       svcVSetpointPU, regulatedBusId, remoteCtrlPercent);
            }
        } else if (mode == 3) {
            bra.setZ(new Complex(rPU, xPU));
            if (svcQMaxPU != 0.0 || svcQMinPU != 0.0) {
                addSVC(fromBusId, "SVC@" + cirId, status, svcQMaxPU, svcQMinPU,
                       svcVSetpointPU, regulatedBusId, remoteCtrlPercent);
            }
        }
        return bra;
    }

    private void addFixedPowerLoad(String busId, String id, Complex loadPQPU) {
        AclfBus bus = network.getBus(busId);
        if (bus == null) return;
        AclfLoad load = CoreObjectFactory.createAclfLoad(id);
        bus.getContributeLoadList().add(load);
        load.setLoadCP(loadPQPU);
        load.setCode(AclfLoadCode.CONST_P);
        bus.setLoadCode(AclfLoadCode.CONST_P);
    }

    // ==================== Switching Device ====================

    /**
     * Add a switching device (breaker/disconnect).
     */
    public AclfBranch addSwitchingDevice(String fromBusId, String toBusId, String cirId,
                                         boolean status, AclfBranchCode code) throws InterpssException {
        AclfBranch bra = CoreObjectFactory.createAclfBranch();
        network.addBranch(bra, fromBusId, toBusId, cirId);
        bra.setStatus(status);
        bra.setBranchCode(code);
        bra.setZ(new Complex(0.0, 0.0));
        return bra;
    }

    // ==================== Flow Interface ====================

    /**
     * Add a flow interface.
     */
    public FlowInterface addFlowInterface(String id) {
        return AclfAdjustObjectFactory.createInterface(network, id);
    }

    public FlowInterfaceBranch addInterfaceBranch(FlowInterface intf,
                                                  String fromBusId, String toBusId, String cirId,
                                                  double weight) {
        FlowInterfaceBranch branch = AclfAdjustObjectFactory.createInterfaceBranch(intf);
        AclfBranch b = network.getBranch(fromBusId, toBusId, cirId);
        if (b == null) {
            b = network.getBranch(toBusId, fromBusId, cirId);
            branch.setBranchDir(false);
        } else {
            branch.setBranchDir(true);
        }
        if (b != null) {
            branch.setBranch(b);
            branch.setWeight(weight);
        } else {
            log.error("Interface branch not found: " + fromBusId + "-" + toBusId + " ckt " + cirId);
        }
        return branch;
    }

    public void setInterfaceLimit(FlowInterface intf,
                                  boolean isOnPeak,
                                  boolean limitStatus, FlowInterfaceType type,
                                  double exportLimitPU, double importLimitPU) {
        FlowInterfaceLimit limit = AclfAdjustObjectFactory.createInterfaceLimit();
        limit.setStatus(limitStatus);
        limit.setType(type);
        limit.setRefDirExportLimit(exportLimitPU);
        limit.setOppsiteRefDirImportLimit(importLimitPU);
        if (isOnPeak) {
            intf.setOnPeakLimit(limit);
        } else {
            intf.setOffPeakLimit(limit);
        }
        network.setFlowInterfaceLoaded(true);
    }

    // ==================== Post-processing / Finalize ====================

    /**
     * Perform post-processing on the network after all data has been loaded.
     * This resolves remote bus references, adjusts transformer impedances,
     * initializes contributing gen/load models, and numbers 3W star buses.
     *
     * Ported from AbstractODMAclfNetMapper.postAclfNetProcessing().
     */
    public void finalizeNetwork() throws InterpssException {
        // Resolve SVC and SwitchedShunt remote bus references
        network.getBusList().forEach(bus -> {
            if (bus.isStaticVarCompensator()) {
                for (StaticVarCompensator svc : bus.getStaticVarCompensatorList()) {
                    if (svc != null && svc.getRemoteBusBranchId() != null && !svc.getRemoteBusBranchId().isEmpty()) {
                        BaseAclfBus<? extends AclfGen, ? extends AclfLoad> remoteBus = network.getBus(svc.getRemoteBusBranchId());
                        svc.setRemoteBus(remoteBus);
                    }
                }
            }
            if (bus.isSwitchedShunt()) {
                for (SwitchedShunt sw : bus.getSwitchedShuntList()) {
                    if (sw != null && sw.getRemoteBusBranchId() != null && !sw.getRemoteBusBranchId().isEmpty()) {
                        BaseAclfBus<? extends AclfGen, ? extends AclfLoad> remoteBus = network.getBus(sw.getRemoteBusBranchId());
                        sw.setRemoteBus(remoteBus);
                    }
                }
            }
        });

        network.adjustXfrZ();
        network.initContributeGenLoad(false);

        // Number 3W transformer star buses
        if (network.getOriginalDataFormat() == OriginalDataFormat.PSSE) {
            long maxBusNum = network.getBusList().stream()
                    .mapToLong(bus -> bus.getNumber() > 0 ? bus.getNumber() : 0)
                    .max()
                    .orElse(network.getBusList().size());

            long startingNum = (long) Math.pow(10, Long.toString(maxBusNum).length());
            for (BaseAclfBus<?, ?> bus : network.getBusList()) {
                if ("3WXfr StarBus".equals(bus.getName())) {
                    bus.setNumber(startingNum++);
                }
            }
        }
    }
}
