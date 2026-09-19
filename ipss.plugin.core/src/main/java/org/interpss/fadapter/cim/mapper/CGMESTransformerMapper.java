/*
 * CGMESTransformerMapper.java
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
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.interpss.fadapter.cim.util.CGMESUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfBranch;

/**
 * Maps CIM PowerTransformer (2-winding) to an AclfNetwork transformer.
 * <p>
 * Impedance may be on {@code PowerTransformerEnd.r/x} (CGMES EQ style) or on
 * {@code TransformerMeshImpedance} between ends (common for converted models).
 * {@code ratedU} / {@code ratedS} are normalized from SI (V / VA) when needed.
 */
public class CGMESTransformerMapper extends AbstractCGMESDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CGMESTransformerMapper.class);

    private final double baseMVA;
    private final Map<String, List<CGMESPropertyBag>> endsByTransformer = new HashMap<>();
    /** Mesh impedance keyed by FromTransformerEnd URI. */
    private final Map<String, CGMESPropertyBag> meshByFromEnd = new HashMap<>();
    /** Core admittance keyed by TransformerEnd URI. */
    private final Map<String, CGMESPropertyBag> coreByEnd = new HashMap<>();

    public CGMESTransformerMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public void indexEnds(List<CGMESPropertyBag> ends) {
        for (CGMESPropertyBag end : ends) {
            String xfrId = end.getResourceId("PowerTransformerEnd.PowerTransformer");
            if (xfrId != null) {
                endsByTransformer.computeIfAbsent(xfrId, k -> new ArrayList<>()).add(end);
            }
        }
        log.debug("Indexed transformer ends: {} transformers", endsByTransformer.size());
    }

    public void indexMeshImpedances(List<CGMESPropertyBag> meshes) {
        meshByFromEnd.clear();
        for (CGMESPropertyBag mesh : meshes) {
            String fromEnd = mesh.getResourceId("TransformerMeshImpedance.FromTransformerEnd");
            if (fromEnd != null) {
                meshByFromEnd.put(fromEnd, mesh);
            }
        }
        log.debug("Indexed {} transformer mesh impedances", meshByFromEnd.size());
    }

    public void indexCoreAdmittances(List<CGMESPropertyBag> cores) {
        coreByEnd.clear();
        for (CGMESPropertyBag core : cores) {
            String end = core.getResourceId("TransformerCoreAdmittance.TransformerEnd");
            if (end != null) {
                coreByEnd.put(end, core);
            }
        }
    }

    @Override
    public void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String xfrId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = xfrId;

        List<CGMESPropertyBag> ends = endsByTransformer.get(bag.getId());
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

        CGMESPropertyBag end1 = ends.get(0);
        CGMESPropertyBag end2 = ends.get(1);

        double ratedU1 = CGMESUnitConverter.toKV(end1.getDouble("PowerTransformerEnd.ratedU",
                            end1.getDouble("TransformerEnd.ratedU", 0.0)));
        double ratedU2 = CGMESUnitConverter.toKV(end2.getDouble("PowerTransformerEnd.ratedU",
                            end2.getDouble("TransformerEnd.ratedU", 0.0)));

        double r1 = end1.getDouble("PowerTransformerEnd.r", end1.getDouble("TransformerEnd.r", 0.0));
        // Missing PowerTransformerEnd.x → NaN so we can detect "not provided"
        Double x1Obj = endHasX(end1) ? end1.getDouble("PowerTransformerEnd.x",
                end1.getDouble("TransformerEnd.x", 0.0)) : null;
        double r2 = end2.getDouble("PowerTransformerEnd.r", end2.getDouble("TransformerEnd.r", 0.0));
        Double x2Obj = endHasX(end2) ? end2.getDouble("PowerTransformerEnd.x",
                end2.getDouble("TransformerEnd.x", 0.0)) : null;
        double r = r1 + r2;
        double x = (x1Obj != null ? x1Obj : 0.0) + (x2Obj != null ? x2Obj : 0.0);
        boolean endXMissing = x1Obj == null && x2Obj == null;

        // IEEE118 / CIM Hub: ends often carry tiny winding r with no x; series Z is on mesh
        CGMESPropertyBag mesh = meshByFromEnd.get(end1.getId());
        if (mesh == null) {
            mesh = meshByFromEnd.get(end2.getId());
        }
        if (mesh != null && (endXMissing || Math.abs(r) + Math.abs(x) == 0.0)) {
            r = mesh.getDouble("TransformerMeshImpedance.r", 0.0);
            x = mesh.getDouble("TransformerMeshImpedance.x", 0.0);
        }

        // Resolve buses from winding terminals so from-side matches end1 (Z reference)
        String fromBusId = resolveBusIdFromEnd(end1);
        String toBusId = resolveBusIdFromEnd(end2);
        if (fromBusId == null || toBusId == null) {
            String[] busIds = resolveBranchBusIds(bag.getId());
            if (fromBusId == null) fromBusId = busIds[0];
            if (toBusId == null) toBusId = busIds[1];
        }

        if (fromBusId == null || toBusId == null) {
            log.warn("Skipping transformer {} - cannot resolve buses (from={}, to={})",
                name, fromBusId, toBusId);
            return;
        }

        // Mesh / winding Z is in ohms on the from-end (end1) voltage base
        double zBaseKV = ratedU1 > 0 ? ratedU1 : 100.0;
        Double busFrom = busBaseKV(builder, fromBusId);
        if (ratedU1 <= 0 && busFrom != null && busFrom > 0) zBaseKV = busFrom;
        double baseZ = zBaseKV * zBaseKV / baseMVA;
        double rPU = r / baseZ;
        double xPU = x / baseZ;

        // Voltage levels from bus bases + Z on ratedU; taps/angles from Ratio/PhaseTapChanger.
        double fromTurnRatio = ratioTapForEnd(end1);
        double toTurnRatio = ratioTapForEnd(end2);
        double fromAngleDeg = phaseShiftDegForEnd(end1);
        double toAngleDeg = phaseShiftDegForEnd(end2);

        double ratingMva = CGMESUnitConverter.apparentPowerToMVA(
                end1.getDouble("PowerTransformerEnd.ratedS",
                    end2.getDouble("PowerTransformerEnd.ratedS", 0.0)));

        Complex magY = null;
        CGMESPropertyBag core = coreByEnd.get(end1.getId());
        if (core == null) core = coreByEnd.get(end2.getId());
        if (core != null) {
            double g = core.getDouble("TransformerCoreAdmittance.g", 0.0);
            double b = core.getDouble("TransformerCoreAdmittance.b", 0.0);
            if (g != 0.0 || b != 0.0) {
                double baseY = baseMVA / (zBaseKV * zBaseKV);
                magY = new Complex(g / baseY, b / baseY);
            }
        }

        String cirId = nextCircuitId(builder, fromBusId, toBusId);
        if (cirId == null) {
            log.warn("Skipping transformer {} - too many parallel circuits", name);
            return;
        }

        AclfBranch branch;
        boolean isPs = Math.abs(fromAngleDeg) > 1e-9 || Math.abs(toAngleDeg) > 1e-9;
        if (isPs) {
            branch = builder.addPsXformer(fromBusId, toBusId, cirId,
                    new Complex(rPU, xPU), fromTurnRatio, toTurnRatio,
                    fromAngleDeg, toAngleDeg,
                    magY, null, ratingMva, 0.0, 0.0, 0, true);
        } else {
            branch = builder.addXformer2W(fromBusId, toBusId, cirId,
                    new Complex(rPU, xPU), fromTurnRatio, toTurnRatio,
                    magY, null, ratingMva, 0.0, 0.0, 0, true);
        }
        branch.setId(xfrId);
        branch.setName(name.isEmpty() ? xfrId : name);

        log.debug("Created xfr branch: {} ({}→{}) ratedU1={} ratedU2={} r={} x={} PU rating={} MVA ps={} ang={}/{}",
            name, fromBusId, toBusId, ratedU1, ratedU2, rPU, xPU, ratingMva, isPs, fromAngleDeg, toAngleDeg);
    }


    private String resolveBusIdFromEnd(CGMESPropertyBag end) {
        if (cimModel == null || end == null) return null;
        String termId = end.getResourceId("TransformerEnd.Terminal");
        if (termId == null) return null;
        String tn = cimModel.getTopologicalNodeByTerminal(termId);
        if (tn != null) return cimModel.getBusId(tn);
        String cn = cimModel.getConnectivityNodeByTerminal(termId);
        if (cn != null) return cimModel.getBusId(cn);
        return null;
    }

    private static boolean endHasX(CGMESPropertyBag end) {
        return end.getString("PowerTransformerEnd.x") != null
                || end.getString("TransformerEnd.x") != null;
    }
}
