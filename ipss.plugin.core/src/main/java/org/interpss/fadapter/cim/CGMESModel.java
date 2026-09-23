/*
 * CGMESModel.java
 *
 * Wraps a Jena Model and provides typed access to CIM elements.
 * Similar to PowSyBl's CgmesModel.
 */

package org.interpss.fadapter.cim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.interpss.fadapter.cim.util.CGMESUnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory CIM model backed by a Jena RDF Model.
 * Provides typed element access and topology index.
 */
public class CGMESModel {
    private static final Logger log = LoggerFactory.getLogger(CGMESModel.class);

    private final Model jenaModel;
    private String cimNamespace;
    private String entsoeNamespace;

    // Topology indices
    private Map<String, List<String>> terminalsByEquipment = new HashMap<>();
    private Map<String, String> topologicalNodeByTerminal = new HashMap<>();
    private Map<String, String> connectivityNodeByTerminal = new HashMap<>();
    private Map<String, String> equipmentByTerminal = new HashMap<>();
    /** Terminal URI/local id → ACDCTerminal.sequenceNumber (1-based). */
    private Map<String, Integer> sequenceByTerminal = new HashMap<>();
    private Map<String, String> baseVoltageById = new HashMap<>();
    private Set<String> boundaryTopologicalNodes = new HashSet<>();
    private Map<String, Double> baseVoltageValueById = new HashMap<>();
    private Map<String, String> voltageLevelByTopoNode = new HashMap<>();
    private Map<String, String> voltageLevelByConnectivityNode = new HashMap<>();
    private Map<String, String> containerByVoltageLevel = new HashMap<>();
    private Map<String, String> busIdByTopoNode = new HashMap<>();

    // Indices built?
    private boolean indicesBuilt = false;

    public CGMESModel(Model jenaModel) {
        this.jenaModel = jenaModel;
        detectNamespace();
    }

