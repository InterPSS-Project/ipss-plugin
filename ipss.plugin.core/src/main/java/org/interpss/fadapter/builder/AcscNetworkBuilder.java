/*
 * @(#)AcscNetworkBuilder.java
 *
 * Copyright (C) 2006-2026 www.interpss.org
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
 * @Date 07/15/2026
 */

package org.interpss.fadapter.builder;

import static com.interpss.core.funcImpl.AcscFunction.acscLineAptr;
import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscLineAdapter;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;

/**
 * Builder for constructing AcscNetwork sequence (short-circuit) data directly
 * from parsed file data, bypassing the ODM XML intermediate representation.
 *
 * This is a standalone class (not extending AclfNetworkBuilder) because
 * AclfNetworkBuilder's network field is private and creates an AclfNetwork,
 * whereas this builder needs an AcscNetwork.
 *
 * Typical workflow:
 *   1. Create AcscNetwork via this builder (or pass in an existing one)
 *   2. Add ACLF buses/branches/gens to the network using CoreObjectFactory
 *   3. Call methods here to set sequence impedance and grounding data
 *   4. Call finalizeAcscNetwork() when done
 */
public class AcscNetworkBuilder {
    private static final Logger log = LoggerFactory.getLogger(AcscNetworkBuilder.class);

    private final AcscNetwork network;

    public AcscNetworkBuilder() {
        this.network = CoreObjectFactory.createAcscNetwork();
    }

    public AcscNetworkBuilder(AcscNetwork network) {
        this.network = network;
    }

    @SuppressWarnings("unchecked")
    public AcscNetworkBuilder(BaseAcscNetwork<?,?> network) {
        this.network = (AcscNetwork) network;
    }

    public AcscNetwork getAcscNetwork() {
        return network;
    }

    // ==================== Bus SC Data ====================

    public void setContributingBus(String busId) {
        BaseAcscBus<?, ?> bus = getAcscBus(busId);
        if (bus == null) return;
        bus.setScCode(BusScCode.CONTRIBUTE);
    }

