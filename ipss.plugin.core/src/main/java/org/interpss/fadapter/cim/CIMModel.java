/*
 * CIMModel.java
 *
 * Wraps a Jena Model and provides typed access to CIM elements.
 * Similar to PowSyBl's CgmesModel.
 */

package org.interpss.fadapter.cim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.interpss.fadapter.cim.util.CIMUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory CIM model backed by a Jena RDF Model.
 * Provides typed element access and topology index.
 */
public class CIMModel {
    private static final Logger log = LoggerFactory.getLogger(CIMModel.class);

    private final Model jenaModel;
    private String cimNamespace;
    private String entsoeNamespace;

    // Topology indices
    private Map<String, List<String>> terminalsByEquipment = new HashMap<>();
    private Map<String, String> topologicalNodeByTerminal = new HashMap<>();
    private Map<String, String> connectivityNodeByTerminal = new HashMap<>();
    private Map<String, String> equipmentByTerminal = new HashMap<>();
    private Map<String, String> baseVoltageById = new HashMap<>();
    private Set<String> boundaryTopologicalNodes = new HashSet<>();
    private Map<String, Double> baseVoltageValueById = new HashMap<>();
    private Map<String, String> voltageLevelByTopoNode = new HashMap<>();
    private Map<String, String> voltageLevelByConnectivityNode = new HashMap<>();
    private Map<String, String> containerByVoltageLevel = new HashMap<>();
    private Map<String, String> busIdByTopoNode = new HashMap<>();

    // Indices built?
    private boolean indicesBuilt = false;

    public CIMModel(Model jenaModel) {
        this.jenaModel = jenaModel;
        detectNamespace();
    }

    /** Detect CIM namespace from the model */
    private void detectNamespace() {
        // Check for CIM16
        ResIterator it = jenaModel.listSubjectsWithProperty(RDF.type,
                jenaModel.createResource(CIMConstants.CIM16_NS + "Substation"));
        if (it.hasNext()) {
            this.cimNamespace = CIMConstants.CIM16_NS;
            this.entsoeNamespace = CIMConstants.ENTSOE_NS;
            log.info("Detected CIM16 namespace");
            it.close();
            return;
        }

        // Check for CIM100 - try multiple common types
        for (String type : new String[]{"Substation", "ACLineSegment", "ConnectivityNode", "EnergyConsumer"}) {
            it = jenaModel.listSubjectsWithProperty(RDF.type,
                    jenaModel.createResource(CIMConstants.CIM100_NS + type));
            if (it.hasNext()) {
                this.cimNamespace = CIMConstants.CIM100_NS;
                this.entsoeNamespace = CIMConstants.ENTSOE_NS;
                log.info("Detected CIM100 namespace via {}", type);
                it.close();
                return;
            }
        }

        // Default to CIM16 if nothing found
        this.cimNamespace = CIMConstants.CIM16_NS;
        this.entsoeNamespace = CIMConstants.ENTSOE_NS;
        log.warn("Could not detect CIM version, defaulting to CIM16");
    }

    public Model getJenaModel() {
        return jenaModel;
    }

    public String getCimNamespace() {
        return cimNamespace;
    }

    public String getEntsoeNamespace() {
        return entsoeNamespace;
    }

    // --- Element accessors ---

    /** Return all resources of a given CIM type */
    private List<CIMPropertyBag> listByType(String typeUri) {
        List<CIMPropertyBag> result = new ArrayList<>();
        Resource typeRes = jenaModel.createResource(typeUri);
        ResIterator it = jenaModel.listSubjectsWithProperty(RDF.type, typeRes);
        while (it.hasNext()) {
            result.add(new CIMPropertyBag(it.next(), cimNamespace, entsoeNamespace));
        }
        it.close();
        return result;
    }

    public List<CIMPropertyBag> substations() {
        return listByType(cimNamespace + "Substation");
    }