    /** Detect CIM namespace from the model */
    private void detectNamespace() {
        // Check for CIM16
        ResIterator it = jenaModel.listSubjectsWithProperty(RDF.type,
                jenaModel.createResource(CGMESConstants.CIM16_NS + "Substation"));
        if (it.hasNext()) {
            this.cimNamespace = CGMESConstants.CIM16_NS;
            this.entsoeNamespace = CGMESConstants.ENTSOE_NS;
            log.info("Detected CIM16 namespace");
            it.close();
            return;
        }

        // Check for CIM100 - try multiple common types
        for (String type : new String[]{"Substation", "ACLineSegment", "ConnectivityNode", "EnergyConsumer"}) {
            it = jenaModel.listSubjectsWithProperty(RDF.type,
                    jenaModel.createResource(CGMESConstants.CIM100_NS + type));
            if (it.hasNext()) {
                this.cimNamespace = CGMESConstants.CIM100_NS;
                this.entsoeNamespace = CGMESConstants.ENTSOE_NS;
                log.info("Detected CIM100 namespace via {}", type);
                it.close();
                return;
            }
        }

        // Default to CIM16 if nothing found
        this.cimNamespace = CGMESConstants.CIM16_NS;
        this.entsoeNamespace = CGMESConstants.ENTSOE_NS;
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
    private List<CGMESPropertyBag> listByType(String typeUri) {
        List<CGMESPropertyBag> result = new ArrayList<>();
        Resource typeRes = jenaModel.createResource(typeUri);
        ResIterator it = jenaModel.listSubjectsWithProperty(RDF.type, typeRes);
        while (it.hasNext()) {
            result.add(new CGMESPropertyBag(it.next(), cimNamespace, entsoeNamespace));
        }
        it.close();
        return result;
    }

    public List<CGMESPropertyBag> substations() {
        return listByType(cimNamespace + "Substation");
    }

    public List<CGMESPropertyBag> voltageLevels() {
        return listByType(cimNamespace + "VoltageLevel");
    }

    public List<CGMESPropertyBag> topologicalNodes() {
        return listByType(cimNamespace + "TopologicalNode");
    }

    public List<CGMESPropertyBag> connectivityNodes() {
        return listByType(cimNamespace + "ConnectivityNode");
    }

    public List<CGMESPropertyBag> acLineSegments() {
        return listByType(cimNamespace + "ACLineSegment");
    }

    /** SeriesCompensator — treated as line by PowSyBl */
    public List<CGMESPropertyBag> seriesCompensators() {
        return listByType(cimNamespace + "SeriesCompensator");
    }

    public List<CGMESPropertyBag> powerTransformers() {
        return listByType(cimNamespace + "PowerTransformer");
    }

    public List<CGMESPropertyBag> transformerEnds() {
        return listByType(cimNamespace + "PowerTransformerEnd");
    }

    public List<CGMESPropertyBag> transformerMeshImpedances() {
        return listByType(cimNamespace + "TransformerMeshImpedance");
    }

    public List<CGMESPropertyBag> transformerCoreAdmittances() {
        return listByType(cimNamespace + "TransformerCoreAdmittance");
    }

    /** RatioTapChanger (EQ + SSH step merged on the same resource id). */
    public List<CGMESPropertyBag> ratioTapChangers() {
        return listByType(cimNamespace + "RatioTapChanger");
    }

    /**
     * Phase tap changers (leaf RDF types). Includes linear, symmetrical,
     * asymmetrical, and tabular specializations.
     */
    public List<CGMESPropertyBag> phaseTapChangers() {
        List<CGMESPropertyBag> result = new ArrayList<>();
        for (String type : new String[] {
                "PhaseTapChangerLinear",
                "PhaseTapChangerSymmetrical",
                "PhaseTapChangerAsymmetrical",
                "PhaseTapChangerTabular",
                "PhaseTapChangerNonLinear",
                "PhaseTapChanger" }) {
            result.addAll(listByType(cimNamespace + type));
        }
        return dedupeById(result);
    }

    /** PhaseTapChangerTablePoint rows (tabular PTC). */
    public List<CGMESPropertyBag> phaseTapChangerTablePoints() {
        return listByType(cimNamespace + "PhaseTapChangerTablePoint");
    }

    /**
     * RatioTapChangerTablePoint rows. When present they replace the linear
     * {@code stepVoltageIncrement} ratio, and may carry the step's series r/x.
     */
    public List<CGMESPropertyBag> ratioTapChangerTablePoints() {
        return listByType(cimNamespace + "RatioTapChangerTablePoint");
    }

    public List<CGMESPropertyBag> energyConsumers() {
        List<CGMESPropertyBag> result = new ArrayList<>();
        for (String type : new String[] {
                "EnergyConsumer", "ConformLoad", "NonConformLoad", "StationSupply" }) {
            result.addAll(listByType(cimNamespace + type));
        }
        if (entsoeNamespace != null) {
            result.addAll(listByType(entsoeNamespace + "StationSupply"));
        }
        return dedupeById(result);
    }

    /** ZIP fractions for an EnergyConsumer. Exponent model is not this list. */
    public List<CGMESPropertyBag> loadResponseCharacteristics() {
        return listByType(cimNamespace + "LoadResponseCharacteristic");
    }

    private static List<CGMESPropertyBag> dedupeById(List<CGMESPropertyBag> bags) {
        Map<String, CGMESPropertyBag> byId = new LinkedHashMap<>();
        int anon = 0;
        for (CGMESPropertyBag bag : bags) {
            String id = bag.getId();
            if (id == null) {
                id = "anon-" + (anon++);
            }
            byId.putIfAbsent(id, bag);
        }
        return new ArrayList<>(byId.values());
    }

    public List<CGMESPropertyBag> synchronousMachines() {
        return listByType(cimNamespace + "SynchronousMachine");
    }

    public List<CGMESPropertyBag> generatingUnits() {
        // Concrete GeneratingUnit subclasses are typed separately in RDF
        List<CGMESPropertyBag> result = new ArrayList<>();
        result.addAll(listByType(cimNamespace + "GeneratingUnit"));
        result.addAll(listByType(cimNamespace + "ThermalGeneratingUnit"));
        result.addAll(listByType(cimNamespace + "HydroGeneratingUnit"));
        result.addAll(listByType(cimNamespace + "NuclearGeneratingUnit"));
        result.addAll(listByType(cimNamespace + "WindGeneratingUnit"));
        result.addAll(listByType(cimNamespace + "SolarGeneratingUnit"));
        return result;
    }

    public List<CGMESPropertyBag> shuntCompensators() {
        // Includes both Linear and nonlinear
        List<CGMESPropertyBag> result = new ArrayList<>();
        result.addAll(listByType(cimNamespace + "LinearShuntCompensator"));
        result.addAll(listByType(cimNamespace + "NonlinearShuntCompensator"));
        return result;
    }

    public List<CGMESPropertyBag> staticVarCompensators() {
        return listByType(cimNamespace + "StaticVarCompensator");
    }

    public List<CGMESPropertyBag> asynchronousMachines() {
        return listByType(cimNamespace + "AsynchronousMachine");
    }

    public List<CGMESPropertyBag> externalNetworkInjections() {
        return listByType(cimNamespace + "ExternalNetworkInjection");
    }

    /** Boundary equivalent. SSH {@code p}/{@code q} use the load sign convention. */
    public List<CGMESPropertyBag> equivalentInjections() {
        return listByType(cimNamespace + "EquivalentInjection");
    }

    /** VSC HVDC converters. AC power is an injection; the DC line itself is not a branch. */
    public List<CGMESPropertyBag> vsConverters() {
        return listByType(cimNamespace + "VsConverter");
    }

    /** LCC HVDC converters. AC power is an injection; the DC line itself is not a branch. */
    public List<CGMESPropertyBag> csConverters() {
        return listByType(cimNamespace + "CsConverter");
    }

    public List<CGMESPropertyBag> dcLineSegments() {
        return listByType(cimNamespace + "DCLineSegment");
    }

    /**
     * DC line and converter DC terminals. A converter terminal is typed
     * {@code ACDCConverterDCTerminal}, not {@code DCTerminal}.
     */
    public List<CGMESPropertyBag> dcTerminals() {
        List<CGMESPropertyBag> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String type : new String[] {"DCTerminal", "ACDCConverterDCTerminal"}) {
            for (CGMESPropertyBag bag : listByType(cimNamespace + type)) {
                if (seen.add(bag.getId())) {
                    result.add(bag);
                }
            }
        }
        return result;
    }

