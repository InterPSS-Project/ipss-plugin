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
        if (!bag.getBoolean("Equipment.inService", true)) {
            log.debug("Skipping out-of-service PowerTransformer {}", bag.getName());
            return;
        }

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
        r1 = applyRatioTableOhm(end1, "r", r1);
        // Missing PowerTransformerEnd.x → NaN so we can detect "not provided"
        Double x1Obj = endHasX(end1) ? end1.getDouble("PowerTransformerEnd.x",
                end1.getDouble("TransformerEnd.x", 0.0)) : null;
        if (x1Obj != null) x1Obj = applyRatioTableOhm(end1, "x", x1Obj);
        if (x1Obj != null) x1Obj = phaseTapSeriesOhm(end1, x1Obj);
        double r2 = end2.getDouble("PowerTransformerEnd.r", end2.getDouble("TransformerEnd.r", 0.0));
        r2 = applyRatioTableOhm(end2, "r", r2);
        Double x2Obj = endHasX(end2) ? end2.getDouble("PowerTransformerEnd.x",
                end2.getDouble("TransformerEnd.x", 0.0)) : null;
        if (x2Obj != null) x2Obj = applyRatioTableOhm(end2, "x", x2Obj);
        if (x2Obj != null) x2Obj = phaseTapSeriesOhm(end2, x2Obj);
        PhaseTapResult tap1 = phaseTapForEnd(end1);
        PhaseTapResult tap2 = phaseTapForEnd(end2);
        r1 = applyPercentDeviation(r1, tap1.rPercent);
        r2 = applyPercentDeviation(r2, tap2.rPercent);
        if (x1Obj != null) x1Obj = applyPercentDeviation(x1Obj, tap1.xPercent);
        if (x2Obj != null) x2Obj = applyPercentDeviation(x2Obj, tap2.xPercent);
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

        // Z reference stays endNumber 1. From/to follow terminal sequence so
        // SvPowerFlow sequence 1 is InterPSS powerFrom2To. endNumber and
        // sequenceNumber disagree on MiniGrid T1.
        CGMESPropertyBag fromEnd = end1;
        CGMESPropertyBag toEnd = end2;
        if (terminalSequence(end2) < terminalSequence(end1)) {
            fromEnd = end2;
            toEnd = end1;
        }
        String fromBusId = resolveBusIdFromEnd(fromEnd);
        String toBusId = resolveBusIdFromEnd(toEnd);
        if (fromBusId == null || toBusId == null) {
            String[] busIds = resolveBranchBusIds(bag.getId());
            if (fromBusId == null) fromBusId = busIds[0];
            if (toBusId == null) toBusId = busIds[1];
        }

        if (fromBusId == null || toBusId == null) {
            if (isUnresolvedTopologyExpected(bag.getId())) {
                log.debug("Skipping transformer {} - out of topology / no TP TopologicalNode (from={}, to={})",
                    name, fromBusId, toBusId);
            } else {
                log.warn("Skipping transformer {} - cannot resolve buses (from={}, to={})",
                    name, fromBusId, toBusId);
            }
            return;
        }

        // Mesh / winding Z is in ohms on the from-end (end1) voltage base
        double zBaseKV = ratedU1 > 0 ? ratedU1 : 100.0;
        Double busFrom = busBaseKV(builder, fromBusId);
        if (ratedU1 <= 0 && busFrom != null && busFrom > 0) zBaseKV = busFrom;
        double baseZ = zBaseKV * zBaseKV / baseMVA;
        double rPU = r / baseZ;
        double xPU = x / baseZ;

        // ratedU/base multiplies the winding that owns it, including a 1–3% scale.
        // Folding that scale onto the other tap keeps the from-tap unit assert but
        // misses BE-TR2_2 Q by ~1 Mvar and BE-TR2_3 Q by ~8 Mvar.
        double fromTurnRatio = windingTurnRatio(fromEnd, busBaseKV(builder, fromBusId));
        double toTurnRatio = windingTurnRatio(toEnd, busBaseKV(builder, toBusId));
        double fromAngleDeg = windingAngleDeg(fromEnd);
        double toAngleDeg = windingAngleDeg(toEnd);

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
        Complex fromMag = endMagnetizingPu(fromEnd);
        Complex toMag = endMagnetizingPu(toEnd);
        if (magY != null) {
            fromMag = fromMag == null ? magY : fromMag.add(magY);
        }
        // SvPowerFlow on MicroGrid matches when a single-end magnetizing branch is
        // split across the two terminals. All of it on the owning end misses Q by
        // about half the magnetizing Mvar (3 Mvar on NL_TR2_3, 0.5 Mvar on BE-TR2_3).
        if (fromMag != null && toMag == null) {
            fromMag = fromMag.multiply(0.5);
            toMag = fromMag;
        } else if (toMag != null && fromMag == null) {
            toMag = toMag.multiply(0.5);
            fromMag = toMag;
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
                    fromMag, toMag, ratingMva, 0.0, 0.0, 0, true);
        } else {
            branch = builder.addXformer2W(fromBusId, toBusId, cirId,
                    new Complex(rPU, xPU), fromTurnRatio, toTurnRatio,
                    fromMag, toMag, ratingMva, 0.0, 0.0, 0, true);
        }
        branch.setId(xfrId);
        branch.setName(name.isEmpty() ? xfrId : name);

        log.debug("Created xfr branch: {} ({}→{}) ratedU1={} ratedU2={} r={} x={} PU rating={} MVA ps={} ang={}/{}",
            name, fromBusId, toBusId, ratedU1, ratedU2, rPU, xPU, ratingMva, isPs, fromAngleDeg, toAngleDeg);
    }

    /**
     * PowerTransformerEnd.g/b is the magnetizing branch in siemens on that
     * winding. Converted on ratedU so the tap (ratedU/base) puts it on the bus base.
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

    private int terminalSequence(CGMESPropertyBag end) {
        if (cimModel == null || end == null) return Integer.MAX_VALUE;
        return cimModel.terminalSequence(end.getResourceId("TransformerEnd.Terminal"));
    }
}