    public List<CIMPropertyBag> voltageLevels() {
        return listByType(cimNamespace + "VoltageLevel");
    }

    public List<CIMPropertyBag> topologicalNodes() {
        return listByType(cimNamespace + "TopologicalNode");
    }

    public List<CIMPropertyBag> connectivityNodes() {
        return listByType(cimNamespace + "ConnectivityNode");
    }

    public List<CIMPropertyBag> acLineSegments() {
        return listByType(cimNamespace + "ACLineSegment");
    }

    /** SeriesCompensator — treated as line by PowSyBl */
    public List<CIMPropertyBag> seriesCompensators() {
        return listByType(cimNamespace + "SeriesCompensator");
    }

    public List<CIMPropertyBag> powerTransformers() {
        return listByType(cimNamespace + "PowerTransformer");
    }

    public List<CIMPropertyBag> transformerEnds() {
        return listByType(cimNamespace + "PowerTransformerEnd");
    }

    public List<CIMPropertyBag> transformerMeshImpedances() {
        return listByType(cimNamespace + "TransformerMeshImpedance");
    }

    public List<CIMPropertyBag> transformerCoreAdmittances() {
        return listByType(cimNamespace + "TransformerCoreAdmittance");
    }

    public List<CIMPropertyBag> energyConsumers() {
        return listByType(cimNamespace + "EnergyConsumer");
    }

    public List<CIMPropertyBag> synchronousMachines() {
        return listByType(cimNamespace + "SynchronousMachine");
    }

    public List<CIMPropertyBag> generatingUnits() {
        // Concrete GeneratingUnit subclasses are typed separately in RDF
        List<CIMPropertyBag> result = new ArrayList<>();
        result.addAll(listByType(cimNamespace + "GeneratingUnit"));
        result.addAll(listByType(cimNamespace + "ThermalGeneratingUnit"));
        result.addAll(listByType(cimNamespace + "HydroGeneratingUnit"));
        result.addAll(listByType(cimNamespace + "NuclearGeneratingUnit"));
        result.addAll(listByType(cimNamespace + "WindGeneratingUnit"));
        result.addAll(listByType(cimNamespace + "SolarGeneratingUnit"));
        return result;
    }

    public List<CIMPropertyBag> shuntCompensators() {
        // Includes both Linear and nonlinear
        List<CIMPropertyBag> result = new ArrayList<>();
        result.addAll(listByType(cimNamespace + "LinearShuntCompensator"));
        result.addAll(listByType(cimNamespace + "NonlinearShuntCompensator"));
        return result;
    }

    public List<CIMPropertyBag> asynchronousMachines() {
        return listByType(cimNamespace + "AsynchronousMachine");
    }

    public List<CIMPropertyBag> externalNetworkInjections() {
        return listByType(cimNamespace + "ExternalNetworkInjection");
    }

    public List<CIMPropertyBag> terminals() {
        return listByType(cimNamespace + "Terminal");
    }

    public List<CIMPropertyBag> baseVoltages() {
        return listByType(cimNamespace + "BaseVoltage");
    }

    public List<CIMPropertyBag> switches() {
        List<CIMPropertyBag> result = new ArrayList<>();
        result.addAll(listByType(cimNamespace + "Switch"));
        result.addAll(listByType(cimNamespace + "Breaker"));
        result.addAll(listByType(cimNamespace + "Disconnector"));
        return result;
    }

    public List<CIMPropertyBag> busbarSections() {
        return listByType(cimNamespace + "BusbarSection");
    }

    public List<CIMPropertyBag> currentLimits() {
        return listByType(cimNamespace + "CurrentLimit");
    }

    public List<CIMPropertyBag> operationalLimitSets() {
        return listByType(cimNamespace + "OperationalLimitSet");
    }

    public List<CIMPropertyBag> operationalLimitTypes() {
        return listByType(cimNamespace + "OperationalLimitType");
    }

