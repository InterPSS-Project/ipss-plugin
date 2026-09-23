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
import org.interpss.fadapter.cim.util.CGMESUnitConverter;
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

    /** Ratio tap table: tableLocalId + "#" + step → ratio / r / x. */
    private final Map<String, CGMESPropertyBag> ratioTablePointByTableStep = new HashMap<>();

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
     * A {@code RatioTapChangerTablePoint} at that step, when present, replaces the
     * linear increment. NL_TR2_3 step 5 is 1.025 in the table and 1.0275 linearly;
     * the linear value misses SvPowerFlow Q by ~30 Mvar on that winding.
     */
    public void indexRatioTapChangers(List<CGMESPropertyBag> tapChangers) {
        indexRatioTapChangers(tapChangers, null);
    }

    public void indexRatioTapChangers(List<CGMESPropertyBag> tapChangers,
                                      List<CGMESPropertyBag> tablePoints) {
        ratioTapByEnd.clear();
        ratioTablePointByTableStep.clear();
        if (tapChangers != null) {
            for (CGMESPropertyBag rtc : tapChangers) {
                putByEnd(ratioTapByEnd, rtc, endUriOf(rtc, true));
            }
        }
        if (tablePoints != null) {
            for (CGMESPropertyBag pt : tablePoints) {
                String table = pt.getResourceId("RatioTapChangerTablePoint.RatioTapChangerTable");
                if (table == null) {
                    table = pt.getResourceId("TapChangerTablePoint.TapChangerTable");
                }
                if (table == null) continue;
                String stepStr = pt.getString("TapChangerTablePoint.step");
                if (stepStr == null || stepStr.isBlank()) continue;
                int step = (int) Math.round(Double.parseDouble(stepStr.trim()));
                String local = CGMESPropertyBag.extractLocal(table);
                ratioTablePointByTableStep.put(local + "#" + step, pt);
            }
        }
        log.debug("Indexed {} RatioTapChangers / {} table points",
                tapChangers == null ? 0 : tapChangers.size(), ratioTablePointByTableStep.size());
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
                CGMESPropertyBag tablePoint = ratioTablePoint(rtc);
                if (tablePoint != null) {
                    ratio = tablePoint.getDouble("TapChangerTablePoint.ratio", 1.0);
                    if (ratio == 0.0) ratio = 1.0;
                } else {
                    double neutral = rtc.getDouble("TapChanger.neutralStep", Double.NaN);
                    double step = resolveStep(rtc);
                    if (!Double.isNaN(neutral) && !Double.isNaN(step)) {
                        double inc = rtc.getDouble("RatioTapChanger.stepVoltageIncrement", 0.0);
                        ratio = linearRatioTap(step, neutral, inc);
                    }
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

    /**
     * InterPSS off-nominal tap for one winding. {@link #ratioTapForEnd} is left as the
     * RatioTapChanger step only. When {@code ratedU} differs from the topological-node
     * base (MiniGrid: 400 kV winding on a 380 kV node, 120 kV on 110 kV), scale by
     * {@code ratedU / busBase} so the solved kV ratio follows ratedU, not the bus bases.
     */
    protected double windingTurnRatio(CGMESPropertyBag end, Double busBaseKv) {
        return windingTurnRatio(end, busBaseKv, true);
    }

    /**
     * @param deadband ignored. A ratedU/base scale inside a few percent is still a
     *                 real off-nominal: BE-TR2_3 is 110.34375 kV on a 110 kV node,
     *                 and BE-TR2_2 is 220 kV on a 225 kV node. Dropping either one
     *                 moves SvPowerFlow Q by more than 1 Mvar. The scale multiplies
     *                 this winding's tap; it is not moved onto the other winding.
     */
    protected double windingTurnRatio(CGMESPropertyBag end, Double busBaseKv, boolean deadband) {
        double tap = ratioTapForEnd(end);
        if (end == null || busBaseKv == null || busBaseKv <= 0.0) return tap;
        double ratedU = CGMESUnitConverter.toKV(end.getDouble("PowerTransformerEnd.ratedU",
                end.getDouble("TransformerEnd.ratedU", 0.0)));
        if (ratedU <= 0.0) return tap;
        double scale = ratedU / busBaseKv;
        if (Math.abs(scale - 1.0) <= 1e-6) return tap;
        double scaled = tap * scale;
        if (!(scaled > 0.0) || scaled >= 2.0) {
            log.warn("ratedU/base tap {} outside (0,2) for end {} — using ratio tap {}",
                    scaled, end.getLocalId(), tap);
            return tap;
        }
        return scaled;
    }

    /** Phase-tap shift (degrees) for a winding end; 0 if none. */
    protected double phaseShiftDegForEnd(CGMESPropertyBag end) {
        return phaseTapForEnd(end).angleDeg;
    }

    /**
     * Winding angle passed to InterPSS. Phase-tap shift only.
     * {@code phaseAngleClock * 30} is not added: MiniGrid SV terminal angles differ by
     * well under 1° across a clock-5 (150°) winding, so inserting that shift moves the
     * solved state away from the published SV.
     */
    protected double windingAngleDeg(CGMESPropertyBag end) {
        return phaseShiftDegForEnd(end);
    }

    /** Vector-group clock as degrees. 0 when the property is absent. */
    public static double phaseAngleClockDeg(CGMESPropertyBag end) {
        if (end == null) return 0.0;
        double clock = end.getDouble("PowerTransformerEnd.phaseAngleClock", Double.NaN);
        if (Double.isNaN(clock)) {
            clock = end.getDouble("TransformerEnd.phaseAngleClock", Double.NaN);
        }
        if (Double.isNaN(clock)) return 0.0;
        return clock * 30.0;
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
        // TapChangerTablePoint.x is the reactance deviation in percent of nominal x.
        double xPercent = pt.getDouble("TapChangerTablePoint.x",
                pt.getDouble("PhaseTapChangerTablePoint.x", 0.0));
        double rPercent = pt.getDouble("TapChangerTablePoint.r",
                pt.getDouble("PhaseTapChangerTablePoint.r", 0.0));
        return PhaseTapResult.of(angle, ratio, xPercent, rPercent);
    }

    private static CGMESPropertyBag lookup(Map<String, CGMESPropertyBag> map, CGMESPropertyBag end) {
        CGMESPropertyBag bag = map.get(end.getId());
        if (bag == null) bag = map.get(end.getLocalId());
        return bag;
    }

    /**
     * Series ohm from a ratio-tap table when the table stores the step impedance
     * rather than a percent deviation. NL_TR2_3's table x at step 5 is 5.67 ohm;
     * the winding x is 5.38. A percent reading of that column leaves ~30 Mvar.
     * A column that is not within 50% of the winding ohm is a percent deviation.
     */
    protected double applyRatioTableOhm(CGMESPropertyBag end, String field, double windingOhm) {
        CGMESPropertyBag rtc = end == null ? null : lookup(ratioTapByEnd, end);
        CGMESPropertyBag pt = rtc == null ? null : ratioTablePoint(rtc);
        if (pt == null) return windingOhm;
        double table = pt.getDouble("TapChangerTablePoint." + field, Double.NaN);
        if (Double.isNaN(table)) return windingOhm;
        if (windingOhm != 0.0 && Math.abs(table - windingOhm) / Math.abs(windingOhm) < 0.5) {
            return table;
        }
        if (windingOhm != 0.0 && Math.abs(table) > 1e-9 && Math.abs(table) < 80.0) {
            return windingOhm * (1.0 + table / 100.0);
        }
        return windingOhm;
    }

    private CGMESPropertyBag ratioTablePoint(CGMESPropertyBag rtc) {
        String table = rtc.getResourceId("RatioTapChanger.RatioTapChangerTable");
        if (table == null) return null;
        double step = resolveStep(rtc);
        if (Double.isNaN(step)) return null;
        String local = CGMESPropertyBag.extractLocal(table);
        if (local == null) return null;
        return ratioTablePointByTableStep.get(local + "#" + (int) Math.round(step));
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

    /**
     * Series reactance (ohm) of a symmetrical or linear phase tap at {@code step}.
     * IEC 61970-301: a U-curve, {@code xMin} at neutral and {@code xMax} at the end step.
     * {@code endStep} is {@code highStep} when that span is non-zero, otherwise {@code lowStep}.
     */
    public static double phaseTapReactanceOhm(double step, double neutralStep, double endStep,
                                              double xMin, double xMax) {
        double span = endStep - neutralStep;
        if (!(Math.abs(span) > 1e-9) || !Double.isFinite(xMin) || !Double.isFinite(xMax)) {
            return xMin;
        }
        double a = (step - neutralStep) / span;
        return xMin + (xMax - xMin) * a * a;
    }

    /**
     * Replace a winding ohm with the phase-tap U-curve when that end owns a
     * phase tap and both {@code xMin} and {@code xMax} are present. A {@code xMin}
     * more than 50% away from the winding ohm is a different scale; keep the winding.
     * Linear and non-linear changers share the curve; the angle formula stays separate.
     */
    protected double phaseTapSeriesOhm(CGMESPropertyBag end, double windingOhm) {
        if (end == null) return windingOhm;
        CGMESPropertyBag ptc = lookup(phaseTapByEnd, end);
        if (ptc == null) return windingOhm;
        double xMin = firstPresent(ptc,
                "PhaseTapChangerNonLinear.xMin",
                "PhaseTapChangerLinear.xMin",
                "PhaseTapChangerSymmetrical.xMin",
                "PhaseTapChangerAsymmetrical.xMin");
        double xMax = firstPresent(ptc,
                "PhaseTapChangerNonLinear.xMax",
                "PhaseTapChangerLinear.xMax",
                "PhaseTapChangerSymmetrical.xMax",
                "PhaseTapChangerAsymmetrical.xMax");
        if (Double.isNaN(xMin) || Double.isNaN(xMax)) return windingOhm;
        if (windingOhm != 0.0 && Math.abs(xMin - windingOhm) / Math.abs(windingOhm) > 0.5) {
            log.debug("Phase tap xMin {} is not on the winding x {} scale — keeping winding ohm",
                    xMin, windingOhm);
            return windingOhm;
        }
        double neutral = ptc.getDouble("TapChanger.neutralStep", Double.NaN);
        double step = resolveStep(ptc);
        if (Double.isNaN(neutral) || Double.isNaN(step)) return windingOhm;
        double high = ptc.getDouble("TapChanger.highStep", Double.NaN);
        double low = ptc.getDouble("TapChanger.lowStep", Double.NaN);
        double endStep = high;
        if (Double.isNaN(endStep) || Math.abs(endStep - neutral) < 1e-9) {
            endStep = low;
        }
        if (Double.isNaN(endStep)) return xMin;
        return phaseTapReactanceOhm(step, neutral, endStep, xMin, xMax);
    }

    private static double firstPresent(CGMESPropertyBag bag, String... names) {
        for (String name : names) {
            if (bag.getString(name) != null) {
                return bag.getDouble(name, Double.NaN);
            }
        }
        return Double.NaN;
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
        public static final PhaseTapResult NEUTRAL = new PhaseTapResult(0.0, 1.0, 0.0, 0.0);
        public final double angleDeg;
        public final double rho;
        /** Reactance deviation, percent of the winding reactance. 0 if not tabular. */
        public final double xPercent;
        /** Resistance deviation, percent of the winding resistance. 0 if not tabular. */
        public final double rPercent;

        private PhaseTapResult(double angleDeg, double rho, double xPercent, double rPercent) {
            this.angleDeg = angleDeg;
            this.rho = rho;
            this.xPercent = xPercent;
            this.rPercent = rPercent;
        }

        public static PhaseTapResult of(double angleDeg, double rho) {
            return new PhaseTapResult(angleDeg, rho, 0.0, 0.0);
        }

        public static PhaseTapResult of(double angleDeg, double rho, double xPercent, double rPercent) {
            return new PhaseTapResult(angleDeg, rho, xPercent, rPercent);
        }
    }

    /** Scale a winding ohm value by a tabular tap deviation (percent). */
    protected static double applyPercentDeviation(double ohm, double percent) {
        if (percent == 0.0 || !Double.isFinite(percent)) return ohm;
        return ohm * (1.0 + percent / 100.0);
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

    /**
     * From/to buses in {@code ACDCTerminal.sequenceNumber} order, not RDF order.
     */
    protected String[] resolveBranchBusIds(String equipmentId) {
        if (cimModel == null) return new String[]{null, null};
        java.util.List<String> terminalIds = new java.util.ArrayList<>(
                cimModel.getTerminalsForEquipment(equipmentId));
        terminalIds.sort(java.util.Comparator.comparingInt(cimModel::terminalSequence));
        String bus1 = null, bus2 = null;
        for (String tid : terminalIds) {
            String busId = busIdForTerminal(tid);
            if (busId == null) continue;
            if (bus1 == null) {
                bus1 = busId;
            } else if (!busId.equals(bus1) && bus2 == null) {
                bus2 = busId;
            }
        }
        return new String[]{bus1, bus2};
    }

    /** Bus created for a transformer end, via {@code TransformerEnd.Terminal}. */
    protected String resolveBusIdFromEnd(CGMESPropertyBag end) {
        if (end == null) return null;
        return busIdForTerminal(end.getResourceId("TransformerEnd.Terminal"));
    }

    protected String busIdForTerminal(String termId) {
        if (cimModel == null || termId == null) return null;
        String node = cimModel.getTopologicalNodeByTerminal(termId);
        if (node == null) {
            String local = CGMESPropertyBag.extractLocal(termId);
            node = cimModel.getTopologicalNodeByTerminal(local);
        }
        if (node == null) {
            node = cimModel.getConnectivityNodeByTerminal(termId);
        }
        if (node == null) return null;
        // Only IDs for buses that were actually created. Falling back to the
        // CN/TN local id invents phantom buses (ReliCap Espheim T2: EQ CNs with
        // no TP Terminal.TopologicalNode → "Branch from && to bus not found").
        return cimModel.getBusId(node);
    }

    protected static boolean endHasX(CGMESPropertyBag end) {
        return end.getString("PowerTransformerEnd.x") != null
                || end.getString("TransformerEnd.x") != null;
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
