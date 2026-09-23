/*
 * CGMESTransformer3WMapper.java
 *
 * Maps CIM PowerTransformer with 3 ends → Aclf3WBranch (star-bus model).
 */

package org.interpss.fadapter.cim.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.interpss.fadapter.cim.util.CGMESUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;

/**
 * Maps CIM 3-winding PowerTransformer using the star-bus equivalent impedance model.
 */
public class CGMESTransformer3WMapper extends AbstractCGMESDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CGMESTransformer3WMapper.class);

    private final double baseMVA;
    /** Mesh impedance keyed by from-end local id + '#' + to-end local id. */
    private final Map<String, CGMESPropertyBag> meshByEndPair = new HashMap<>();

    public CGMESTransformer3WMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public void indexMeshImpedances(List<CGMESPropertyBag> meshes) {
        meshByEndPair.clear();
        for (CGMESPropertyBag mesh : meshes) {
            String fromEnd = mesh.getResourceId("TransformerMeshImpedance.FromTransformerEnd");
            String toEnd = mesh.getResourceId("TransformerMeshImpedance.ToTransformerEnd");
            if (fromEnd == null || toEnd == null) continue;
            String fromLocal = CGMESPropertyBag.extractLocal(fromEnd);
            String toLocal = CGMESPropertyBag.extractLocal(toEnd);
            if (fromLocal == null || toLocal == null) continue;
            meshByEndPair.put(fromLocal + "#" + toLocal, mesh);
        }
        log.debug("Indexed {} 3W transformer mesh impedances", meshByEndPair.size());
    }

    @Override
    public void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        throw new UnsupportedOperationException("Use map3W for 3-winding transformers");
    }

    public void map3W(CGMESPropertyBag bag, List<CGMESPropertyBag> sortedEnds, AclfNetworkBuilder builder) throws Exception {
        String xfrId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = xfrId;

        if (sortedEnds.size() != 3) {
            log.warn("3W transformer {} has {} ends, expected 3 — skipping", name, sortedEnds.size());
            return;
        }

        CGMESPropertyBag end1 = sortedEnds.get(0);
        CGMESPropertyBag end2 = sortedEnds.get(1);
        CGMESPropertyBag end3 = sortedEnds.get(2);

        // Keep endNumber order (1 = from). Parallel units such as MiniGrid T3/T4
        // assign sequenceNumber differently; flipping one of them changes the star-bus
        // base and circulates reactive power that SV does not have.
        double ratedU1 = getRatedU(end1);
        double ratedU2 = getRatedU(end2);
        double ratedU3 = getRatedU(end3);

        Complex zFromTo;
        Complex zToTert;
        Complex zFromTert;
        boolean windingXMissing = !endHasX(end1) && !endHasX(end2) && !endHasX(end3);
        Complex star1 = windingPu(getR(end1), getX(end1), ratedU1);
        Complex star2 = windingPu(getR(end2), getX(end2), ratedU2);
        Complex star3 = windingPu(getR(end3), getX(end3), ratedU3);
        boolean windingZZero = star1.abs() + star2.abs() + star3.abs() == 0.0;
        if ((windingXMissing || windingZZero) && hasMesh(end1, end2, end3)) {
            zFromTo = meshPu(end1, end2, ratedU1, ratedU2);
            zToTert = meshPu(end2, end3, ratedU2, ratedU3);
            zFromTert = meshPu(end1, end3, ratedU1, ratedU3);
        } else {
            zFromTo = star1.add(star2);
            zToTert = star2.add(star3);
            zFromTert = star3.add(star1);
        }

        String bus1Id = resolveBusIdFromEnd(end1);
        String bus2Id = resolveBusIdFromEnd(end2);
        String bus3Id = resolveBusIdFromEnd(end3);

        if (bus1Id == null || bus2Id == null || bus3Id == null) {
            if (isUnresolvedTopologyExpected(bag.getId())) {
                log.debug("Skipping 3W transformer {} - out of topology / no TP TopologicalNode (bus1={}, bus2={}, bus3={})",
                    name, bus1Id, bus2Id, bus3Id);
            } else {
                log.warn("Skipping 3W transformer {} - cannot resolve all buses (bus1={}, bus2={}, bus3={})",
                    name, bus1Id, bus2Id, bus3Id);
            }
            return;
        }

        double fromTurnRatio = windingTurnRatio(end1, busBaseKV(builder, bus1Id), false);
        double toTurnRatio = windingTurnRatio(end2, busBaseKV(builder, bus2Id), false);
        double tertTurnRatio = windingTurnRatio(end3, busBaseKV(builder, bus3Id), false);
        double fromAngleDeg = windingAngleDeg(end1);
        double toAngleDeg = windingAngleDeg(end2);
        double tertAngleDeg = windingAngleDeg(end3);
        boolean isPs = Math.abs(fromAngleDeg) > 1e-9
                || Math.abs(toAngleDeg) > 1e-9
                || Math.abs(tertAngleDeg) > 1e-9;

        Exception last = null;
        for (int ci = 1; ci <= 10; ci++) {
            String cirId = String.valueOf(ci);
            try {
                Aclf3WBranch branch = builder.addXformer3W(bus1Id, bus2Id, bus3Id, cirId,
                        zFromTo, zToTert, zFromTert,
                        fromTurnRatio, toTurnRatio, tertTurnRatio,
                        null, 1.0, 0.0,
                        false, false, false,
                        isPs, fromAngleDeg, toAngleDeg, tertAngleDeg,
                        true);
                branch.setId(xfrId);
                branch.setName(name.isEmpty() ? xfrId : name);
                // Each leg's terminal shunt. The from leg's terminal is its from bus;
                // the to and tertiary legs run star → terminal, so their shunt is on the to side.
                setTerminalShunt(branch.getFromAclfBranch(), endMagnetizingPu(end1), true);
                setTerminalShunt(branch.getToAclfBranch(), endMagnetizingPu(end2), false);
                setTerminalShunt(branch.getTertAclfBranch(), endMagnetizingPu(end3), false);
                log.debug("Created 3W xfr branch: {} ({}→{}→{}) ratedU={}/{}/{} z12={} ps={} ang={}/{}/{}",
                    name, bus1Id, bus2Id, bus3Id, ratedU1, ratedU2, ratedU3, zFromTo,
                    isPs, fromAngleDeg, toAngleDeg, tertAngleDeg);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        log.warn("Skipping 3W transformer {} - too many parallel circuits{}",
                name, last == null ? "" : ": " + last.getMessage());
    }

    private Complex windingPu(double rOhm, double xOhm, double ratedUKv) {
        double kv = ratedUKv > 0 ? ratedUKv : 100.0;
        double baseZ = kv * kv / baseMVA;
        return new Complex(rOhm / baseZ, xOhm / baseZ);
    }

    private boolean hasMesh(CGMESPropertyBag end1, CGMESPropertyBag end2, CGMESPropertyBag end3) {
        return meshBetween(end1, end2) != null
                || meshBetween(end2, end3) != null
                || meshBetween(end3, end1) != null;
    }

    private Complex meshPu(CGMESPropertyBag from, CGMESPropertyBag to, double fromRatedU, double toRatedU) {
        CGMESPropertyBag mesh = meshBetween(from, to);
        if (mesh == null) {
            return Complex.ZERO;
        }
        double r = mesh.getDouble("TransformerMeshImpedance.r", 0.0);
        double x = mesh.getDouble("TransformerMeshImpedance.x", 0.0);
        String fromEnd = mesh.getResourceId("TransformerMeshImpedance.FromTransformerEnd");
        String fromLocal = CGMESPropertyBag.extractLocal(fromEnd);
        double ratedU = fromLocal != null && fromLocal.equals(from.getLocalId()) ? fromRatedU : toRatedU;
        return windingPu(r, x, ratedU);
    }

    private CGMESPropertyBag meshBetween(CGMESPropertyBag a, CGMESPropertyBag b) {
        if (a == null || b == null) return null;
        CGMESPropertyBag mesh = meshByEndPair.get(a.getLocalId() + "#" + b.getLocalId());
        if (mesh == null) {
            mesh = meshByEndPair.get(b.getLocalId() + "#" + a.getLocalId());
        }
        return mesh;
    }

    /**
     * PowerTransformerEnd.g/b is the magnetizing branch in siemens on that
     * winding, converted on ratedU so the tap puts it on the bus base.
     */
    private Complex endMagnetizingPu(CGMESPropertyBag end) {
        if (end == null) return null;
        double g = end.getDouble("PowerTransformerEnd.g", 0.0);
        double b = end.getDouble("PowerTransformerEnd.b", 0.0);
        if (g == 0.0 && b == 0.0) return null;
        double ratedU = CGMESUnitConverter.toKV(end.getDouble("PowerTransformerEnd.ratedU",
                end.getDouble("TransformerEnd.ratedU", 0.0)));
        if (ratedU <= 0.0) ratedU = 100.0;
        double baseY = baseMVA / (ratedU * ratedU);
        return new Complex(g / baseY, b / baseY);
    }

    private static void setTerminalShunt(AclfBranch leg, Complex magPu, boolean onFromSide) {
        if (leg == null || magPu == null) return;
        if (onFromSide) {
            leg.setFromShuntY(magPu);
        } else {
            leg.setToShuntY(magPu);
        }
    }

    private double getRatedU(CGMESPropertyBag end) {
        return CGMESUnitConverter.toKV(end.getDouble("PowerTransformerEnd.ratedU",
                end.getDouble("TransformerEnd.ratedU", 0.0)));
    }

    private double getR(CGMESPropertyBag end) {
        double r = end.getDouble("PowerTransformerEnd.r",
                end.getDouble("TransformerEnd.r", 0.0));
        return applyRatioTableOhm(end, "r", r);
    }

    private double getX(CGMESPropertyBag end) {
        double x = end.getDouble("PowerTransformerEnd.x",
                end.getDouble("TransformerEnd.x", 0.0));
        return phaseTapSeriesOhm(end, applyRatioTableOhm(end, "x", x));
    }
}
