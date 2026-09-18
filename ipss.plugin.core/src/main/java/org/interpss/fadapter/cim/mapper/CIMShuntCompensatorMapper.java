/*
 * CIMShuntCompensatorMapper.java
 *
 * Maps CIM LinearShuntCompensator → bus shunt Y.
 */

package org.interpss.fadapter.cim.mapper;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.CIMPropertyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps CIM ShuntCompensator to bus shunt admittance.
 */
public class CIMShuntCompensatorMapper extends AbstractCIMDataMapper {
    private static final Logger log = LoggerFactory.getLogger(CIMShuntCompensatorMapper.class);

    private final double baseMVA;

    public CIMShuntCompensatorMapper(double baseMVA) {
        this.baseMVA = baseMVA;
    }

    @Override
    public void map(CIMPropertyBag bag, AclfNetworkBuilder builder) throws Exception {
        String shuntId = bag.getLocalId();
        String name = bag.getName();
        if (name == null) name = shuntId;

        double totalB;
        double totalG;

        double bPerSection = bag.getDouble("LinearShuntCompensator.bPerSection", 0.0);
        double gPerSection = bag.getDouble("LinearShuntCompensator.gPerSection", 0.0);

        if (bPerSection != 0.0 || gPerSection != 0.0) {
            int sections = bag.getInt("ShuntCompensator.normalSections",
                         bag.getInt("ShuntCompensator.maximumSections", 1));
            totalB = bPerSection * sections;
            totalG = gPerSection * sections;
        } else {
            totalB = 0.0;
            totalG = 0.0;
            int normalSections = bag.getInt("ShuntCompensator.normalSections",
                            bag.getInt("ShuntCompensator.maximumSections", 1));
            if (cimModel != null) {
                java.util.List<org.apache.jena.query.QuerySolution> points = cimModel.sparqlSelect(
                    "PREFIX cim: <" + cimModel.getCimNamespace() + "> " +
                    "SELECT ?b ?g ?section WHERE { " +
                    "  ?point cim:NonlinearShuntCompensatorPoint.NonlinearShuntCompensator <" + bag.getResource().getURI() + "> . " +
                    "  ?point cim:NonlinearShuntCompensatorPoint.sectionNumber ?section . " +
                    "  ?point cim:NonlinearShuntCompensatorPoint.b ?b . " +
                    "  ?point cim:NonlinearShuntCompensatorPoint.g ?g . " +
                    "  FILTER(?section = " + normalSections + ") " +
                    "}");
                if (!points.isEmpty()) {
                    totalB = points.get(0).getLiteral("b").getDouble();
                    totalG = points.get(0).getLiteral("g").getDouble();
                } else {
                    java.util.List<org.apache.jena.query.QuerySolution> allPoints = cimModel.sparqlSelect(
                        "PREFIX cim: <" + cimModel.getCimNamespace() + "> " +
                        "SELECT ?b ?g ?section WHERE { " +
                        "  ?point cim:NonlinearShuntCompensatorPoint.NonlinearShuntCompensator <" + bag.getResource().getURI() + "> . " +
                        "  ?point cim:NonlinearShuntCompensatorPoint.sectionNumber ?section . " +
                        "  ?point cim:NonlinearShuntCompensatorPoint.b ?b . " +
                        "  ?point cim:NonlinearShuntCompensatorPoint.g ?g . " +
                        "  FILTER(?section <= " + normalSections + ") " +
                        "}");
                    for (var pt : allPoints) {
                        totalB += pt.getLiteral("b").getDouble();
                        totalG += pt.getLiteral("g").getDouble();
                    }
                    if (totalB == 0.0 && totalG == 0.0) {
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
            log.warn("Skipping shunt {} - cannot resolve bus", name);
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

        builder.addToBusShuntY(busId, new Complex(gPU, bPU));

        log.debug(String.format("Created shunt: %s on bus %s, B=%.6f S (%.4f PU)",
            name, busId, totalB, bPU));
    }
}
