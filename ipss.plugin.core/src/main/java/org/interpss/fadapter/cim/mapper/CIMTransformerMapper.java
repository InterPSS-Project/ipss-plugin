/*
 * CIMTransformerMapper.java
 *
 * Maps CIM PowerTransformer + PowerTransformerEnd → 2W xfr branch.
 */

package org.interpss.fadapter.cim.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CIMPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfBranch;

/**
 * Maps CIM PowerTransformer (2-winding) to an AclfNetwork transformer.
 */
public class CIMTransformerMapper extends AbstractCIMDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CIMTransformerMapper.class);

    private final double baseMVA;
    private final Map<String, List<CIMPropertyBag>> endsByTransformer = new HashMap<>();

    public CIMTransformerMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public void indexEnds(List<CIMPropertyBag> ends) {
        for (CIMPropertyBag end : ends) {
            String xfrId = end.getResourceId("PowerTransformerEnd.PowerTransformer");
            if (xfrId != null) {
                endsByTransformer.computeIfAbsent(xfrId, k -> new ArrayList<>()).add(end);
            }
        }
        log.debug("Indexed transformer ends: {} transformers", endsByTransformer.size());
    }

    @Override
    public void map(CIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String xfrId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = xfrId;

        List<CIMPropertyBag> ends = endsByTransformer.get(bag.getId());
        if (ends == null || ends.size() < 2) {
            log.warn("Skipping transformer {} - insufficient ends ({})", name,
                ends == null ? 0 : ends.size());
            return;
        }

        ends.sort((a, b) -> {
            int ea = a.getInt("TransformerEnd.endNumber",
                a.getInt("PowerTransformerEnd.endNumber", 1));
            int eb = b.getInt("TransformerEnd.endNumber",
                b.getInt("PowerTransformerEnd.endNumber", 1));
            return Integer.compare(ea, eb);
        });

        CIMPropertyBag end1 = ends.get(0);
        CIMPropertyBag end2 = ends.get(1);

        double ratedU1 = end1.getDouble("PowerTransformerEnd.ratedU",
                            end1.getDouble("TransformerEnd.ratedU", 0.0));
        double ratedU2 = end2.getDouble("PowerTransformerEnd.ratedU",
                            end2.getDouble("TransformerEnd.ratedU", 0.0));

        double r1 = end1.getDouble("PowerTransformerEnd.r", end1.getDouble("TransformerEnd.r", 0.0));
        double x1 = end1.getDouble("PowerTransformerEnd.x", end1.getDouble("TransformerEnd.x", 0.0));
        double r2 = end2.getDouble("PowerTransformerEnd.r", end2.getDouble("TransformerEnd.r", 0.0));
        double x2 = end2.getDouble("PowerTransformerEnd.x", end2.getDouble("TransformerEnd.x", 0.0));
        double r = r1 + r2;
        double x = x1 + x2;

        String[] busIds = resolveBranchBusIds(bag.getId());
        String fromBusId = busIds[0];
        String toBusId = busIds[1];

        if (fromBusId == null || toBusId == null) {
            log.warn("Skipping transformer {} - cannot resolve buses (from={}, to={})",
                name, fromBusId, toBusId);
            return;
        }

        // Prefer bus base voltages for Z/tap — topology node order may not match end order
        Double baseKV_from = busBaseKV(builder, fromBusId);
        Double baseKV_to = busBaseKV(builder, toBusId);
        if (baseKV_from == null && cimModel != null) {
            java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(bag.getId());
            if (!topoNodes.isEmpty()) {
                baseKV_from = cimModel.getNominalVoltageForTopoNode(topoNodes.get(0));
            }
        }
        if (baseKV_to == null && cimModel != null) {
            java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(bag.getId());
            if (topoNodes.size() >= 2) {
                baseKV_to = cimModel.getNominalVoltageForTopoNode(topoNodes.get(1));
            }
        }
        if (baseKV_from == null || baseKV_from == 0.0) baseKV_from = ratedU1 > 0 ? ratedU1 : 100.0;
        if (baseKV_to == null || baseKV_to == 0.0) baseKV_to = ratedU2 > 0 ? ratedU2 : 100.0;

        // Z on from-side voltage base (system MVA)
        double baseZ = baseKV_from * baseKV_from / baseMVA;
        double rPU = r / baseZ;
        double xPU = x / baseZ;

        // Off-nominal taps relative to each bus base voltage (normally ~1.0)
        double fromTurnRatio = ratedU1 > 0 ? ratedU1 / baseKV_from : 1.0;
        double toTurnRatio = ratedU2 > 0 ? ratedU2 / baseKV_to : 1.0;
        fromTurnRatio = clampTap(fromTurnRatio);
        toTurnRatio = clampTap(toTurnRatio);

        String cirId = nextCircuitId(builder, fromBusId, toBusId);
        if (cirId == null) {
            log.warn("Skipping transformer {} - too many parallel circuits", name);
            return;
        }

        AclfBranch branch = builder.addXformer2W(fromBusId, toBusId, cirId,
                new Complex(rPU, xPU), fromTurnRatio, toTurnRatio,
                null, null, 0.0, 0.0, 0.0, 0, true);
        branch.setId(xfrId);
        branch.setName(name.isEmpty() ? xfrId : name);

        log.info("Created xfr branch: {} ({}→{}) ratedU1={:.1f} ratedU2={:.1f} r={:.6f} x={:.6f} PU",
            name, fromBusId, toBusId, ratedU1, ratedU2, rPU, xPU);
    }

    private static Double busBaseKV(AclfNetworkBuilder builder, String busId) {
        if (busId == null) return null;
        var bus = builder.getBus(busId);
        if (bus == null || bus.getBaseVoltage() <= 0) return null;
        return bus.getBaseVoltage() / 1000.0;
    }

    /** InterPSS rejects taps outside (0, 2]; fall back to 1.0 when data is inconsistent. */
    private static double clampTap(double tap) {
        if (tap <= 0.0 || tap > 2.0) {
            log.warn("Transformer tap {} outside (0,2] — using 1.0", tap);
            return 1.0;
        }
        return tap;
    }
}
