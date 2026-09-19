/*
 * CGMESShuntCompensatorMapper.java
 *
 * Maps CIM LinearShuntCompensator → bus-owned ShuntCompensator (B) + G-only shuntY.
 */

package org.interpss.fadapter.cim.mapper;

import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps CIM ShuntCompensator to a bus-owned InterPSS {@code ShuntCompensator}
 * (PSS/E fixed-shunt policy: B on compensator, G on bus.shuntY).
 */
public class CGMESShuntCompensatorMapper extends AbstractCGMESDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CGMESShuntCompensatorMapper.class);

    private final double baseMVA;

    public CGMESShuntCompensatorMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    @Override
    public void map(CGMESPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String shuntId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = shuntId;

        double totalB;
        double totalG;

        double bPerSection = bag.getDouble("LinearShuntCompensator.bPerSection", 0.0);
        double gPerSection = bag.getDouble("LinearShuntCompensator.gPerSection", 0.0);

        if (bPerSection != 0.0 || gPerSection != 0.0) {
            int sections = inServiceSections(bag);
            totalB = bPerSection * sections;
            totalG = gPerSection * sections;
        } else {
            totalB = 0.0;
            totalG = 0.0;
            int sections = inServiceSections(bag);
            if (cimModel != null) {
                java.util.List<org.apache.jena.query.QuerySolution> points = cimModel.sparqlSelect(
                    "PREFIX cim: <" + cimModel.getCimNamespace() + "> " +
                    "SELECT ?b ?g ?section WHERE { " +
                    "  ?point cim:NonlinearShuntCompensatorPoint.NonlinearShuntCompensator <" + bag.getResource().getURI() + "> . " +
                    "  ?point cim:NonlinearShuntCompensatorPoint.sectionNumber ?section . " +
                    "  ?point cim:NonlinearShuntCompensatorPoint.b ?b . " +
                    "  ?point cim:NonlinearShuntCompensatorPoint.g ?g . " +
                    "  FILTER(?section = " + sections + ") " +
                    "}");
                if (!points.isEmpty()) {
                    totalB = points.get(0).getLiteral("b").getDouble();
                    totalG = points.get(0).getLiteral("g").getDouble();
                } else {
                    // Points are cumulative totals, not increments. Take the single
                    // in-service step, never the sum of every point at or below it.
                    java.util.List<org.apache.jena.query.QuerySolution> atOrBelow = cimModel.sparqlSelect(
                        "PREFIX cim: <" + cimModel.getCimNamespace() + "> " +
                        "SELECT ?b ?g ?section WHERE { " +
                        "  ?point cim:NonlinearShuntCompensatorPoint.NonlinearShuntCompensator <" + bag.getResource().getURI() + "> . " +
                        "  ?point cim:NonlinearShuntCompensatorPoint.sectionNumber ?section . " +
                        "  ?point cim:NonlinearShuntCompensatorPoint.b ?b . " +
                        "  ?point cim:NonlinearShuntCompensatorPoint.g ?g . " +
                        "  FILTER(?section <= " + sections + ") " +
                        "} ORDER BY DESC(?section) LIMIT 1");
                    if (!atOrBelow.isEmpty()) {
                        totalB = atOrBelow.get(0).getLiteral("b").getDouble();
                        totalG = atOrBelow.get(0).getLiteral("g").getDouble();
                    } else {
                        java.util.List<org.apache.jena.query.QuerySolution> minPoint = cimModel.sparqlSelect(
                            "PREFIX cim: <" + cimModel.getCimNamespace() + "> " +
                            "SELECT ?b ?g WHERE { " +
                            "  ?point cim:NonlinearShuntCompensatorPoint.NonlinearShuntCompensator <" + bag.getResource().getURI() + "> . " +
                            "  ?point cim:NonlinearShuntCompensatorPoint.sectionNumber ?section . " +
                            "  ?point cim:NonlinearShuntCompensatorPoint.b ?b . " +
                            "  ?point cim:NonlinearShuntCompensatorPoint.g ?g . " +
                            "} ORDER BY ?section LIMIT 1");
                        if (!minPoint.isEmpty()) {
                            totalB = minPoint.get(0).getLiteral("b").getDouble();
                            totalG = minPoint.get(0).getLiteral("g").getDouble();
                        }
                    }
                }
            }
        }

        if (totalB == 0.0 && totalG == 0.0) {
            log.debug("Skipping zero shunt: {}", name);
            return;
        }

        String busId = resolveBusId(bag.getId());
        if (busId == null) {
            if (isUnresolvedTopologyExpected(bag.getId())) {
                log.debug("Skipping shunt {} - out of topology / no TP TopologicalNode", name);
            } else {
                log.warn("Skipping shunt {} - cannot resolve bus", name);
            }
            return;
        }

        if (builder.getBus(busId) == null) {
            log.warn("Skipping shunt {} - bus {} not found", name, busId);
            return;
        }

        Double baseKV = null;
        if (cimModel != null) {
            java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(bag.getId());
            if (!topoNodes.isEmpty()) {
                baseKV = cimModel.getNominalVoltageForTopoNode(topoNodes.get(0));
            }
        }
        if (baseKV == null) baseKV = 100.0;

        double baseY = baseMVA / (baseKV * baseKV);
        double bPU = totalB / baseY;
        double gPU = totalG / baseY;

        builder.addFixedShunt(busId, shuntId, true, gPU, bPU, name);

        log.debug(String.format("Created shunt: %s on bus %s, B=%.6f S (%.4f PU)",
            name, busId, totalB, bPU));
    }

    /**
     * SSH {@code ShuntCompensator.sections} is the in-service count. EQ
     * {@code normalSections} is only the fallback when SSH did not merge a step.
     */
    private static int inServiceSections(CGMESPropertyBag bag) {
        String ssh = bag.getString("ShuntCompensator.sections");
        if (ssh != null && !ssh.isBlank()) {
            try {
                return (int) Math.round(Double.parseDouble(ssh.trim()));
            } catch (NumberFormatException ignore) {
                // fall through to EQ normalSections
            }
        }
        return bag.getInt("ShuntCompensator.normalSections",
                bag.getInt("ShuntCompensator.maximumSections", 1));
    }
}
