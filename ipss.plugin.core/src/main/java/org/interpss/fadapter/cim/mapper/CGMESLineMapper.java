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

        if (fromBusId == null || toBusId == null) {
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
        boolean crossVoltage = fromBase != null && toBase != null && Math.abs(fromBase - toBase) > 0.05;
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

        log.debug("Created line branch: {} ({}→{}) r={} x={} bch={} PU",
            name, fromBusId, toBusId, rPU, xPU, bPU);
    }

    /**
     * Map SeriesCompensator as a line (PowSyBl behavior).
     */
    public void mapSeriesCompensator(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String lineId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = lineId;

        double r = bag.getDouble("SeriesCompensator.r", 0.0);
        double x = bag.getDouble("SeriesCompensator.x", 0.0);

        String[] busIds = resolveBranchBusIds(bag.getId());
        String fromBusId = busIds[0];
        String toBusId = busIds[1];

        if (fromBusId == null || toBusId == null) {
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

        // 0.0001 pu keeps 100 MW inside 0.01°. Small enough to match SV, large
        // enough that NR does not treat the branch as a singular zero-Z row.
        final double xPu = 1.0e-4;
        Double fromBase = busBaseKV(builder, fromBusId);
        Double toBase = busBaseKV(builder, toBusId);
        boolean crossVoltage = fromBase != null && toBase != null && Math.abs(fromBase - toBase) > 0.05;
        AclfBranch branch;
        if (crossVoltage) {
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
            branch = builder.addLine(fromBusId, toBusId, cirId,
                    new Complex(0.0, xPu),
                    new Complex(0.0, 0.0),
                    null, null, 0.0, 0.0, 0.0, true);
        }
        branch.setId(lineId);
        branch.setName(name.isEmpty() ? lineId : name);
        log.debug("Created closed switch as tie: {} ({}→{})", name, fromBusId, toBusId);
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