    /** Get a specific resource by URI */
    public CIMPropertyBag getResource(String uri) {
        Resource r = jenaModel.getResource(uri);
        if (r != null) {
            return new CIMPropertyBag(r, cimNamespace, entsoeNamespace);
        }
        return null;
    }

    // --- Index building ---

    /**
     * Build lookup indices for terminals, topology, base voltages.
     * Must be called before using topology resolution methods.
     */
    public void buildIndices() {
        if (indicesBuilt) return;

        log.info("Building CIM model indices...");

        // Build base voltage index (normalize to kV)
        // CIM spec says BaseVoltage.nominalVoltage is in V, but some datasets (ENTSO-E) use kV.
        // Heuristic: if value > 1000, assume V and convert to kV.
        for (CIMPropertyBag bv : baseVoltages()) {
            String id = bv.getId();
            double nomV = bv.getDouble("BaseVoltage.nominalVoltage");
            if (nomV > 1000) nomV = nomV / 1000.0; // V → kV
            baseVoltageById.put(id, bv.getLocalId());
            baseVoltageValueById.put(bv.getLocalId(), nomV);
            baseVoltageValueById.put(id, nomV);
        }
        log.debug("Indexed {} base voltages", baseVoltageValueById.size());

        // Build terminal index: Terminal → ConductingEquipment, TopologicalNode, ConnectivityNode
        for (CIMPropertyBag term : terminals()) {
            String termId = term.getId();
            String equipId = term.getResourceId("Terminal.ConductingEquipment");
            String topoNodeId = term.getResourceId("Terminal.TopologicalNode");
            String connNodeId = term.getResourceId("Terminal.ConnectivityNode");

            if (equipId != null) {
                equipmentByTerminal.put(termId, equipId);
                terminalsByEquipment.computeIfAbsent(equipId, k -> new ArrayList<>()).add(termId);
            }
            if (topoNodeId != null) {
                topologicalNodeByTerminal.put(termId, topoNodeId);
            }
            if (connNodeId != null) {
                connectivityNodeByTerminal.put(termId, connNodeId);
            }
        }
        log.debug("Indexed {} terminals", topologicalNodeByTerminal.size());

        // Detect boundary TopologicalNodes (boundaryPoint=true on TopologicalNode)
        String entsoeNs = "http://entsoe.eu/CIM/SchemaExtension/3/1#";
        for (CIMPropertyBag tn : topologicalNodes()) {
            String bp = tn.getString("TopologicalNode.boundaryPoint");
            if (bp != null) {
                // Try entsoe namespace prefix
                bp = tn.getString(entsoeNs.equals("http://entsoe.eu/CIM/SchemaExtension/3/1#") ? "entsoe:TopologicalNode.boundaryPoint" : "TopologicalNode.boundaryPoint");
            }
            // Also check via SPARQL for the entsoe namespaced property
            if (bp == null) {
                // Try Jena property lookup directly
                org.apache.jena.rdf.model.Resource res = tn.getResource();
                org.apache.jena.rdf.model.Property boundaryProp =
                    jenaModel.createProperty("http://entsoe.eu/CIM/SchemaExtension/3/1#", "TopologicalNode.boundaryPoint");
                if (res.hasProperty(boundaryProp)) {
                    bp = res.getProperty(boundaryProp).getString();
                }
            }
            if ("true".equals(bp)) {
                boundaryTopologicalNodes.add(tn.getId());
                log.debug("Boundary TN: {}", tn.getName());
            }
        }
        // Also detect boundary from ConnectivityNode (boundaryPoint=true on CN)
        // CNs in boundary files map to boundary TNs via ConnectivityNode.TopologicalNode
        for (CIMPropertyBag cn : connectivityNodes()) {
            String bp = null;
            org.apache.jena.rdf.model.Resource res = cn.getResource();
            org.apache.jena.rdf.model.Property boundaryProp =
                jenaModel.createProperty("http://entsoe.eu/CIM/SchemaExtension/3/1#", "ConnectivityNode.boundaryPoint");
            if (res.hasProperty(boundaryProp)) {
                bp = res.getProperty(boundaryProp).getString();
            }
            if ("true".equals(bp)) {
                // Find the TN for this CN and mark it as boundary
                String tnId = topologicalNodeByTerminal.get(cn.getId());
                // CN → TN via Terminal? Actually CN has ConnectivityNode.TopologicalNode directly
                // Let's use SPARQL
                if (tnId == null) {
                    String cnUri = cn.getResource().getURI();
                    java.util.List<org.apache.jena.query.QuerySolution> results = sparqlSelect(
                        "PREFIX cim: <" + cimNamespace + "> " +
                        "SELECT ?tn WHERE { <" + cnUri + "> cim:ConnectivityNode.TopologicalNode ?tn } LIMIT 1");
                    if (!results.isEmpty()) {
                        tnId = results.get(0).getResource("tn").getURI();
                    }
                }
                if (tnId != null) {
                    boundaryTopologicalNodes.add(tnId);
                    log.debug("Boundary TN from CN: {}", tnId);
                }
            }
        }
        log.debug("Detected {} boundary TopologicalNodes", boundaryTopologicalNodes.size());

        // Build voltage level → substation mapping
        for (CIMPropertyBag vl : voltageLevels()) {
            String vlId = vl.getId();
            String substationId = vl.getResourceId("VoltageLevel.MemberOf_Substation");
            if (substationId != null) {
                containerByVoltageLevel.put(vlId, substationId);
            }
        }

        // Build topological node → voltage level mapping
        for (CIMPropertyBag tn : topologicalNodes()) {
            String tnId = tn.getId();
            String vlId = tn.getResourceId("TopologicalNode.ConnectivityNodeContainer");
            if (vlId != null) {
                voltageLevelByTopoNode.put(tnId, vlId);
            }
        }

        // Build connectivity node → voltage level mapping
        for (CIMPropertyBag cn : connectivityNodes()) {
            String cnId = cn.getId();
            String vlId = cn.getResourceId("ConnectivityNode.ConnectivityNodeContainer");
            if (vlId != null) {
                voltageLevelByConnectivityNode.put(cnId, vlId);
            }
        }

        // If no TopologicalNodes and no ConnectivityNodes, try BusbarSection-based topology
        if (topologicalNodeByTerminal.isEmpty() && connectivityNodeByTerminal.isEmpty()) {
            log.info("No TopologicalNode or ConnectivityNode references found in terminals, building BusbarSection-based topology");
            buildBusbarBusIndex();
        }

        indicesBuilt = true;
        log.info("CIM model indices built successfully");
    }

