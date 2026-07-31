/*
 * CIMLineMapper.java
 *
 * Maps CIM ACLineSegment → AclfNetwork line branch.
 */

package org.interpss.fadapter.cim.mapper;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CIMPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfBranch;

/**
 * Maps CIM ACLineSegment to an AclfNetwork line.
 * CIM stores R, X in Ohms and Gch, Bch in Siemens (total line charging).
 */
public class CIMLineMapper extends AbstractCIMDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CIMLineMapper.class);

    private final double baseMVA;

    public CIMLineMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    @Override
    public void map(CIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
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
            log.warn("Skipping line {} - cannot resolve bus connectivity (from={}, to={})", name, fromBusId, toBusId);
            return;
        }

        Double baseKV = resolveBaseKV(bag);
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

        AclfBranch branch = builder.addLine(fromBusId, toBusId, cirId,
                new Complex(rPU, xPU),
                new Complex(gPU * 0.5, bPU * 0.5),
                null, null, 0.0, 0.0, 0.0, true);
        branch.setId(lineId);
        branch.setName(name.isEmpty() ? lineId : name);

        log.debug("Created line branch: {} ({}→{}) r={} x={} bch={} PU",
            name, fromBusId, toBusId, rPU, xPU, bPU);
    }

    /**
     * Map SeriesCompensator as a line (PowSyBl behavior).
     */
    public void mapSeriesCompensator(CIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String lineId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = lineId;

        double r = bag.getDouble("SeriesCompensator.r", 0.0);
        double x = bag.getDouble("SeriesCompensator.x", 0.0);

        String[] busIds = resolveBranchBusIds(bag.getId());
        String fromBusId = busIds[0];
        String toBusId = busIds[1];

        if (fromBusId == null || toBusId == null) {
            log.warn("Skipping SeriesCompensator {} - cannot resolve bus (from={}, to={})", name, fromBusId, toBusId);
            return;
        }

        Double baseKV = resolveBaseKV(bag);
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

    private Double resolveBaseKV(CIMPropertyBag bag) {
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
            log.warn("Cannot determine base voltage for {}, using 100 kV", bag.getName());
            baseKV = 100.0;
        }
        return baseKV;
    }
}
