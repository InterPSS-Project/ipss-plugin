/*
 * CGMESLineMapper.java
 *
 * Maps CIM ACLineSegment → AclfNetwork line branch.
 */

package org.interpss.fadapter.cim.mapper;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfBranch;

/**
 * Maps CIM ACLineSegment to an AclfNetwork line.
 * CIM stores R, X in Ohms and Gch, Bch in Siemens (total line charging).
 */
public class CGMESLineMapper extends AbstractCGMESDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CGMESLineMapper.class);

    private final double baseMVA;

    public CGMESLineMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    @Override
    public void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        boolean inService = bag.getBoolean("Equipment.inService", true);

        String lineId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = lineId;

        double r = bag.getDouble("ACLineSegment.r");
        double x = bag.getDouble("ACLineSegment.x");
        double gch = bag.getDouble("ACLineSegment.gch", 0.0);
        double bch = bag.getDouble("ACLineSegment.bch", 0.0);

        String[] busIds = resolveBranchBusIds(bag.getId());
        String fromBusId = busIds[0];
        String toBusId = busIds[1];

        if (fromBusId == null || toBusId == null
                || builder.getBus(fromBusId) == null || builder.getBus(toBusId) == null) {
            // A skipped boundary TN still yields a local id; do not build a branch
            // whose from/to bus was never created.
            logSkippedBranch("line", name, fromBusId, toBusId, bag.getId());
            return;
        }

        Double baseKV = resolveBaseKV(bag, builder, fromBusId, toBusId);
        double baseZ = baseKV * baseKV / baseMVA;
        double baseY = baseMVA / (baseKV * baseKV);
        double rPU = r / baseZ;
        double xPU = x / baseZ;
        double gPU = gch / baseY;
        double bPU = bch / baseY;

        String cirId = nextCircuitId(builder, fromBusId, toBusId);
        if (cirId == null) {
            log.warn("Skipping line {} - too many parallel circuits", name);
            return;
        }

        Double fromBase = busBaseKV(builder, fromBusId);
        Double toBase = busBaseKV(builder, toBusId);
        // UCTE labels the same EHV class as 380 or 400 (and 220/225). Those are not
        // real voltage transformations — keep the ACLineSegment as a line.
        boolean crossVoltage = fromBase != null && toBase != null
                && !sameUcteVoltageClass(fromBase, toBase);
        if (!crossVoltage) {
            double targetKv = preferredUcteBaseKv(baseKV, fromBase, toBase);
            if (Math.abs(targetKv - baseKV) > 0.05) {
                baseKV = targetKv;
                baseZ = baseKV * baseKV / baseMVA;
                baseY = baseMVA / (baseKV * baseKV);
                rPU = r / baseZ;
                xPU = x / baseZ;
                gPU = gch / baseY;
                bPU = bch / baseY;
            }
            harmonizeBusBase(builder, fromBusId, fromBase, targetKv);
            harmonizeBusBase(builder, toBusId, toBase, targetKv);
        }
        AclfBranch branch;
        if (crossVoltage) {
            // Refer series Z to the from-bus base. toTap = fromBase/toBase makes
            // equal pu voltages the same kilovolts across the conductor.
            double zKv = fromBase;
            double crossZ = zKv * zKv / baseMVA;
            rPU = r / crossZ;
            xPU = x / crossZ;
            double fromY = baseMVA / (fromBase * fromBase);
            double toY = baseMVA / (toBase * toBase);
            // InterPSS rejects a turn ratio outside (0, 2]. Keep the kilovolt
            // match when it fits; otherwise use a 1:1 pu transformer.
            double fromTap = 1.0;
            double toTap = fromBase / toBase;
            // setToTurnRatio rejects 2.0 itself, not only values above it.
            if (!(toTap > 0.0 && toTap < 2.0)) {
                fromTap = toBase / fromBase;
                toTap = 1.0;
            }
            if (!(fromTap > 0.0 && fromTap < 2.0 && toTap > 0.0 && toTap < 2.0)) {
                fromTap = 1.0;
                toTap = 1.0;
            }
            Complex yFrom = new Complex((gch * 0.5) / fromY, (bch * 0.5) / fromY).multiply(fromTap * fromTap);
            Complex yTo = new Complex((gch * 0.5) / toY, (bch * 0.5) / toY).multiply(toTap * toTap);
            branch = builder.addXformer2W(fromBusId, toBusId, cirId,
                    new Complex(rPU, xPU), fromTap, toTap,
                    yFrom, yTo, 0.0, 0.0, 0.0, 0, true);
        } else {
            branch = builder.addLine(fromBusId, toBusId, cirId,
                    new Complex(rPU, xPU),
                    new Complex(gPU * 0.5, bPU * 0.5),
                    null, null, 0.0, 0.0, 0.0, true);
        }
        branch.setId(lineId);
        branch.setName(name.isEmpty() ? lineId : name);
        // SSH/SvStatus may mark the segment out of service. Keep the branch for
        // SV compare / topology ids, but leave it inactive so NR does not short
        // SV-distant buses (e.g. ReliCap Espheim line 8-9).
        if (!inService) {
            branch.setStatus(false);
            log.debug("Mapped out-of-service ACLineSegment {} as inactive", name);
        }

        log.debug("Created line branch: {} ({}→{}) r={} x={} bch={} PU",
            name, fromBusId, toBusId, rPU, xPU, bPU);
    }

    /**
     * Map SeriesCompensator as a line (PowSyBl behavior).
     */
    public void mapSeriesCompensator(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        boolean inService = bag.getBoolean("Equipment.inService", true);

        String lineId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = lineId;

        double r = bag.getDouble("SeriesCompensator.r", 0.0);
        double x = bag.getDouble("SeriesCompensator.x", 0.0);

        String[] busIds = resolveBranchBusIds(bag.getId());
        String fromBusId = busIds[0];
        String toBusId = busIds[1];

        if (fromBusId == null || toBusId == null
                || builder.getBus(fromBusId) == null || builder.getBus(toBusId) == null) {
            logSkippedBranch("SeriesCompensator", name, fromBusId, toBusId, bag.getId());
            return;
        }

        Double baseKV = resolveBaseKV(bag, builder, fromBusId, toBusId);
        double baseZ = baseKV * baseKV / baseMVA;
        double rPU = r / baseZ;
        double xPU = x / baseZ;

        String cirId = nextCircuitId(builder, fromBusId, toBusId);
        if (cirId == null) {
            log.warn("Skipping SeriesCompensator {} - too many parallel circuits", name);
            return;
        }

        AclfBranch branch = builder.addLine(fromBusId, toBusId, cirId,
                new Complex(rPU, xPU),
                new Complex(0.0, 0.0),
                null, null, 0.0, 0.0, 0.0, true);
        branch.setId(lineId);
        branch.setName(name.isEmpty() ? lineId : name);
        if (!inService) {
            branch.setStatus(false);
            log.debug("Mapped out-of-service SeriesCompensator {} as inactive", name);
        }

        log.debug("Created SeriesCompensator as line: {} ({}→{}) r={} x={} PU",
            name, fromBusId, toBusId, rPU, xPU);
    }

    /**
     * Closed retained switch. CGMES keeps the two topological nodes distinct and
     * the switch as the branch between them. A non-retained switch is already
     * inside one topological node; mapping it uses connectivity-node ids that
     * were never created as buses. MicroGrid breaker B1 is retained and is the
     * only direct link between NL-Busbar_2 and NL_Busbar__4.
     */
    public void mapClosedSwitch(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        if (!bag.getBoolean("Equipment.inService", true)) return;
        if (bag.getBoolean("Switch.open", false)) return;
        if (!bag.getBoolean("Switch.retained", false)) return;

        String lineId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = lineId;

        String[] busIds = resolveBranchBusIds(bag.getId());
        String fromBusId = busIds[0];
        String toBusId = busIds[1];
        if (fromBusId == null || toBusId == null || fromBusId.equals(toBusId)) {
            return;
        }
        if (builder.getBus(fromBusId) == null || builder.getBus(toBusId) == null) {
            log.debug("Skipping closed switch {} - bus missing ({}, {})", name, fromBusId, toBusId);
            return;
        }

        String cirId = nextCircuitId(builder, fromBusId, toBusId);
        if (cirId == null) {
            log.warn("Skipping closed switch {} - too many parallel circuits", name);
            return;
        }

        Double fromBase = busBaseKV(builder, fromBusId);
        Double toBase = busBaseKV(builder, toBusId);
        boolean crossVoltage = fromBase != null && toBase != null
                && !sameUcteVoltageClass(fromBase, toBase);
        AclfBranch branch;
        if (crossVoltage) {
            // isZeroZBranch() rejects transformers. Keep a small series X so the
            // cross-voltage tie stays a nonsingular two-winding transformer.
            final double xPu = 1.0e-4;
            double fromTap = 1.0;
            double toTap = fromBase / toBase;
            if (!(toTap > 0.0 && toTap < 2.0)) {
                fromTap = toBase / fromBase;
                toTap = 1.0;
            }
            if (!(fromTap > 0.0 && fromTap < 2.0 && toTap > 0.0 && toTap < 2.0)) {
                fromTap = 1.0;
                toTap = 1.0;
            }
            branch = builder.addXformer2W(fromBusId, toBusId, cirId,
                    new Complex(0.0, xPu), fromTap, toTap,
                    null, null, 0.0, 0.0, 0.0, 0, true);
        } else {
            // Same-base closed switch is a zero-impedance branch. Load flow
            // consolidates the two buses before NR (ZeroZBranch usage guide).
            branch = builder.addLine(fromBusId, toBusId, cirId,
                    new Complex(0.0, 0.0),
                    new Complex(0.0, 0.0),
                    null, null, 0.0, 0.0, 0.0, true);
        }
        branch.setId(lineId);
        branch.setName(name.isEmpty() ? lineId : name);
        log.debug("Created closed switch as tie: {} ({}→{})", name, fromBusId, toBusId);
    }

    /**
     * Align ACLineSegment end-bus bases when TP labeled one end with a UCTE synonym
     * (380 vs 400). Safe to call after every line has been mapped.
     */
    public void alignUcteLineEndBases(CGMESPropertyBag bag, AclfNetworkBuilder builder) {
        String[] busIds = resolveBranchBusIds(bag.getId());
        String fromBusId = busIds[0];
        String toBusId = busIds[1];
        if (fromBusId == null || toBusId == null) {
            return;
        }
        Double baseKV = resolveBaseKV(bag, builder, fromBusId, toBusId);
        if (baseKV == null) {
            return;
        }
        Double fromBase = busBaseKV(builder, fromBusId);
        Double toBase = busBaseKV(builder, toBusId);
        if (fromBase != null && toBase != null && !sameUcteVoltageClass(fromBase, toBase)) {
            return;
        }
        // Prefer the higher UCTE synonym for 380/400 only (never pull 400→380).
        double targetKv = preferredUcteBaseKv(baseKV, fromBase, toBase);
        harmonizeBusBase(builder, fromBusId, fromBase, targetKv);
        harmonizeBusBase(builder, toBusId, toBase, targetKv);
    }

    /**
     * Propagate the preferred UCTE synonym across every Line (ACLineSegment and
     * closed retained switch). EQ/SSH alignment only walks ACLineSegments, so a
     * BE 225 kV raise on a border TN never reaches NL buses tied only through a
     * retained breaker — LF then auto-turns the path into a 1:1 xfr and SvPowerFlow
     * matching collapses (MicroGrid Type1).
     *
     * <p>When a bus base changes, rescale that line's pu Z and shunt Y so the ohm
     * model stays consistent with the new kilovolt base.
     */
    public void unifyUcteLineBusBases(AclfNetworkBuilder builder) {
        var net = builder.getNetwork();
        if (net == null) {
            return;
        }
        boolean changed = true;
        for (int guard = 0; changed && guard < 32; guard++) {
            changed = false;
            for (AclfBranch br : net.getBranchList()) {
                if (br == null || !br.isLine() || !br.isActive()) {
                    continue;
                }
                var from = br.getFromAclfBus();
                var to = br.getToAclfBus();
                if (from == null || to == null) {
                    continue;
                }
                double fromKv = from.getBaseVoltage() / 1000.0;
                double toKv = to.getBaseVoltage() / 1000.0;
                if (!(fromKv > 0.0 && toKv > 0.0)) {
                    continue;
                }
                if (Math.abs(fromKv - toKv) <= 0.05) {
                    continue;
                }
                if (!sameUcteVoltageClass(fromKv, toKv)) {
                    continue;
                }
                double targetKv = Math.max(fromKv, toKv);
                double oldRefKv = 0.5 * (fromKv + toKv);
                if (Math.abs(fromKv - targetKv) > 0.05) {
                    from.setBaseVoltage(targetKv * 1000.0);
                    changed = true;
                }
                if (Math.abs(toKv - targetKv) > 0.05) {
                    to.setBaseVoltage(targetKv * 1000.0);
                    changed = true;
                }
                if (oldRefKv > 0.0 && Math.abs(oldRefKv - targetKv) > 0.05) {
                    double zScale = (oldRefKv * oldRefKv) / (targetKv * targetKv);
                    double yScale = (targetKv * targetKv) / (oldRefKv * oldRefKv);
                    br.setZ(br.getZ().multiply(zScale));
                    br.setHShuntY(br.getHShuntY().multiply(yScale));
                    log.debug("UCTE unify {}: buses→{} kV, Z×{}, Y×{}",
                            br.getId(), targetKv, zScale, yScale);
                }
            }
        }
    }

    private Double resolveBaseKV(CGMESPropertyBag bag, AclfNetworkBuilder builder,
                                 String fromBusId, String toBusId) {
        Double baseKV = null;
        String bvRef = bag.getResourceId("ConductingEquipment.BaseVoltage");
        if (bvRef != null && cimModel != null) {
            baseKV = cimModel.getBaseVoltageValue(bvRef);
        }
        if (baseKV == null && cimModel != null) {
            java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(bag.getId());
            if (!topoNodes.isEmpty()) {
                baseKV = cimModel.getNominalVoltageForTopoNode(topoNodes.get(0));
            }
        }
        if (baseKV == null) {
            baseKV = busBaseKV(builder, fromBusId);
        }
        if (baseKV == null) {
            baseKV = busBaseKV(builder, toBusId);
        }
        if (baseKV == null) {
            log.warn("Cannot determine base voltage for {}, using 100 kV", bag.getName());
            baseKV = 100.0;
        }
        return baseKV;
    }

    /** UCTE EHV synonyms: 380↔400 kV and 220↔225 kV are the same voltage class. */
    static boolean sameUcteVoltageClass(double aKv, double bKv) {
        if (Math.abs(aKv - bKv) <= 0.05) {
            return true;
        }
        double lo = Math.min(aKv, bKv);
        double hi = Math.max(aKv, bKv);
        return (lo >= 375.0 && hi <= 405.0) || (lo >= 215.0 && hi <= 230.0);
    }

    /** Prefer the higher synonym within a UCTE class (400 over 380, 225 over 220). */
    static double preferredUcteBaseKv(double lineKv, Double fromKv, Double toKv) {
        double target = lineKv;
        if (fromKv != null && sameUcteVoltageClass(fromKv, target) && fromKv > target) {
            target = fromKv;
        }
        if (toKv != null && sameUcteVoltageClass(toKv, target) && toKv > target) {
            target = toKv;
        }
        return target;
    }

    private void harmonizeBusBase(AclfNetworkBuilder builder, String busId,
            Double currentKv, double targetKv) {
        if (busId == null) {
            return;
        }
        var bus = builder.getBus(busId);
        if (bus == null || bus.getBaseVoltage() <= 0) {
            return;
        }
        double current = bus.getBaseVoltage() / 1000.0;
        if (Math.abs(current - targetKv) <= 0.05) {
            return;
        }
        if (!sameUcteVoltageClass(current, targetKv)) {
            return;
        }
        bus.setBaseVoltage(targetKv * 1000.0);
        log.debug("Aligned bus {} base from {} kV to line base {} kV",
                busId, current, targetKv);
    }

    /**
     * Boundary interconnectors (one end on a skipped/dangling boundary TN) are expected
     * when EQBD/TP_BD is absent — log at debug. Genuine connectivity gaps stay WARN.
     */
    private void logSkippedBranch(String kind, String name, String fromBusId, String toBusId,
                                  String equipmentId) {
        boolean boundaryTie = (fromBusId == null) != (toBusId == null)
                || isBoundaryOrUnmappedEnd(equipmentId);
        if (boundaryTie) {
            log.debug("Skipping {} {} - boundary/unmapped end (from={}, to={})",
                    kind, name, fromBusId, toBusId);
        } else {
            log.warn("Skipping {} {} - cannot resolve bus connectivity (from={}, to={})",
                    kind, name, fromBusId, toBusId);
        }
    }

    private boolean isBoundaryOrUnmappedEnd(String equipmentId) {
        if (cimModel == null) return false;
        for (String tn : cimModel.getTopologicalNodesForEquipment(equipmentId)) {
            if (cimModel.isBoundaryTopologicalNode(tn) || cimModel.isUnmappedTopoNode(tn)) {
                return true;
            }
        }
        return false;
    }
}
