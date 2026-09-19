/*
 * AbstractCGMESDataMapper.java
 *
 * Base mapper for converting CIM elements via AclfNetworkBuilder.
 */

package org.interpss.fadapter.cim.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CGMESModel;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for CIM → AclfNetwork data mappers.
 */
public abstract class AbstractCGMESDataMapper {
    protected static final Logger log = LoggerFactory.getLogger(AbstractCGMESDataMapper.class);

    protected CGMESModel cimModel;

    /** RatioTapChanger bags keyed by TransformerEnd URI (and local id). */
    private final Map<String, CGMESPropertyBag> ratioTapByEnd = new HashMap<>();

    /** PhaseTapChanger bags keyed by TransformerEnd URI (and local id). */
    private final Map<String, CGMESPropertyBag> phaseTapByEnd = new HashMap<>();

    /**
     * Tabular phase points: tableLocalId|step → bag with angle/ratio.
     * Key format {@code localTableId + "#" + stepInt}.
     */
    private final Map<String, CGMESPropertyBag> phaseTablePointByTableStep = new HashMap<>();

    public void setCimModel(CGMESModel model) {
        this.cimModel = model;
    }

    /**
     * Index {@code RatioTapChanger} resources by their TransformerEnd.
     * EQ carries neutralStep / stepVoltageIncrement; SSH merges {@code TapChanger.step}
     * onto the same RDF id when profiles are loaded together.
     */
    public void indexRatioTapChangers(List<CGMESPropertyBag> tapChangers) {
        ratioTapByEnd.clear();
        if (tapChangers == null) return;
        for (CGMESPropertyBag rtc : tapChangers) {
            putByEnd(ratioTapByEnd, rtc, endUriOf(rtc, true));
        }
        log.debug("Indexed {} RatioTapChangers covering {} end keys",
                tapChangers.size(), ratioTapByEnd.size());
    }

    /**
     * Index phase tap changers (linear / symmetrical / asymmetrical / tabular)
     * and optional {@code PhaseTapChangerTablePoint} rows for tabular lookup.
     */
    public void indexPhaseTapChangers(List<CGMESPropertyBag> tapChangers,
                                      List<CGMESPropertyBag> tablePoints) {
        phaseTapByEnd.clear();
        phaseTablePointByTableStep.clear();
        if (tapChangers != null) {
            for (CGMESPropertyBag ptc : tapChangers) {
                putByEnd(phaseTapByEnd, ptc, endUriOf(ptc, false));
            }
        }
        if (tablePoints != null) {
            for (CGMESPropertyBag pt : tablePoints) {
                String table = pt.getResourceId("PhaseTapChangerTablePoint.PhaseTapChangerTable");
                if (table == null) {
                    table = pt.getResourceId("TapChangerTablePoint.TapChangerTable");
                }
                if (table == null) continue;
                String stepStr = pt.getString("PhaseTapChangerTablePoint.step");
                if (stepStr == null) stepStr = pt.getString("TapChangerTablePoint.step");
                if (stepStr == null || stepStr.isEmpty()) continue;
                int step = (int) Math.round(Double.parseDouble(stepStr.trim()));
                String local = CGMESPropertyBag.extractLocal(table);
                phaseTablePointByTableStep.put(local + "#" + step, pt);
            }
        }
        log.debug("Indexed {} PhaseTapChangers / {} table points",
                phaseTapByEnd.size() / 3, phaseTablePointByTableStep.size());
    }

    private static void putByEnd(Map<String, CGMESPropertyBag> map, CGMESPropertyBag bag, String endUri) {
        if (endUri == null) return;
        map.put(endUri, bag);
        String local = CGMESPropertyBag.extractLocal(endUri);
        if (local != null) {
            map.put(local, bag);
            map.putIfAbsent("#" + local, bag);
        }
    }

    private static String endUriOf(CGMESPropertyBag bag, boolean ratio) {
        if (ratio) {
            String u = bag.getResourceId("RatioTapChanger.TransformerEnd");
            if (u == null) u = bag.getResourceId("TapChanger.TransformerEnd");
            return u;
        }
        String u = bag.getResourceId("PhaseTapChanger.TransformerEnd");
        if (u == null) u = bag.getResourceId("TapChanger.TransformerEnd");
        return u;
    }

    /**
     * InterPSS tap for a winding end from RatioTapChanger × optional PTC rho, else 1.0.
     * Neutral tap is 1.0 when no RTC; do not fold ratedU/BaseVoltage into the InterPSS
     * tap (that fights bus-base pu and MiniGrid P4).
     */
    protected double ratioTapForEnd(CGMESPropertyBag end) {
        double ratio = 1.0;
        if (end != null) {
            CGMESPropertyBag rtc = lookup(ratioTapByEnd, end);
            if (rtc != null) {
                double neutral = rtc.getDouble("TapChanger.neutralStep", Double.NaN);
                double step = resolveStep(rtc);
                if (!Double.isNaN(neutral) && !Double.isNaN(step)) {
                    double inc = rtc.getDouble("RatioTapChanger.stepVoltageIncrement", 0.0);
                    ratio = linearRatioTap(step, neutral, inc);
                }
            }
            PhaseTapResult ptc = phaseTapForEnd(end);
            ratio *= ptc.rho;
        }
        if (ratio <= 0.0 || ratio > 2.0) {
            log.warn("Combined tap {} outside (0,2] for end {} — using 1.0",
                    ratio, end != null ? end.getLocalId() : null);
            return 1.0;
        }
        return ratio;
    }

