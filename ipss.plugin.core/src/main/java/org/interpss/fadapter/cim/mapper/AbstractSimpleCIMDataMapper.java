/*
 * AbstractSimpleCIMDataMapper.java
 *
 * Base mapper for converting CIM elements via AclfNetworkBuilder.
 */

package org.interpss.fadapter.cim.mapper;

import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.SimpleCIMModel;
import org.interpss.fadapter.cim.SimpleCIMPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for CIM → AclfNetwork data mappers.
 */
public abstract class AbstractSimpleCIMDataMapper {
    protected static final Logger log = LoggerFactory.getLogger(AbstractSimpleCIMDataMapper.class);

    protected SimpleCIMModel cimModel;

    public void setCimModel(SimpleCIMModel model) {
        this.cimModel = model;
    }

    /**
     * Map a CIM property bag into the network via the builder.
     */
    public abstract void map(SimpleCIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception;

    /**
     * Resolve the bus ID for a conducting equipment by finding its
     * connected topological node and looking up the mapped bus ID.
     */
    public String resolveBusId(String equipmentId) {
        if (cimModel == null) return null;
        java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(equipmentId);
        if (!topoNodes.isEmpty()) {
            return cimModel.getBusId(topoNodes.get(0));
        }
        return null;
    }

    /**
     * Resolve the two bus IDs for a branch (line or transformer).
     */
    protected String[] resolveBranchBusIds(String equipmentId) {
        if (cimModel == null) return new String[]{null, null};
        java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(equipmentId);
        String bus1 = null, bus2 = null;
        if (topoNodes.size() >= 2) {
            bus1 = cimModel.getBusId(topoNodes.get(0));
            bus2 = cimModel.getBusId(topoNodes.get(1));
        } else if (topoNodes.size() == 1) {
            bus1 = cimModel.getBusId(topoNodes.get(0));
        }
        return new String[]{bus1, bus2};
    }

    /**
     * Find an unused circuit ID for a branch between two buses (tries 1–10).
     */
    protected String nextCircuitId(AclfNetworkBuilder builder, String fromBusId, String toBusId) {
        for (int ci = 1; ci <= 10; ci++) {
            String cirId = String.valueOf(ci);
            if (builder.getNetwork().getBranch(fromBusId, toBusId, cirId) == null
                    && builder.getNetwork().getBranch(toBusId, fromBusId, cirId) == null) {
                return cirId;
            }
        }
        return null;
    }
}