    /**
     * Build terminal index for BusbarSection-based topology (EQ-only mode).
     * In node-breaker EQ files, a terminal connected to a BusbarSection
     * implies the equipment is on the same bus as that busbar.
     */
    private void buildBusbarBusIndex() {
        // Find terminals connected to BusbarSections and map equipment → busbar
        for (CIMPropertyBag term : terminals()) {
            String termId = term.getId();
            String equipId = term.getResourceId("Terminal.ConductingEquipment");
            if (equipId == null) continue;

            // Check if this terminal's equipment is a BusbarSection
            // Terminals of busbar sections point to the busbar itself
            Resource equipRes = jenaModel.getResource(equipId);
            Resource busbarType = jenaModel.createResource(cimNamespace + "BusbarSection");
            if (equipRes.hasProperty(org.apache.jena.vocabulary.RDF.type, busbarType)) {
                // This terminal belongs to a busbar section — store busbar as the "node" for this terminal
                // Other terminals connected to the same connectivity node will be on this bus
                connectivityNodeByTerminal.put(termId, equipId);
            }
        }
    }

    // --- Topology helpers ---

    /** Get the topological node URI connected to a terminal */
    public String getTopologicalNodeByTerminal(String terminalId) {
        return topologicalNodeByTerminal.get(terminalId);
    }