    public void setNonContributingBus(String busId) {
        BaseAcscBus<?, ?> bus = getAcscBus(busId);
        if (bus == null) return;
        bus.setScCode(BusScCode.NON_CONTRI);
        bus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.POSITIVE);
        bus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.NEGATIVE);
        bus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.ZERO);
        bus.getGrounding().setGroundCode(BusGroundCode.UNGROUNDED);
        bus.getGrounding().setZ(NumericConstant.LargeBusZ);
    }

    public void setBusGrounding(String busId, BusGroundCode code, Complex zg) {
        BaseAcscBus<?, ?> bus = getAcscBus(busId);
        if (bus == null) return;
        bus.getGrounding().setGroundCode(code);
        if (zg != null) {
            bus.getGrounding().setZ(zg);
        }
    }

    // ==================== Generator Sequence Impedance ====================

    public void setGenPosSeqZ(String busId, String genId, double r, double x) {
        AcscGen gen = findAcscGen(busId, genId);
        if (gen != null) gen.setPosGenZ(new Complex(r, x));
    }

    public void setGenNegSeqZ(String busId, String genId, double r, double x) {
        AcscGen gen = findAcscGen(busId, genId);
        if (gen != null) gen.setNegGenZ(new Complex(r, x));
    }

    public void setGenZeroSeqZ(String busId, String genId, double r, double x) {
        AcscGen gen = findAcscGen(busId, genId);
        if (gen != null) gen.setZeroGenZ(new Complex(r, x));
    }

    // ==================== Load Sequence Data ====================

    public void setLoadNegSeqShuntY(String busId, double g, double b) {
        BaseAcscBus<?, ?> bus = getAcscBus(busId);
        if (bus != null) bus.setScLoadShuntY2(new Complex(g, b));
    }

    public void setLoadZeroSeqShuntY(String busId, double g, double b) {
        BaseAcscBus<?, ?> bus = getAcscBus(busId);
        if (bus != null) bus.setScLoadShuntY0(new Complex(g, b));
    }

    // ==================== Line Zero Sequence ====================

    /**
     * @param b0 total zero sequence charging susceptance (will be halved for HB0)
     * @param gi from-side zero seq shunt G (reserved, not stored in current model)
     * @param bi from-side zero seq shunt B (reserved, not stored in current model)
     * @param gj to-side zero seq shunt G (reserved, not stored in current model)
     * @param bj to-side zero seq shunt B (reserved, not stored in current model)
     */
    public void setLineZeroSeqData(String fromBusId, String toBusId, String cirId,
                                    double r0, double x0, double b0,
                                    double gi, double bi, double gj, double bj) {
        AcscBranch branch = getAcscBranch(fromBusId, toBusId, cirId);
        if (branch == null) return;

        AcscLineAdapter line = acscLineAptr.apply(branch);
        double baseV = branch.getFromAcscBus().getBaseVoltage();
        line.setZ0(new Complex(r0, x0), UnitType.PU, baseV);
        line.setHB0(b0 * 0.5, UnitType.PU, baseV);
    }

    // ==================== Transformer Zero Sequence ====================

    /**
     * Set transformer zero sequence data using PSS/E connection code (CC).
     *
     * CC values define the zero sequence equivalent circuit topology:
     *   1 = Yg-Yg (series path)
     *   2 = gY-Delta (ground path on winding 1)
     *   3 = Delta-gY (ground path on winding 2)
     *   4 = Delta-Delta (no series or ground path)
     *   5 = series path with ground on winding 2
     *   6 = gY-Delta with earthing transformer on winding 2
     *   7 = Delta-gY with earthing transformer on winding 1
     *   8 = Yg-Yg with different grounding Z per side
     *
     * @param rg grounding resistance (winding 1 for CC=1,2,6,8; winding 2 for CC=3,5,7)
     * @param xg grounding reactance
     * @param r1 zero sequence resistance in PU
     * @param x1 zero sequence reactance in PU
     * @param r2 secondary grounding resistance (CC=8: winding 2 grounding)
     * @param x2 secondary grounding reactance
     */
    public void setXfrZeroSeqData(String fromBusId, String toBusId, String cirId,
                                   int cc, double rg, double xg,
                                   double r1, double x1, double r2, double x2) {
        AcscBranch branch = getAcscBranch(fromBusId, toBusId, cirId);
        if (branch == null) return;

        AcscXformerAdapter xfr = acscXfrAptr.apply(branch);
        double baseV = Math.max(branch.getFromAcscBus().getBaseVoltage(),
                                branch.getToAcscBus().getBaseVoltage());
        xfr.setZ0(new Complex(r1, x1), UnitType.PU, baseV);

        Complex zgPrimary = new Complex(rg, xg);

        switch (cc) {
            case 1:
                xfr.setFromGrounding(wyeGroundCode(rg, xg), XFormerConnectCode.WYE, zgPrimary);
                xfr.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0, 0));
                break;
            case 2:
                xfr.setFromGrounding(wyeGroundCode(rg, xg), XFormerConnectCode.WYE, zgPrimary);
                xfr.setToGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0, 0));
                break;
            case 3:
                xfr.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0, 0));
                xfr.setToGrounding(wyeGroundCode(rg, xg), XFormerConnectCode.WYE, zgPrimary);
                break;
            case 4:
                xfr.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0, 0));
                xfr.setToGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0, 0));
                break;
            case 5:
                log.warn("CC=5 for xfr {}->{} ckt {}: limited support", fromBusId, toBusId, cirId);
                xfr.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0, 0));
                xfr.setToGrounding(wyeGroundCode(rg, xg), XFormerConnectCode.WYE, zgPrimary);
                break;
            case 6:
                xfr.setFromGrounding(wyeGroundCode(rg, xg), XFormerConnectCode.WYE, zgPrimary);
                xfr.setToGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0, 0));
                break;
            case 7:
                xfr.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0, 0));
                xfr.setToGrounding(wyeGroundCode(rg, xg), XFormerConnectCode.WYE, zgPrimary);
                break;
            case 8:
                xfr.setFromGrounding(wyeGroundCode(rg, xg), XFormerConnectCode.WYE, zgPrimary);
                xfr.setToGrounding(wyeGroundCode(r2, x2), XFormerConnectCode.WYE, new Complex(r2, x2));
                break;
            default:
                log.error("Unknown CC={} for xfr {}->{} ckt {}", cc, fromBusId, toBusId, cirId);
        }
    }

    // ==================== Switched Shunt Zero Sequence ====================

    public void setSwitchedShuntZeroSeqY(String busId, double g0, double b0) {
        BaseAcscBus<?, ?> bus = getAcscBus(busId);
        if (bus != null) bus.setScSwitchedShuntY0(new Complex(g0, b0));
    }

    // ==================== Finalize ====================

    public void finalizeAcscNetwork() {
        network.setPositiveSeqDataOnly(false);
        network.setScDataLoaded(true);
    }

    // ==================== Private Helpers ====================

    private BaseAcscBus<?, ?> getAcscBus(String busId) {
        Object bus = network.getBus(busId);
        if (bus == null) {
            log.error("Bus not found: {}", busId);
            return null;
        }
        return (BaseAcscBus<?, ?>) bus;
    }

    private AcscBranch getAcscBranch(String fromBusId, String toBusId, String cirId) {
        AcscBranch branch = (AcscBranch) network.getBranch(fromBusId, toBusId, cirId);
        if (branch == null) {
            branch = (AcscBranch) network.getBranch(toBusId, fromBusId, cirId);
        }
        if (branch == null) {
            log.error("Branch not found: {}->{} ckt {}", fromBusId, toBusId, cirId);
        }
        return branch;
    }

    private AcscGen findAcscGen(String busId, String genId) {
        BaseAcscBus<?, ?> bus = getAcscBus(busId);
        if (bus == null) return null;
        for (Object gen : bus.getContributeGenList()) {
            if (gen instanceof AcscGen) {
                AcscGen acscGen = (AcscGen) gen;
                if (acscGen.getId().equals(genId)) {
                    return acscGen;
                }
            }
        }
        log.error("AcscGen {} not found on bus {}", genId, busId);
        return null;
    }

    private BusGroundCode wyeGroundCode(double r, double x) {
        return (r == 0.0 && x == 0.0) ? BusGroundCode.SOLID_GROUNDED : BusGroundCode.ZGROUNDED;
    }
}
