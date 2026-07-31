/*
 * CIMDirectParser.java
 *
 * Direct CIM/CGMES → AclfNetwork parser that bypasses the ODM XML layer.
 */

package org.interpss.fadapter.cim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.cim.mapper.CIMGeneratorMapper;
import org.interpss.fadapter.cim.mapper.CIMLineMapper;
import org.interpss.fadapter.cim.mapper.CIMLoadMapper;
import org.interpss.fadapter.cim.mapper.CIMShuntCompensatorMapper;
import org.interpss.fadapter.cim.mapper.CIMTransformer3WMapper;
import org.interpss.fadapter.cim.mapper.CIMTransformerMapper;
import org.interpss.fadapter.cim.parser.CIMRdfParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.net.OriginalDataFormat;

/**
 * Parses CIM RDF/XML (single file or CGMES multi-profile set) into an AclfNetwork
 * via {@link AclfNetworkBuilder}.
 */
public class CIMDirectParser {
    private static final Logger log = LoggerFactory.getLogger(CIMDirectParser.class);

    public static final double DEFAULT_BASE_MVA = 100.0;

    private static int lastLoadCount = 0;

    private CIMModel cimModel;
    private final AclfNetworkBuilder builder;

    public CIMDirectParser() {
        this.builder = new AclfNetworkBuilder();
    }

    /** Number of individual loads mapped in the last conversion. */
    public static int getLastLoadCount() {
        return lastLoadCount;
    }

    public CIMModel getCimModel() {
        return cimModel;
    }

    public AclfNetwork parse(String filepath) throws InterpssException {
        try {
            String content = readFile(filepath);
            CIMRdfParser rdfParser = new CIMRdfParser();
            Model jenaModel = rdfParser.parseString(content);
            this.cimModel = new CIMModel(jenaModel);
            cimModel.buildIndices();
            return buildNetwork(cimModel, filepath);
        } catch (InterpssException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing CIM file: {}", filepath, e);
            throw new InterpssException("Error parsing CIM file: " + filepath + ": " + e.getMessage());
        }
    }

    /**
     * Parse and merge multiple CIM profile files (EQ, TP, SSH, SV, BD, …).
     */
    public AclfNetwork parse(String[] filepaths) throws InterpssException {
        if (filepaths == null || filepaths.length == 0) {
            throw new InterpssException("No CIM files specified");
        }
        if (filepaths.length == 1) {
            return parse(filepaths[0]);
        }
        try {
            CIMRdfParser rdfParser = new CIMRdfParser();
            Model merged = ModelFactory.createDefaultModel();
            for (String path : filepaths) {
                try {
                    String content = readFile(path);
                    Model part = rdfParser.parseString(content);
                    merged.add(part);
                    log.info("Merged CIM file {}, total: {} triples", path, merged.size());
                } catch (Exception e) {
                    log.warn("Skipping CIM file {}: {}", path, e.getMessage());
                }
            }
            this.cimModel = new CIMModel(merged);
            cimModel.buildIndices();
            return buildNetwork(cimModel, filepaths[0]);
        } catch (InterpssException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing CIM files", e);
            throw new InterpssException("Error parsing CIM files: " + e.getMessage());
        }
    }

    private AclfNetwork buildNetwork(CIMModel cimModel, String nameHint) throws Exception {
        String netName = nameHint != null
                ? nameHint.substring(Math.max(nameHint.lastIndexOf('/') + 1, nameHint.lastIndexOf('\\') + 1))
                : "CIM_Import";
        builder.setNetworkInfo("CIM_Import", netName, DEFAULT_BASE_MVA * 1000.0, OriginalDataFormat.CIM);

        convertBuses(cimModel);
        convertBranches(cimModel);
        convertInjections(cimModel);

        builder.finalizeNetwork();

        AclfNetwork net = builder.getNetwork();
        log.info("CIM import: {} buses, {} branches", net.getNoBus(), net.getNoBranch());
        return net;
    }

