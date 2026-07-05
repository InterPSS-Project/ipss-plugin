package org.interpss.plugin.contingency.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.ContingencyAction;
import org.interpss.plugin.contingency.definition.ContingencyActionType;
import org.interpss.plugin.contingency.definition.ContingencyDefinition;
import org.interpss.plugin.contingency.definition.ContingencyObjectType;

public final class ContingencyDefinitionAdapter {
    public static final String METADATA_FROM_BUS = "from_bus";
    public static final String METADATA_TO_BUS = "to_bus";
    public static final String METADATA_CIRCUIT = "circuit";
    public static final String METADATA_FROM_BUS_AREA = "from_bus_area";
    public static final String METADATA_TO_BUS_AREA = "to_bus_area";
    public static final String METADATA_BASE_KV = "base_kv";
    public static final String METADATA_PRE_CONTINGENCY_FLOW_MW = "pre_contingency_flow_mw";

    private ContingencyDefinitionAdapter() {
    }

    public static List<ContingencyDefinition> fromBranchRecords(List<BranchContingencyRecord> records) {
        List<ContingencyDefinition> definitions = new ArrayList<>();
        Map<String, ContingencyDefinition> namedDefinitions = new LinkedHashMap<>();
        int unnamedSequence = 1;

        if (records == null) {
            return definitions;
        }

        for (BranchContingencyRecord record : records) {
            String name = trimToNull(record.name);
            ContingencyDefinition definition;
            if (name == null) {
                definition = new ContingencyDefinition(defaultUnnamedName(record, unnamedSequence++));
                definitions.add(definition);
            } else {
                definition = namedDefinitions.get(name);
                if (definition == null) {
                    definition = new ContingencyDefinition(name);
                    namedDefinitions.put(name, definition);
                    definitions.add(definition);
                }
            }
            definition.addAction(toAction(record));
        }
        return definitions;
    }

    public static List<BranchContingencyRecord> toBranchRecords(List<ContingencyDefinition> definitions) {
        List<BranchContingencyRecord> records = new ArrayList<>();
        if (definitions == null) {
            return records;
        }

        for (ContingencyDefinition definition : definitions) {
            if (definition == null || definition.actions == null) {
                continue;
            }
            for (ContingencyAction action : definition.actions) {
                requireSupported(action);
                records.add(toBranchRecord(definition.name, action));
            }
        }
        return records;
    }

    public static List<String> flattenBranchIds(List<ContingencyDefinition> definitions) {
        List<String> branchIds = new ArrayList<>();
        if (definitions == null) {
            return branchIds;
        }

        for (ContingencyDefinition definition : definitions) {
            if (definition == null || definition.actions == null) {
                continue;
            }
            for (ContingencyAction action : definition.actions) {
                requireSupported(action);
                branchIds.add(action.objectId);
            }
        }
        return branchIds;
    }

    private static ContingencyAction toAction(BranchContingencyRecord record) {
        ContingencyAction action = new ContingencyAction(
                ContingencyObjectType.BRANCH,
                toActionType(record.actionType),
                branchId(record));
        putIfNotBlank(action.metadata, METADATA_FROM_BUS, record.fromBus);
        putIfNotBlank(action.metadata, METADATA_TO_BUS, record.toBus);
        putIfNotBlank(action.metadata, METADATA_CIRCUIT, record.ckt);
        putIfNotBlank(action.metadata, METADATA_FROM_BUS_AREA, record.fromBusArea);
        putIfNotBlank(action.metadata, METADATA_TO_BUS_AREA, record.toBusArea);
        if (record.baseKv != 0.0) {
            action.metadata.put(METADATA_BASE_KV, Double.toString(record.baseKv));
        }
        if (record.preContingencyFlowMW != 0.0) {
            action.metadata.put(
                    METADATA_PRE_CONTINGENCY_FLOW_MW,
                    Double.toString(record.preContingencyFlowMW));
        }
        return action;
    }

    private static BranchContingencyRecord toBranchRecord(String name, ContingencyAction action) {
        BranchTerminal terminal = terminal(action);
        return new BranchContingencyRecord(
                name,
                "Branch",
                action.actionType.name(),
                terminal.fromBus(),
                terminal.toBus(),
                terminal.circuit(),
                metadataValue(action, METADATA_FROM_BUS_AREA),
                metadataValue(action, METADATA_TO_BUS_AREA),
                metadataDouble(action, METADATA_BASE_KV),
                metadataDouble(action, METADATA_PRE_CONTINGENCY_FLOW_MW));
    }

    private static void requireSupported(ContingencyAction action) {
        if (action == null) {
            throw new IllegalArgumentException("Contingency action cannot be null");
        }
        if (action.objectType != ContingencyObjectType.BRANCH) {
            throw new IllegalArgumentException("Unsupported contingency object type: " + action.objectType);
        }
        if (action.actionType != ContingencyActionType.OPEN) {
            throw new IllegalArgumentException("Unsupported contingency action type: " + action.actionType);
        }
        if (isBlank(action.objectId)) {
            throw new IllegalArgumentException("Contingency action objectId is required");
        }
    }

    private static ContingencyActionType toActionType(String actionType) {
        String normalized = actionType == null ? "" : actionType.trim().toUpperCase(Locale.ROOT);
        if ("OPEN".equals(normalized)) {
            return ContingencyActionType.OPEN;
        }
        throw new IllegalArgumentException("Unsupported branch contingency action type: " + actionType);
    }

    private static String branchId(BranchContingencyRecord record) {
        if (!isBlank(record.fromBus) && !isBlank(record.toBus) && !isBlank(record.ckt)) {
            return record.fromBus + "->" + record.toBus + "(" + record.ckt + ")";
        }
        throw new IllegalArgumentException("Branch contingency record requires fromBus, toBus, and ckt");
    }

    private static BranchTerminal terminal(ContingencyAction action) {
        String fromBus = metadataValue(action, METADATA_FROM_BUS);
        String toBus = metadataValue(action, METADATA_TO_BUS);
        String circuit = metadataValue(action, METADATA_CIRCUIT);
        if (!isBlank(fromBus) && !isBlank(toBus) && !isBlank(circuit)) {
            return new BranchTerminal(fromBus, toBus, circuit);
        }

        String objectId = action.objectId.trim();
        int arrow = objectId.indexOf("->");
        int openParen = objectId.lastIndexOf('(');
        int closeParen = objectId.endsWith(")") ? objectId.length() - 1 : -1;
        if (arrow > 0 && openParen > arrow + 2 && closeParen > openParen) {
            return new BranchTerminal(
                    objectId.substring(0, arrow),
                    objectId.substring(arrow + 2, openParen),
                    objectId.substring(openParen + 1, closeParen));
        }
        throw new IllegalArgumentException("Cannot derive branch terminals from objectId: " + action.objectId);
    }

    private static String defaultUnnamedName(BranchContingencyRecord record, int sequence) {
        return "BRANCH_OPEN_" + sequence + "_" + branchId(record);
    }

    private static String metadataValue(ContingencyAction action, String key) {
        if (action.metadata == null) {
            return null;
        }
        return action.metadata.get(key);
    }

    private static double metadataDouble(ContingencyAction action, String key) {
        String value = metadataValue(action, key);
        if (isBlank(value)) {
            return 0.0;
        }
        return Double.parseDouble(value);
    }

    private static void putIfNotBlank(Map<String, String> metadata, String key, String value) {
        if (!isBlank(value)) {
            metadata.put(key, value);
        }
    }

    private static String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record BranchTerminal(String fromBus, String toBus, String circuit) {
    }
}
