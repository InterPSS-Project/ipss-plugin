/*
 * AbstractCGMESDataMapper.java
 *
 * Base mapper for converting CIM elements via AclfNetworkBuilder.
 */

package org.interpss.fadapter.cim.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            String endUri = rtc.getResourceId("RatioTapChanger.TransformerEnd");
            if (endUri == null) {
                endUri = rtc.getResourceId("TapChanger.TransformerEnd");
            }
            if (endUri == null) continue;
            ratioTapByEnd.put(endUri, rtc);
            String local = CGMESPropertyBag.extractLocal(endUri);
            if (local != null) ratioTapByEnd.put(local, rtc);
            // also key by end bag id forms
            ratioTapByEnd.putIfAbsent("#" + local, rtc);
        }
        log.debug("Indexed {} RatioTapChangers covering {} end keys",
                tapChangers.size(), ratioTapByEnd.size());
    }

    /**
     * InterPSS tap for a winding end from RatioTapChanger, else 1.0.
     * <p>
     * Linear CGMES formula (stepVoltageIncrement in percent per step):
     * {@code 1 + (step - neutralStep) * stepVoltageIncrement / 100}.
     * When SSH omits step, falls back to normalStep then neutralStep.
     */
    protected double ratioTapForEnd(CGMESPropertyBag end) {
        if (end == null) return 1.0;
        CGMESPropertyBag rtc = ratioTapByEnd.get(end.getId());
        if (rtc == null) rtc = ratioTapByEnd.get(end.getLocalId());
        if (rtc == null) return 1.0;

        double neutral = rtc.getDouble("TapChanger.neutralStep", Double.NaN);
        double step = rtc.getDouble("TapChanger.step", Double.NaN);
        if (Double.isNaN(step)) {
            step = rtc.getDouble("TapChanger.normalStep", Double.NaN);
        }
        if (Double.isNaN(step)) {
            step = neutral;
        }
        if (Double.isNaN(neutral) || Double.isNaN(step)) {
            return 1.0;
        }

        double inc = rtc.getDouble("RatioTapChanger.stepVoltageIncrement", 0.0);
        double ratio = linearRatioTap(step, neutral, inc);
        if (ratio <= 0.0 || ratio > 2.0) {
            log.warn("RatioTapChanger {} tap {} outside (0,2] — using 1.0",
                    rtc.getLocalId(), ratio);
            return 1.0;
        }
        return ratio;
    }

    /**
     * {@code 1 + (step - neutral) * incrementPercent / 100}.
     * Exposed for unit tests.
     */
    public static double linearRatioTap(double step, double neutralStep, double stepVoltageIncrementPercent) {
        return 1.0 + (step - neutralStep) * stepVoltageIncrementPercent / 100.0;
    }

    /**
     * Map a CIM property bag into the network via the builder.
     */
    public abstract void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception;

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