    public List<CGMESPropertyBag> terminals() {
        return listByType(cimNamespace + "Terminal");
    }

    public List<CGMESPropertyBag> baseVoltages() {
        return listByType(cimNamespace + "BaseVoltage");
    }

    /**
     * Breakers, disconnectors and load-break switches. A resource typed as both
     * Switch and Breaker is returned once. Only a closed retained switch is a
     * branch; a non-retained switch is already inside one topological node.
     */
    public List<CGMESPropertyBag> switches() {
        List<CGMESPropertyBag> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String type : new String[] {"Breaker", "Disconnector", "LoadBreakSwitch", "Switch"}) {
            for (CGMESPropertyBag bag : listByType(cimNamespace + type)) {
                if (seen.add(bag.getId())) {
                    result.add(bag);
                }
            }
        }
        return result;
    }

    public List<CGMESPropertyBag> busbarSections() {
        return listByType(cimNamespace + "BusbarSection");
    }

    public List<CGMESPropertyBag> currentLimits() {
        return listByType(cimNamespace + "CurrentLimit");
    }

    public List<CGMESPropertyBag> operationalLimitSets() {
        return listByType(cimNamespace + "OperationalLimitSet");
    }

    public List<CGMESPropertyBag> operationalLimitTypes() {
        return listByType(cimNamespace + "OperationalLimitType");
    }

    /** Get a specific resource by URI */
    public CGMESPropertyBag getResource(String uri) {
        Resource r = jenaModel.getResource(uri);
        if (r != null) {
            return new CGMESPropertyBag(r, cimNamespace, entsoeNamespace);
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
        for (CGMESPropertyBag bv : baseVoltages()) {
            String id = bv.getId();
            double nomV = bv.getDouble("BaseVoltage.nominalVoltage");
            if (nomV > 1000) nomV = nomV / 1000.0; // V → kV
            baseVoltageById.put(id, bv.getLocalId());
            baseVoltageValueById.put(bv.getLocalId(), nomV);
            baseVoltageValueById.put(id, nomV);
        }
        log.debug("Indexed {} base voltages", baseVoltageValueById.size());

        // Build terminal index: Terminal → ConductingEquipment, TopologicalNode, ConnectivityNode
        for (CGMESPropertyBag term : terminals()) {
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
            int seq = term.getInt("ACDCTerminal.sequenceNumber",
                    term.getInt("Terminal.sequenceNumber", 0));
            if (seq > 0) {
                sequenceByTerminal.put(termId, seq);
                String local = CGMESPropertyBag.extractLocal(termId);
                if (local != null) {
                    sequenceByTerminal.put(local, seq);
                }
            }
        }
        log.debug("Indexed {} terminals", topologicalNodeByTerminal.size());

        // Detect boundary TopologicalNodes (boundaryPoint=true on TopologicalNode)
        String entsoeNs = "http://entsoe.eu/CIM/SchemaExtension/3/1#";
        for (CGMESPropertyBag tn : topologicalNodes()) {
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
        for (CGMESPropertyBag cn : connectivityNodes()) {
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
        for (CGMESPropertyBag vl : voltageLevels()) {
            String vlId = vl.getId();
            String substationId = vl.getResourceId("VoltageLevel.MemberOf_Substation");
            if (substationId != null) {
                containerByVoltageLevel.put(vlId, substationId);
            }
        }

        // Build topological node → voltage level mapping
        // Container may be a VoltageLevel or a Bay (then chase Bay.VoltageLevel)
        for (CGMESPropertyBag tn : topologicalNodes()) {
            String tnId = tn.getId();
            String containerId = tn.getResourceId("TopologicalNode.ConnectivityNodeContainer");
            String vlId = resolveVoltageLevelUri(containerId);
            if (vlId != null) {
                voltageLevelByTopoNode.put(tnId, vlId);
            }
        }

        // Build connectivity node → voltage level mapping
        for (CGMESPropertyBag cn : connectivityNodes()) {
            String cnId = cn.getId();
            String containerId = cn.getResourceId("ConnectivityNode.ConnectivityNodeContainer");
            String vlId = resolveVoltageLevelUri(containerId);
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
        for (CGMESPropertyBag term : terminals()) {
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

    /**
     * {@code ACDCTerminal.sequenceNumber}, or {@link Integer#MAX_VALUE} when absent
     * so unknown terminals sort after numbered ones.
     */
    public int terminalSequence(String terminalId) {
        if (terminalId == null) return Integer.MAX_VALUE;
        Integer seq = sequenceByTerminal.get(terminalId);
        if (seq == null) {
            seq = sequenceByTerminal.get(CGMESPropertyBag.extractLocal(terminalId));
        }
        return seq == null || seq <= 0 ? Integer.MAX_VALUE : seq;
    }

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
            val = baseVoltageValueById.get(CGMESPropertyBag.extractLocal(baseVoltageUri));
        }
        return val;
    }

    /** Check if a TopologicalNode is a boundary node (should not be created as a bus) */
    public boolean isBoundaryTopologicalNode(String tnId) {
        return boundaryTopologicalNodes.contains(tnId);
    }

    /**
     * True if this TN URI is referenced by a terminal but has no bus mapping
     * (dangling boundary ref when TP_BD is not loaded, or skipped boundary TN).
     */
    public boolean isUnmappedTopoNode(String tnUri) {
        return tnUri != null && getBusId(tnUri) == null;
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
     * Resolve nominal voltage (kV) for a ConnectivityNode or TopologicalNode from
     * connected equipment {@code ConductingEquipment.BaseVoltage} via terminals, or from
     * {@code TransformerEnd.BaseVoltage} / {@code PowerTransformerEnd.ratedU}
     * when the only connected equipment is a SynchronousMachine without BaseVoltage
     * (IEEE118 hub style).
     */
    public Double getBaseVoltageFromConnectivityNode(String nodeUri) {
        if (nodeUri == null) return null;
        for (Map.Entry<String, String> e : connectivityNodeByTerminal.entrySet()) {
            if (!nodeUri.equals(e.getValue())) continue;
            Double val = resolveVoltageFromTerminal(e.getKey());
            if (val != null) return val;
        }
        // TN-based models: match TopologicalNode via terminal → TN index
        for (Map.Entry<String, String> e : topologicalNodeByTerminal.entrySet()) {
            if (!nodeUri.equals(e.getValue())) continue;
            Double val = resolveVoltageFromTerminal(e.getKey());
            if (val != null) return val;
        }
        return null;
    }

    private Double resolveVoltageFromTerminal(String termId) {
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
        return getBaseVoltageFromTransformerTerminal(termId);
    }

    /**
     * Resolve kV from the PowerTransformerEnd that owns {@code terminalUri}.
     */
    private Double getBaseVoltageFromTransformerTerminal(String terminalUri) {
        if (terminalUri == null) return null;
        for (CGMESPropertyBag end : transformerEnds()) {
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
                return CGMESUnitConverter.toKV(ratedU);
            }
        }
        return null;
    }

    /**
     * If {@code containerUri} is a Bay, return its VoltageLevel; otherwise return the URI as-is
     * when it looks like / is a VoltageLevel resource.
     */
    private String resolveVoltageLevelUri(String containerUri) {
        if (containerUri == null) return null;
        Resource res = jenaModel.getResource(containerUri);
        Property bayVl = jenaModel.createProperty(cimNamespace + "Bay.VoltageLevel");
        Statement st = res.getProperty(bayVl);
        if (st != null && st.getObject().isResource()) {
            return st.getObject().asResource().getURI();
        }
        return containerUri;
    }

    /**
     * Get nominal voltage from VoltageLevel resource.
     * Tries: nominalVoltage property → BaseVoltage reference → VL name (fallback).
     * Also accepts a Bay URI and chases {@code Bay.VoltageLevel}.
     */
    public Double getVLRatedVoltage(String vlUri) {
        if (vlUri == null) return null;
        vlUri = resolveVoltageLevelUri(vlUri);

        // Try nominalVoltage property directly
        Resource vlRes = jenaModel.getResource(vlUri);
        Property nomVProp = jenaModel.createProperty(cimNamespace + "VoltageLevel.nominalVoltage");
        Statement stmt = vlRes.getProperty(nomVProp);
        if (stmt != null && stmt.getObject().isLiteral()) {
            double v = stmt.getObject().asLiteral().getDouble();
            if (v > 1000) v = v / 1000.0; // normalize V → kV
            if (v > 0) return v;
        }

        // Try BaseVoltage reference
        Property bvProp = jenaModel.createProperty(cimNamespace + "VoltageLevel.BaseVoltage");
        stmt = vlRes.getProperty(bvProp);
        if (stmt != null && stmt.getObject().isResource()) {
            String bvUri = stmt.getObject().asResource().getURI();
            Double val = getBaseVoltageValue(bvUri);
            if (val != null && val > 0) return val;
        }

        // Fallback: VoltageLevel name is often the nominal voltage (e.g. "380.0", "220.0"),
        // ReliCap-style "VL_220" / "VL-380", or embeds kV like "EDO132KV".
        // Do NOT treat arbitrary trailing digits (e.g. Bay name "BAY_61-62_0") as kV.
        Property nameProp = jenaModel.createProperty(cimNamespace + "IdentifiedObject.name");
        stmt = vlRes.getProperty(nameProp);
        if (stmt != null && stmt.getObject().isLiteral()) {
            Double fromName = parseVoltageFromName(stmt.getObject().asLiteral().getString());
            if (fromName != null) return fromName;
        }
        return null;
    }

    /**
     * Parse a plausible base voltage (kV) from a VoltageLevel name.
     * @return kV or null if the name is not a voltage label
     */
    static Double parseVoltageFromName(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        try {
            double v = Double.parseDouble(n);
            return v > 0 ? v : null;
        } catch (NumberFormatException ignore) {
            // continue
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)^VL[_-]?(\\d+(?:\\.\\d+)?)$")
                .matcher(n);
        if (m.matches()) {
            double v = Double.parseDouble(m.group(1));
            return v > 0 ? v : null;
        }
        m = java.util.regex.Pattern
                .compile("(?i)(\\d+(?:\\.\\d+)?)\\s*kV$")
                .matcher(n);
        if (m.find()) {
            double v = Double.parseDouble(m.group(1));
            return v > 0 ? v : null;
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