    /** Phase shift (degrees) for a winding end; 0 if none. */
    protected double phaseShiftDegForEnd(CGMESPropertyBag end) {
        return phaseTapForEnd(end).angleDeg;
    }

    protected PhaseTapResult phaseTapForEnd(CGMESPropertyBag end) {
        if (end == null) return PhaseTapResult.NEUTRAL;
        CGMESPropertyBag ptc = lookup(phaseTapByEnd, end);
        if (ptc == null) return PhaseTapResult.NEUTRAL;

        double neutral = ptc.getDouble("TapChanger.neutralStep", Double.NaN);
        double step = resolveStep(ptc);
        if (Double.isNaN(step)) step = neutral;
        if (Double.isNaN(neutral) || Double.isNaN(step)) return PhaseTapResult.NEUTRAL;

        String leaf = rdfTypeLocalName(ptc);
        if (leaf != null && leaf.contains("Tabular")) {
            return tabularPhaseTap(ptc, step);
        }
        // Linear: stepPhaseShiftIncrement in degrees/step
        double stepPhaseInc = ptc.getDouble("PhaseTapChangerLinear.stepPhaseShiftIncrement", Double.NaN);
        if (!Double.isNaN(stepPhaseInc) || (leaf != null && leaf.contains("Linear"))) {
            if (Double.isNaN(stepPhaseInc)) stepPhaseInc = 0.0;
            return PhaseTapResult.of(linearPhaseAngleDeg(step, neutral, stepPhaseInc), 1.0);
        }
        // Asymmetrical / Symmetrical share voltageStepIncrement (%); asym has windingConnectionAngle
        double vInc = ptc.getDouble("PhaseTapChangerNonLinear.voltageStepIncrement", Double.NaN);
        if (Double.isNaN(vInc)) {
            vInc = ptc.getDouble("PhaseTapChangerAsymmetrical.voltageStepIncrement", Double.NaN);
        }
        if (Double.isNaN(vInc)) {
            vInc = ptc.getDouble("PhaseTapChangerSymmetrical.voltageStepIncrement", Double.NaN);
        }
        double wca = ptc.getDouble("PhaseTapChangerAsymmetrical.windingConnectionAngle", Double.NaN);
        if (!Double.isNaN(wca) || (leaf != null && leaf.contains("Asymmetrical"))) {
            if (Double.isNaN(vInc)) vInc = 0.0;
            if (Double.isNaN(wca)) wca = 90.0; // IEC default often 90° when omitted
            return asymmetricalPhaseTap(step, neutral, vInc, wca);
        }
        if (!Double.isNaN(vInc) || (leaf != null && leaf.contains("Symmetrical"))) {
            if (Double.isNaN(vInc)) vInc = 0.0;
            return symmetricalPhaseTap(step, neutral, vInc);
        }
        log.debug("Unrecognized PhaseTapChanger {} type={} — angle 0", ptc.getLocalId(), leaf);
        return PhaseTapResult.NEUTRAL;
    }

    private PhaseTapResult tabularPhaseTap(CGMESPropertyBag ptc, double step) {
        String table = ptc.getResourceId("PhaseTapChangerTabular.table");
        if (table == null) table = ptc.getResourceId("PhaseTapChangerTabular.PhaseTapChangerTable");
        if (table == null) {
            log.warn("PhaseTapChangerTabular {} has no table — angle 0", ptc.getLocalId());
            return PhaseTapResult.NEUTRAL;
        }
        String key = CGMESPropertyBag.extractLocal(table) + "#" + (int) Math.round(step);
        CGMESPropertyBag pt = phaseTablePointByTableStep.get(key);
        if (pt == null) {
            log.warn("No PhaseTapChangerTablePoint for {} — angle 0", key);
            return PhaseTapResult.NEUTRAL;
        }
        double angle = pt.getDouble("PhaseTapChangerTablePoint.angle",
                pt.getDouble("TapChangerTablePoint.angle", 0.0));
        double ratio = pt.getDouble("PhaseTapChangerTablePoint.ratio",
                pt.getDouble("TapChangerTablePoint.ratio", 1.0));
        if (ratio == 0.0) ratio = 1.0;
        return PhaseTapResult.of(angle, ratio);
    }

    private static CGMESPropertyBag lookup(Map<String, CGMESPropertyBag> map, CGMESPropertyBag end) {
        CGMESPropertyBag bag = map.get(end.getId());
        if (bag == null) bag = map.get(end.getLocalId());
        return bag;
    }

