/*
 * CIMTransformer3WMapper.java
 *
 * Maps CIM PowerTransformer with 3 ends → Aclf3WBranch (star-bus model).
 */

package org.interpss.fadapter.cim.mapper;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CIMPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.Aclf3WBranch;

/**
 * Maps CIM 3-winding PowerTransformer using the star-bus equivalent impedance model.
 */
public class CIMTransformer3WMapper extends AbstractCIMDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CIMTransformer3WMapper.class);

    private final double baseMVA;

    public CIMTransformer3WMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    @Override
    public void map(CIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        throw new UnsupportedOperationException("Use map3W for 3-winding transformers");
    }

    public void map3W(CIMPropertyBag bag, List<CIMPropertyBag> sortedEnds, AclfNetworkBuilder builder) throws Exception {
        String xfrId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = xfrId;

        if (sortedEnds.size() != 3) {
            log.warn("3W transformer {} has {} ends, expected 3 — skipping", name, sortedEnds.size());
            return;
        }

        CIMPropertyBag end1 = sortedEnds.get(0);
        CIMPropertyBag end2 = sortedEnds.get(1);
        CIMPropertyBag end3 = sortedEnds.get(2);

        double ratedU1 = getRatedU(end1);
        double ratedU2 = getRatedU(end2);
        double ratedU3 = getRatedU(end3);

        double r1 = getR(end1);
        double x1 = getX(end1);
        double r2 = getR(end2);
        double x2 = getX(end2);
        double r3 = getR(end3);
        double x3 = getX(end3);

        double ratedU0 = ratedU1;

        double z1_pu_r = r1 * baseMVA / (ratedU0 * ratedU0);
        double z1_pu_x = x1 * baseMVA / (ratedU0 * ratedU0);
        double z2_pu_r = r2 * baseMVA / (ratedU0 * ratedU0);
        double z2_pu_x = x2 * baseMVA / (ratedU0 * ratedU0);
        double z3_pu_r = r3 * baseMVA / (ratedU0 * ratedU0);
        double z3_pu_x = x3 * baseMVA / (ratedU0 * ratedU0);

        double z12_r = z1_pu_r + z2_pu_r;
        double z12_x = z1_pu_x + z2_pu_x;
        double z23_r = z2_pu_r + z3_pu_r;
        double z23_x = z2_pu_x + z3_pu_x;
        double z31_r = z3_pu_r + z1_pu_r;
        double z31_x = z3_pu_x + z1_pu_x;

        String[] busIds = resolveBranchBusIds(bag.getId());
        String bus1Id = busIds[0];
        String bus2Id = busIds[1];
        String bus3Id = resolveBusIdForEnd(bag.getId(), 3, sortedEnds);

        if (bus1Id == null || bus2Id == null || bus3Id == null) {
            log.warn("Skipping 3W transformer {} - cannot resolve all buses (bus1={}, bus2={}, bus3={})",
                name, bus1Id, bus2Id, bus3Id);
            return;
        }

        Double nominalV1 = busBaseKV(builder, bus1Id);
        Double nominalV2 = busBaseKV(builder, bus2Id);
        Double nominalV3 = busBaseKV(builder, bus3Id);
        if (nominalV1 == null) nominalV1 = getNominalVForBus(bus1Id);
        if (nominalV2 == null) nominalV2 = getNominalVForBus(bus2Id);
        if (nominalV3 == null) nominalV3 = getNominalVForBus(bus3Id);

        if (nominalV1 == null || nominalV1 == 0.0) nominalV1 = ratedU1 > 0 ? ratedU1 : 100.0;
        if (nominalV2 == null || nominalV2 == 0.0) nominalV2 = ratedU2 > 0 ? ratedU2 : 100.0;
        if (nominalV3 == null || nominalV3 == 0.0) nominalV3 = ratedU3 > 0 ? ratedU3 : 100.0;

        double fromTurnRatio = clampTap(ratedU1 > 0 ? ratedU1 / nominalV1 : 1.0);
        double toTurnRatio = clampTap(ratedU2 > 0 ? ratedU2 / nominalV2 : 1.0);
        double tertTurnRatio = clampTap(ratedU3 > 0 ? ratedU3 / nominalV3 : 1.0);

        String cirId = "1";
        for (int ci = 1; ci <= 10; ci++) {
            cirId = String.valueOf(ci);
            try {
                Aclf3WBranch branch = builder.addXformer3W(bus1Id, bus2Id, bus3Id, cirId,
                        new Complex(z12_r, z12_x),
                        new Complex(z23_r, z23_x),
                        new Complex(z31_r, z31_x),
                        fromTurnRatio, toTurnRatio, tertTurnRatio,
                        null, 1.0, 0.0,
                        false, false, false,
                        false, 0.0, 0.0, 0.0,
                        true);
                branch.setId(xfrId);
                branch.setName(name.isEmpty() ? xfrId : name);
                log.info("Created 3W xfr branch: {} ({}→{}→{}) ratedU={:.1f}/{:.1f}/{:.1f} z12={:.6f}+j{:.6f} PU",
                    name, bus1Id, bus2Id, bus3Id, ratedU1, ratedU2, ratedU3, z12_r, z12_x);
                return;
            } catch (Exception e) {
                // parallel or conflict — try next circuit ID
            }
        }
        log.warn("Skipping 3W transformer {} - too many parallel circuits", name);
    }

    private double getRatedU(CIMPropertyBag end) {
        return end.getDouble("PowerTransformerEnd.ratedU",
                end.getDouble("TransformerEnd.ratedU", 0.0));
    }

    private double getR(CIMPropertyBag end) {
        return end.getDouble("PowerTransformerEnd.r",
                end.getDouble("TransformerEnd.r", 0.0));
    }

    private double getX(CIMPropertyBag end) {
        return end.getDouble("PowerTransformerEnd.x",
                end.getDouble("TransformerEnd.x", 0.0));
    }

    private String resolveBusIdForEnd(String xfrId, int endNumber, List<CIMPropertyBag> ends) {
        if (cimModel == null) return null;
        int idx = endNumber - 1;
        if (idx >= ends.size()) return null;

        java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(xfrId);
        if (topoNodes.size() >= endNumber) {
            String busId = cimModel.getBusId(topoNodes.get(idx));
            if (busId != null) return busId;
            return CIMPropertyBag.extractLocal(topoNodes.get(idx));
        }
        return null;
    }

    private Double getNominalVForBus(String busId) {
        if (cimModel == null) return null;
        return cimModel.getNominalVoltageForTopoNode(busId);
    }

    private static Double busBaseKV(AclfNetworkBuilder builder, String busId) {
        if (busId == null) return null;
        var bus = builder.getBus(busId);
        if (bus == null || bus.getBaseVoltage() <= 0) return null;
        return bus.getBaseVoltage() / 1000.0;
    }

    private static double clampTap(double tap) {
        if (tap <= 0.0 || tap > 2.0) {
            log.warn("3W transformer tap {} outside (0,2] — using 1.0", tap);
            return 1.0;
        }
        return tap;
    }
}