    private void convertBuses(CIMModel cimModel) throws Exception {
        List<CIMPropertyBag> topoNodes = cimModel.topologicalNodes();
        List<CIMPropertyBag> connNodes = cimModel.connectivityNodes();
        List<CIMPropertyBag> busbars = cimModel.busbarSections();

        int busNumber = 1;

        if (!topoNodes.isEmpty()) {
            log.info("Converting {} TopologicalNodes to buses", topoNodes.size());
            for (CIMPropertyBag tn : topoNodes) {
                String tnId = tn.getId();
                if (cimModel.isBoundaryTopologicalNode(tnId)) {
                    log.debug("Skipping boundary TN: {}", tn.getName());
                    continue;
                }
                String name = tn.getName() != null ? tn.getName() : tn.getLocalId();
                Double baseKV = resolveTopoNodeVoltage(cimModel, tn);
                double baseV = (baseKV != null ? baseKV : 100.0) * 1000.0;

                builder.addBus(tn.getLocalId(), name, busNumber++, baseV, 1.0, 0.0, null, null, null);
                cimModel.mapBusId(tnId, tn.getLocalId());
            }
        } else if (!busbars.isEmpty()) {
            log.info("Using {} BusbarSections as bus proxies", busbars.size());
            for (CIMPropertyBag bb : busbars) {
                String name = bb.getName() != null ? bb.getName() : bb.getLocalId();
                String vlUri = bb.getResourceId("Equipment.EquipmentContainer");
                Double baseKV = vlUri != null ? cimModel.getVLRatedVoltage(vlUri) : null;
                double baseV = (baseKV != null ? baseKV : 100.0) * 1000.0;

                builder.addBus(bb.getLocalId(), name, busNumber++, baseV, 1.0, 0.0, null, null, null);
                cimModel.mapBusId(bb.getId(), bb.getLocalId());
            }
        } else if (!connNodes.isEmpty()) {
            log.info("Using {} ConnectivityNodes as buses", connNodes.size());
            for (CIMPropertyBag cn : connNodes) {
                String cnId = cn.getId();
                String name = cn.getName() != null ? cn.getName() : cn.getLocalId();

                Double baseKV = null;
                String containerUri = cn.getResourceId("ConnectivityNode.ConnectivityNodeContainer");
                if (containerUri != null) {
                    baseKV = cimModel.getVLRatedVoltage(containerUri);
                }
                double baseV = (baseKV != null ? baseKV : 100.0) * 1000.0;

                builder.addBus(cn.getLocalId(), name, busNumber++, baseV, 1.0, 0.0, null, null, null);
                cimModel.mapBusId(cnId, cn.getLocalId());
            }
        }
    }

    private Double resolveTopoNodeVoltage(CIMModel cimModel, CIMPropertyBag tn) {
        String bvUri = tn.getResourceId("TopologicalNode.BaseVoltage");
        if (bvUri != null) {
            Double v = cimModel.getBaseVoltageValue(bvUri);
            if (v != null) return v;
        }
        Double v = cimModel.getNominalVoltageForTopoNode(tn.getId());
        if (v != null) return v;

        java.util.List<String> topoNodes = cimModel.getTopologicalNodesForEquipment(tn.getId());
        if (!topoNodes.isEmpty()) {
            v = cimModel.getNominalVoltageForTopoNode(topoNodes.get(0));
            if (v != null) return v;
        }

        String name = tn.getName();
        if (name != null) {
            try {
                return Double.parseDouble(name);
            } catch (NumberFormatException e) { /* ignore */ }
        }
        return null;
    }