    private static double resolveStep(CGMESPropertyBag tap) {
        double step = tap.getDouble("TapChanger.step", Double.NaN);
        if (Double.isNaN(step)) step = tap.getDouble("TapChanger.normalStep", Double.NaN);
        if (Double.isNaN(step)) step = tap.getDouble("TapChanger.neutralStep", Double.NaN);
        return step;
    }

    private static String rdfTypeLocalName(CGMESPropertyBag bag) {
        Resource r = bag.getResource();
        if (r == null) return null;
        StmtIterator it = r.listProperties(RDF.type);
        String best = null;
        while (it.hasNext()) {
            RDFNode n = it.next().getObject();
            if (!n.isResource()) continue;
            String uri = n.asResource().getURI();
            if (uri == null) continue;
            String local = uri.substring(uri.lastIndexOf('#') + 1);
            if (local.contains("PhaseTap") || local.contains("RatioTap")) {
                best = local;
            }
        }
        it.close();
        return best;
    }

    /** {@code 1 + (step - neutral) * incrementPercent / 100}. */
    public static double linearRatioTap(double step, double neutralStep, double stepVoltageIncrementPercent) {
        return 1.0 + (step - neutralStep) * stepVoltageIncrementPercent / 100.0;
    }

    /** Linear PTC: {@code (step - neutral) * stepPhaseShiftIncrement} degrees. */
    public static double linearPhaseAngleDeg(double step, double neutralStep, double stepPhaseShiftIncrementDeg) {
        return (step - neutralStep) * stepPhaseShiftIncrementDeg;
    }

    /**
     * Symmetrical PTC (IEC): ΔU = n·du, α = 2·atan(ΔU/2), ρ = 1.
     * {@code voltageStepIncrementPercent} is percent of rated voltage per step.
     */
    public static PhaseTapResult symmetricalPhaseTap(double step, double neutralStep,
                                                     double voltageStepIncrementPercent) {
        double n = step - neutralStep;
        double du = n * voltageStepIncrementPercent / 100.0;
        double angleRad = 2.0 * Math.atan(du / 2.0);
        return PhaseTapResult.of(Math.toDegrees(angleRad), 1.0);
    }

    /**
     * Asymmetrical PTC: complex in-phase / quadrature injection at
     * {@code windingConnectionAngle} degrees.
     * ρ = |1 + du∠θ|, α = arg(1 + du∠θ).
     */
    public static PhaseTapResult asymmetricalPhaseTap(double step, double neutralStep,
                                                      double voltageStepIncrementPercent,
                                                      double windingConnectionAngleDeg) {
        double n = step - neutralStep;
        double du = n * voltageStepIncrementPercent / 100.0;
        double th = Math.toRadians(windingConnectionAngleDeg);
        double re = 1.0 + du * Math.cos(th);
        double im = du * Math.sin(th);
        double rho = Math.hypot(re, im);
        double angleDeg = Math.toDegrees(Math.atan2(im, re));
        if (rho <= 0.0) rho = 1.0;
        return PhaseTapResult.of(angleDeg, rho);
    }

    /** Result of evaluating a phase tap changer at the current step. */
    public static final class PhaseTapResult {
        public static final PhaseTapResult NEUTRAL = new PhaseTapResult(0.0, 1.0);
        public final double angleDeg;
        public final double rho;

        private PhaseTapResult(double angleDeg, double rho) {
            this.angleDeg = angleDeg;
            this.rho = rho;
        }

        public static PhaseTapResult of(double angleDeg, double rho) {
            return new PhaseTapResult(angleDeg, rho);
        }
    }

    /**
     * Map a CIM property bag into the network via the builder.
     */
    public abstract void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception;

    public String resolveBusId(String equipmentId) {
        if (cimModel == null) return null;
        java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(equipmentId);
        if (!topoNodes.isEmpty()) {
            return cimModel.getBusId(topoNodes.get(0));
        }
        return null;
    }

    /**
     * True when no terminal of this equipment maps to a created bus via
     * {@code Terminal.TopologicalNode}. Typical for EQ gear absent from TP
     * (CN-only / out-of-topology Cub_ terminals) and for multi-MAS EQ merged
     * without the matching TP — skip at debug, not WARN.
     */
    protected boolean isUnresolvedTopologyExpected(String equipmentId) {
        if (cimModel == null || equipmentId == null) return false;
        java.util.List<String> terms = cimModel.getTerminalsForEquipment(equipmentId);
        if (terms.isEmpty()) return true;
        for (String tid : terms) {
            String tn = cimModel.getTopologicalNodeByTerminal(tid);
            if (tn != null && cimModel.getBusId(tn) != null) {
                return false;
            }
        }
        return true;
    }

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

    /** Bus base voltage in kV, or null if bus missing / base ≤ 0. */
    protected static Double busBaseKV(AclfNetworkBuilder builder, String busId) {
        if (busId == null) return null;
        var bus = builder.getBus(busId);
        if (bus == null || bus.getBaseVoltage() <= 0) return null;
        return bus.getBaseVoltage() / 1000.0;
    }
}
