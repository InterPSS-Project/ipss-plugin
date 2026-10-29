package org.interpss.threePhase.dataParser.cim;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import com.interpss.core.acsc.XFormerConnectCode;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

/**
 * Offline CIMHub CIM100 RDF/XML import for distribution power flow at 60 Hz.
 * Imports one feeder and returns a per-unit network. Multiple files may contain
 * equipment and steady-state profiles of the same RDF graph. Unsupported electrical
 * configurations fail explicitly; no partially imported network is published.
 * This class is stateful and is not thread safe.
 */
public final class CimDistributionDataParser {
    private DStabNetwork3Phase network;
    private Map<String, String> busIds = Map.of(), equipmentIds = Map.of(), names = Map.of();
    private Map<String, List<String>> equipmentBranchIds = Map.of();
    private List<String> warnings = List.of();
    private String mappedDss = "";

    public DStabNetwork3Phase parse(Path file) throws IOException {
        return parse(List.of(file), null, 10.0);
    }

    /** @param feederMrid required when the graph contains more than one feeder; accepts mRID or RDF URI. */
    public DStabNetwork3Phase parse(List<Path> files, String feederMrid, double baseMva) throws IOException {
        network = null;
        busIds = Map.of(); equipmentIds = Map.of(); names = Map.of(); equipmentBranchIds = Map.of(); warnings = List.of(); mappedDss = "";
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("At least one CIM RDF/XML file is required");
        if (!Double.isFinite(baseMva) || baseMva <= 0) throw new IllegalArgumentException("baseMva must be finite and positive");
        Path temporary = null;
        try (var graph = new CimDistributionModel()) {
            for (Path file : files) graph.read(file);
            var mapping = new CimDistributionMapper(graph);
            String dss = mapping.map(feederMrid);
            temporary = Files.createTempFile("interpss-cim-", ".dss");
            Files.writeString(temporary, dss);
            var parser = new OpenDSSDataParser();
            parser.setGeometryUseSpecifiedAcResistance(true);
            parser.setPreserveSpecifiedLineImpedance(true);
            parser.setMinLineSeriesImpedancePu(0);
            parser.setNormalizeSinglePhaseLoadVoltageBases(true);
            parser.setRegControlEnabled(false);
            if (!parser.parseFeederData(temporary.getParent().toString(), temporary.getFileName().toString())
                    || !parser.calcVoltageBases() || !parser.convertActualValuesToPU(baseMva))
                throw new IllegalArgumentException("CIM equipment mapping or per-unit conversion failed");
            var candidate = parser.getDistNetwork();
            for (var bus : candidate.getBusList()) {
                if (bus.isSwing()) bus.setVoltageAng(mapping.sourceAngle);
                if (bus.getBaseVoltage() <= 0) throw new IllegalArgumentException("Unenergized/disconnected bus has no voltage base: " + bus.getId());
            }
            // Apply operating taps only after nominal voltage bases and impedances are converted.
            for (var entry : mapping.taps.entrySet()) {
                var branch = parser.getBranchByName(entry.getKey());
                if (branch == null) throw new IllegalArgumentException("Missing mapped transformer " + entry.getKey());
                branch.setFromTurnRatio(branch.getFromTurnRatio() * entry.getValue()[0]);
                branch.setToTurnRatio(branch.getToTurnRatio() * entry.getValue()[1]);
                var adapter = acscXfrAptr.apply(branch);
                int clock = mapping.clocks.get(entry.getKey());
                if (clock != 0) {
                    boolean fromDelta = adapter.getFromGrounding().getXfrConnectCode() != XFormerConnectCode.WYE;
                    var delta = fromDelta ? adapter.getFromGrounding() : adapter.getToGrounding();
                    delta.setXfrConnectCode((fromDelta ? clock == 1 : clock == 11)
                            ? XFormerConnectCode.DELTA11 : XFormerConnectCode.DELTA);
                }
            }
            busIds = Map.copyOf(mapping.busIds); equipmentIds = Map.copyOf(mapping.equipmentIds);
            Map<String, List<String>> branches = new LinkedHashMap<>();
            mapping.equipmentIds.forEach((uri, id) -> {
                var ids = candidate.getBranchList().stream().filter(b -> id.equals(b.getName())
                        || b.getName() != null && b.getName().startsWith(id + "_w")).map(b -> b.getId()).toList();
                if (!ids.isEmpty()) branches.put(uri, ids);
            });
            equipmentBranchIds = Map.copyOf(branches);
            names = Map.copyOf(mapping.names); warnings = List.copyOf(mapping.warnings); mappedDss = dss;
            network = candidate;
            return network;
        } catch (IllegalArgumentException | IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Cannot import CIM distribution model: " + ex.getMessage(), ex);
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    /** Null before import or after a failed import. Values are already per unit. */
    public DStabNetwork3Phase getDistNetwork() { return network; }
    /** ConnectivityNode RDF URI to InterPSS bus ID. */
    public Map<String, String> getBusIds() { return busIds; }
    /** Equipment RDF URI to mapped element name; multiport transformers expand into several branches. */
    public Map<String, String> getEquipmentIds() { return equipmentIds; }
    /** Equipment RDF URI to all network branch IDs, including three-winding primitive blocks. */
    public Map<String, List<String>> getEquipmentBranchIds() { return equipmentBranchIds; }
    public Map<String, String> getCimNames() { return names; }
    public List<String> getWarnings() { return warnings; }
    /** Diagnostic intermediate data; fixed tap/clock adjustments are applied to the network separately. */
    public String getMappedDss() { return mappedDss; }
}
