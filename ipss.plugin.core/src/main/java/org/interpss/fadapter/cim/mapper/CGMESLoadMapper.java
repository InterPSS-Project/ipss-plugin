/*
 * CGMESLoadMapper.java
 *
 * Maps CIM EnergyConsumer → contribute load on bus.
 */

package org.interpss.fadapter.cim.mapper;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.interpss.fadapter.cim.util.CGMESUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps CIM EnergyConsumer (and AsynchronousMachine) to contribute load data.
 * CIM/CGMES ActivePower and ReactivePower are SI (W / var).
 */
public class CGMESLoadMapper extends AbstractCGMESDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CGMESLoadMapper.class);

    private final double baseMVA;
    private int mappedCount = 0;
    private Map<String, CGMESPropertyBag> loadResponseById;

    public CGMESLoadMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    public int getMappedCount() { return mappedCount; }

    @Override
    public void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String loadId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = loadId;

        double pW = bag.getDouble("EnergyConsumer.p",
                    bag.getDouble("EquivalentInjection.p",
                    bag.getDouble("RotatingMachine.p", 0.0)));
        double qVar = bag.getDouble("EnergyConsumer.q",
                    bag.getDouble("EquivalentInjection.q",
                    bag.getDouble("RotatingMachine.q", 0.0)));

        boolean equivalent = bag.getString("EquivalentInjection.p") != null
                || bag.getString("EquivalentInjection.q") != null;
        boolean isAsyncMachine = bag.getString("EnergyConsumer.p") == null && !equivalent;

        if (pW == 0.0 && qVar == 0.0 && !isAsyncMachine) {
            log.debug("Skipping zero load: {}", name);
            return;
        }

        String busId = resolveBusId(bag.getId());
        if ((busId == null || builder.getBus(busId) == null) && equivalent) {
            // Injection is on a skipped boundary node. Put it on the internal
            // end of the tie line so the IGM still balances.
            busId = boundaryTieBusId(bag.getId(), builder);
        }
        if (busId == null) {
            if (isUnresolvedTopologyExpected(bag.getId())) {
                log.debug("Skipping load {} - out of topology / no TP TopologicalNode", name);
            } else {
                log.warn("Skipping load {} - cannot resolve bus", name);
            }
            return;
        }

        if (builder.getBus(busId) == null) {
            log.warn("Skipping load {} - bus {} not found", name, busId);
            return;
        }

        double pMW = CGMESUnitConverter.siPowerToMVA(pW);
        double qMVAr = CGMESUnitConverter.siPowerToMVA(qVar);
        double pPU = CGMESUnitConverter.pToPU(pW, baseMVA);
        double qPU = CGMESUnitConverter.qToPU(qVar, baseMVA);

        double[] zip = zipFractions(bag);
        builder.addContributeLoad(busId, loadId, true,
                zipPart(pPU, qPU, zip[0], zip[3]),
                zipPart(pPU, qPU, zip[1], zip[4]),
                zipPart(pPU, qPU, zip[2], zip[5]),
                null, false);

        log.debug(String.format("Created load: %s on bus %s, P=%.2f MW, Q=%.2f MVAr", name, busId, pMW, qMVAr));
        mappedCount++;
    }

    /**
     * Constant-power / current / impedance fractions for P then Q.
     * Absent characteristic, or an exponent model, stays constant power.
     * NL-Load_3 is 80/20 P and 70/30 Q; at 1.019 pu that is the 1.9 MW the
     * constant-power reading misses on NL_TR2_3.
     */
    private double[] zipFractions(CGMESPropertyBag load) {
        double[] zip = { 1.0, 0.0, 0.0, 1.0, 0.0, 0.0 };
        CGMESPropertyBag resp = loadResponse(load);
        if (resp == null || resp.getBoolean("LoadResponseCharacteristic.exponentModel", false)) {
            return zip;
        }
        zip[0] = resp.getDouble("LoadResponseCharacteristic.pConstantPower", 1.0);
        zip[1] = resp.getDouble("LoadResponseCharacteristic.pConstantCurrent", 0.0);
        zip[2] = resp.getDouble("LoadResponseCharacteristic.pConstantImpedance", 0.0);
        zip[3] = resp.getDouble("LoadResponseCharacteristic.qConstantPower", 1.0);
        zip[4] = resp.getDouble("LoadResponseCharacteristic.qConstantCurrent", 0.0);
        zip[5] = resp.getDouble("LoadResponseCharacteristic.qConstantImpedance", 0.0);
        // CGMES portions are 0..1. CIMHub writes the same attributes as percent (100 = all constant power).
        if (portionSum(zip, 0) > 1.5 || portionSum(zip, 3) > 1.5) {
            for (int i = 0; i < zip.length; i++) zip[i] *= 0.01;
        }
        return zip;
    }

    private static double portionSum(double[] zip, int from) {
        return Math.abs(zip[from]) + Math.abs(zip[from + 1]) + Math.abs(zip[from + 2]);
    }

    private static Complex zipPart(double pPU, double qPU, double pFrac, double qFrac) {
        if (Math.abs(pFrac) < 1e-12 && Math.abs(qFrac) < 1e-12) return null;
        return new Complex(pPU * pFrac, qPU * qFrac);
    }

    private CGMESPropertyBag loadResponse(CGMESPropertyBag load) {
        if (load == null || cimModel == null) return null;
        String ref = load.getResourceId("EnergyConsumer.LoadResponse");
        if (ref == null) return null;
        if (loadResponseById == null) {
            loadResponseById = new HashMap<>();
            for (CGMESPropertyBag ch : cimModel.loadResponseCharacteristics()) {
                if (ch.getId() != null) loadResponseById.put(ch.getId(), ch);
                if (ch.getLocalId() != null) loadResponseById.put(ch.getLocalId(), ch);
            }
        }
        CGMESPropertyBag ch = loadResponseById.get(ref);
        if (ch == null) {
            String local = CGMESPropertyBag.extractLocal(ref);
            if (local != null) ch = loadResponseById.get(local);
        }
        return ch;
    }

    /**
     * Internal bus of the tie that reaches this boundary injection.
     * The injection's own topological node is skipped, so the schedule has to
     * sit on the in-service end of the AC line (or series compensator).
     */
    private String boundaryTieBusId(String equipmentId, AclfNetworkBuilder builder) {
        if (cimModel == null) return null;
        java.util.List<String> ownNodes = cimModel.getTopologicalNodesForEquipment(equipmentId);
        if (ownNodes.isEmpty()) return null;
        for (CGMESPropertyBag line : cimModel.acLineSegments()) {
            String partner = partnerBus(ownNodes, line.getId(), builder);
            if (partner != null) return partner;
        }
        for (CGMESPropertyBag sc : cimModel.seriesCompensators()) {
            String partner = partnerBus(ownNodes, sc.getId(), builder);
            if (partner != null) return partner;
        }
        return null;
    }

    private String partnerBus(java.util.List<String> ownNodes, String branchEquipId,
                              AclfNetworkBuilder builder) {
        java.util.List<String> nodes = cimModel.getTopologicalNodesForEquipment(branchEquipId);
        boolean touches = false;
        String other = null;
        for (String node : nodes) {
            if (touchesNode(ownNodes, node)) {
                touches = true;
            } else if (other == null) {
                other = node;
            }
        }
        if (!touches || other == null) return null;
        String busId = cimModel.getBusId(other);
        if (busId == null) {
            String local = CGMESPropertyBag.extractLocal(other);
            if (local != null && cimModel.getBusId(local) != null) {
                busId = cimModel.getBusId(local);
            } else if (local != null && builder.getBus(local) != null) {
                busId = local;
            }
        }
        return busId != null && builder.getBus(busId) != null ? busId : null;
    }

    private static boolean touchesNode(java.util.List<String> ownNodes, String node) {
        for (String own : ownNodes) {
            if (own == null || node == null) continue;
            if (own.equals(node)) return true;
            String a = CGMESPropertyBag.extractLocal(own);
            String b = CGMESPropertyBag.extractLocal(node);
            if (a != null && a.equals(b)) return true;
        }
        return false;
    }
}