    private void convertBranches(CIMModel cimModel) throws Exception {
        CIMLineMapper lineMapper = new CIMLineMapper(DEFAULT_BASE_MVA);
        lineMapper.setCimModel(cimModel);
        List<CIMPropertyBag> lineSegments = cimModel.acLineSegments();
        log.info("Processing {} ACLineSegments", lineSegments.size());
        for (CIMPropertyBag line : lineSegments) {
            lineMapper.map(line, builder);
        }

        List<CIMPropertyBag> seriesComps = cimModel.seriesCompensators();
        if (!seriesComps.isEmpty()) {
            log.info("Processing {} SeriesCompensators as lines", seriesComps.size());
            for (CIMPropertyBag sc : seriesComps) {
                lineMapper.mapSeriesCompensator(sc, builder);
            }
        }

        CIMTransformerMapper xfr2wMapper = new CIMTransformerMapper(DEFAULT_BASE_MVA);
        xfr2wMapper.setCimModel(cimModel);
        xfr2wMapper.indexEnds(cimModel.transformerEnds());

        CIMTransformer3WMapper xfr3wMapper = new CIMTransformer3WMapper(DEFAULT_BASE_MVA);
        xfr3wMapper.setCimModel(cimModel);

        Map<String, List<CIMPropertyBag>> endsByXfr = new HashMap<>();
        for (CIMPropertyBag end : cimModel.transformerEnds()) {
            String xfrId = end.getResourceId("PowerTransformerEnd.PowerTransformer");
            if (xfrId != null) {
                endsByXfr.computeIfAbsent(xfrId, k -> new ArrayList<>()).add(end);
            }
        }

        for (CIMPropertyBag xfr : cimModel.powerTransformers()) {
            String xfrKey = xfr.getId();
            List<CIMPropertyBag> ends = endsByXfr.get(xfrKey);
            if (ends != null && ends.size() >= 3) {
                ends.sort((a, b) -> {
                    int ea = a.getInt("TransformerEnd.endNumber",
                        a.getInt("PowerTransformerEnd.endNumber", 1));
                    int eb = b.getInt("TransformerEnd.endNumber",
                        b.getInt("PowerTransformerEnd.endNumber", 1));
                    return Integer.compare(ea, eb);
                });
                xfr3wMapper.map3W(xfr, ends, builder);
            } else {
                xfr2wMapper.map(xfr, builder);
            }
        }
    }

    private void convertInjections(CIMModel cimModel) throws Exception {
        int loadCount = 0;

        CIMLoadMapper loadMapper = new CIMLoadMapper(DEFAULT_BASE_MVA);
        loadMapper.setCimModel(cimModel);
        for (CIMPropertyBag load : cimModel.energyConsumers()) {
            int before = loadMapper.getMappedCount();
            loadMapper.map(load, builder);
            if (loadMapper.getMappedCount() > before) loadCount++;
        }
        for (CIMPropertyBag asm : cimModel.asynchronousMachines()) {
            int before = loadMapper.getMappedCount();
            loadMapper.map(asm, builder);
            if (loadMapper.getMappedCount() > before) loadCount++;
        }

        CIMGeneratorMapper genMapper = new CIMGeneratorMapper(DEFAULT_BASE_MVA);
        genMapper.setCimModel(cimModel);
        genMapper.indexGeneratingUnits(cimModel.generatingUnits());
        boolean hasSwing = false;

        for (CIMPropertyBag gen : cimModel.synchronousMachines()) {
            genMapper.map(gen, builder);
            String busId = genMapper.resolveBusId(gen.getId());
            if (busId != null) {
                BaseAclfBus bus = builder.getBus(busId);
                if (bus != null && bus.getGenCode() == AclfGenCode.SWING) {
                    hasSwing = true;
                }
            }
        }

        for (CIMPropertyBag eni : cimModel.externalNetworkInjections()) {
            genMapper.mapExternalNetworkInjection(eni, builder);
            String busId = genMapper.resolveBusId(eni.getId());
            if (busId != null) {
                BaseAclfBus bus = builder.getBus(busId);
                if (bus != null && bus.getGenCode() == AclfGenCode.SWING) {
                    hasSwing = true;
                }
            }
        }

        if (!hasSwing) {
            for (CIMPropertyBag gen : cimModel.synchronousMachines()) {
                String busId = genMapper.resolveBusId(gen.getId());
                if (busId != null && genMapper.promoteToSwing(builder, busId)) {
                    hasSwing = true;
                    break;
                }
            }
        }

        lastLoadCount = loadCount;
        CIMShuntCompensatorMapper shuntMapper = new CIMShuntCompensatorMapper(DEFAULT_BASE_MVA);
        shuntMapper.setCimModel(cimModel);
        for (CIMPropertyBag shunt : cimModel.shuntCompensators()) {
            shuntMapper.map(shunt, builder);
        }
    }

    private static String readFile(String filepath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