    /** Get the connectivity node URI connected to a terminal */
    public String getConnectivityNodeByTerminal(String terminalId) {
        return connectivityNodeByTerminal.get(terminalId);
    }

    /** Get the conducting equipment URI for a terminal */
    public String getEquipmentByTerminal(String terminalId) {
        return equipmentByTerminal.get(terminalId);
    }

    /** Get all terminal URIs for a conducting equipment */
    public List<String> getTerminalsForEquipment(String equipmentId) {
        return terminalsByEquipment.getOrDefault(equipmentId, new ArrayList<>());
    }

    /**
     * Get the topological node URIs connected to a conducting equipment via its terminals.
     * Falls back to connectivity nodes if no topological nodes found.
     */
    public List<String> getTopologicalNodesForEquipment(String equipmentId) {
        List<String> terminalIds = getTerminalsForEquipment(equipmentId);
        List<String> resultNodes = new ArrayList<>();
        for (String tid : terminalIds) {
            String tnId = topologicalNodeByTerminal.get(tid);
            if (tnId != null) {
                resultNodes.add(tnId);
            } else {
                // fallback to connectivity node
                String cnId = connectivityNodeByTerminal.get(tid);
                if (cnId != null) {
                    resultNodes.add(cnId);
                }
            }
        }
        return resultNodes;
    }

    /** Get base voltage value for a base voltage resource URI */
    public Double getBaseVoltageValue(String baseVoltageUri) {
        if (baseVoltageUri == null) return null;
        Double val = baseVoltageValueById.get(baseVoltageUri);
        if (val == null) {
            val = baseVoltageValueById.get(CIMPropertyBag.extractLocal(baseVoltageUri));
        }
        return val;
    }

    /** Check if a TopologicalNode is a boundary node (should not be created as a bus) */
    public boolean isBoundaryTopologicalNode(String tnId) {
        return boundaryTopologicalNodes.contains(tnId);
    }

    /** Get voltage level URI for a topological node */
    public String getVoltageLevelByTopoNode(String topoNodeUri) {
        return voltageLevelByTopoNode.get(topoNodeUri);
    }

    /** Map topological node to ODM bus ID */
    public void mapBusId(String topoNodeUri, String busId) {
        busIdByTopoNode.put(topoNodeUri, busId);
    }

    /** Get ODM bus ID for a topological node */
    public String getBusId(String topoNodeUri) {
        return busIdByTopoNode.get(topoNodeUri);
    }

    /**
     * Get the nominal voltage for a topological node or connectivity node.
     */
    public Double getNominalVoltageForTopoNode(String topoNodeUri) {
        // Try topological node → voltage level
        String vlUri = voltageLevelByTopoNode.get(topoNodeUri);
        if (vlUri == null) {
            // Try connectivity node → voltage level
            vlUri = voltageLevelByConnectivityNode.get(topoNodeUri);
        }
        if (vlUri != null) {
            Double v = getVLRatedVoltage(vlUri);
            if (v != null) return v;
        }
        // No VoltageLevel (e.g. IEEE118 hub CIM): resolve from connected equipment
        return getBaseVoltageFromConnectivityNode(topoNodeUri);
    }

    /**
     * Resolve nominal voltage (kV) for a ConnectivityNode from connected equipment
     * {@code ConductingEquipment.BaseVoltage} via terminals, or from
     * {@code TransformerEnd.BaseVoltage} / {@code PowerTransformerEnd.ratedU}
     * when the only connected equipment is a SynchronousMachine without BaseVoltage
     * (IEEE118 hub style).
     */
    public Double getBaseVoltageFromConnectivityNode(String cnUri) {
        if (cnUri == null) return null;
        for (Map.Entry<String, String> e : connectivityNodeByTerminal.entrySet()) {
            if (!cnUri.equals(e.getValue())) continue;
            String termId = e.getKey();
            String equipId = equipmentByTerminal.get(termId);
            if (equipId != null) {
                Resource eqRes = jenaModel.getResource(equipId);
                Property bvProp = jenaModel.createProperty(cimNamespace + "ConductingEquipment.BaseVoltage");
                Statement st = eqRes.getProperty(bvProp);
                if (st != null && st.getObject().isResource()) {
                    Double val = getBaseVoltageValue(st.getObject().asResource().getURI());
                    if (val != null) return val;
                }
            }
            Double fromEnd = getBaseVoltageFromTransformerTerminal(termId);
            if (fromEnd != null) return fromEnd;
        }
        return null;
    }

    /**
     * Resolve kV from the PowerTransformerEnd that owns {@code terminalUri}.
     */
    private Double getBaseVoltageFromTransformerTerminal(String terminalUri) {
        if (terminalUri == null) return null;
        for (CIMPropertyBag end : transformerEnds()) {
            String term = end.getResourceId("TransformerEnd.Terminal");
            if (!terminalUri.equals(term)) continue;
            String bvUri = end.getResourceId("TransformerEnd.BaseVoltage");
            if (bvUri != null) {
                Double val = getBaseVoltageValue(bvUri);
                if (val != null) return val;
            }
            double ratedU = end.getDouble("PowerTransformerEnd.ratedU",
                    end.getDouble("TransformerEnd.ratedU", 0.0));
            if (ratedU > 0) {
                return CIMUnitConverter.toKV(ratedU);
            }
        }
        return null;
    }

    /**
     * Get nominal voltage from VoltageLevel resource.
     * Tries: nominalVoltage property → BaseVoltage reference → VL name (fallback).
     */
    public Double getVLRatedVoltage(String vlUri) {
        // Try nominalVoltage property directly
        Resource vlRes = jenaModel.getResource(vlUri);
        Property nomVProp = jenaModel.createProperty(cimNamespace + "VoltageLevel.nominalVoltage");
        Statement stmt = vlRes.getProperty(nomVProp);
        if (stmt != null && stmt.getObject().isLiteral()) {
            double v = stmt.getObject().asLiteral().getDouble();
            if (v > 1000) v = v / 1000.0; // normalize V → kV
            return v;
        }

        // Try BaseVoltage reference
        Property bvProp = jenaModel.createProperty(cimNamespace + "VoltageLevel.BaseVoltage");
        stmt = vlRes.getProperty(bvProp);
        if (stmt != null && stmt.getObject().isResource()) {
            String bvUri = stmt.getObject().asResource().getURI();
            Double val = getBaseVoltageValue(bvUri);
            if (val != null) return val;
        }

        // Fallback: VoltageLevel name is often the nominal voltage (e.g. "380.0", "220.0")
        Property nameProp = jenaModel.createProperty(cimNamespace + "IdentifiedObject.name");
        stmt = vlRes.getProperty(nameProp);
        if (stmt != null && stmt.getObject().isLiteral()) {
            try {
                return Double.parseDouble(stmt.getObject().asLiteral().getString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Run a SPARQL SELECT query and return results as list of QuerySolution.
     */
    public List<QuerySolution> sparqlSelect(String sparql) {
        List<QuerySolution> results = new ArrayList<>();
        Query query = QueryFactory.create(sparql);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, jenaModel)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                results.add(rs.next());
            }
        }
        return results;
    }

    /** Detect CIM version from namespace */
    public String detectVersion() {
        if (cimNamespace == null) return "unknown";
        if (cimNamespace.contains("TC57/2013")) return "CIM16";
        if (cimNamespace.contains("TC57/CIM100")) return "CIM100";
        if (cimNamespace.contains("TC57/2007")) return "CIM14";
        return "unknown(" + cimNamespace + ")";
    }

    /** Get model size (number of triples) */
    public long size() {
        return jenaModel.size();
    }
}
