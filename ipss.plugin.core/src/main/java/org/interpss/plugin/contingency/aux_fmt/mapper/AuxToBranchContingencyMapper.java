package org.interpss.plugin.contingency.aux_fmt.mapper;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.interpss.plugin.contingency.aux_fmt.AuxConversionOptions;
import org.interpss.plugin.contingency.aux_fmt.AuxConversionReport;
import org.interpss.plugin.contingency.aux_fmt.bean.AuxContingency;
import org.interpss.plugin.contingency.aux_fmt.bean.AuxCtgElement;
import org.interpss.plugin.contingency.aux_fmt.bean.AuxParsedData;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;

public class AuxToBranchContingencyMapper {
    private final AuxConversionOptions options;
    private final AuxBranchResolver branchResolver;

    public AuxToBranchContingencyMapper(AuxConversionOptions options, AuxBranchResolver branchResolver) {
        this.options = options;
        this.branchResolver = Objects.requireNonNull(branchResolver, "branchResolver");
    }

    public java.util.List<BranchContingencyRecord> map(AuxParsedData parsedData, AuxConversionReport report) {
        Set<String> knownContingencies = new LinkedHashSet<>();
        for (AuxContingency contingency : parsedData.getContingencies()) {
            knownContingencies.add(contingency.getName());
        }

        java.util.List<BranchContingencyRecord> records = new java.util.ArrayList<>();
        for (AuxCtgElement element : parsedData.getCtgElements()) {
            String name = firstPresent(element, "contingency", "ctgname", "contingencyname", "name");
            if (isBlank(name)) {
                skip(element, report, "missing parent contingency name");
                continue;
            }
            if (!knownContingencies.isEmpty() && !knownContingencies.contains(name)) {
                report.addWarning("Line " + element.getLineNumber()
                        + ": CTGElement references contingency not declared in Contingency block: " + name);
            }

            String object = firstPresent(element, "object", "objectid", "device", "element");
            ResolvedBranchRef branchObject = resolveBranchObject(object);

            String elementType = firstPresent(element, "elementtype", "objecttype", "devicetype", "object", "type");
            if (!isSupportedBranchType(elementType)) {
                unsupported(element, report, "unsupported element type: " + elementType);
                continue;
            }

            String actionType = normalizeAction(firstPresent(element, "action", "ctgaction", "status", "operation"));
            if (actionType == null) {
                unsupported(element, report, "unsupported action: "
                        + firstPresent(element, "action", "ctgaction", "status", "operation"));
                continue;
            }

            String fromBus = normalizeBusId(firstPresent(element, "busnumfrom", "frombus", "frombusnum", "busnum"));
            String toBus = normalizeBusId(firstPresent(element, "busnumto", "tobus", "tobusnum"));
            if ((isBlank(fromBus) || isBlank(toBus)) && branchObject != null) {
                fromBus = normalizeBusId(branchObject.fromBus());
                toBus = normalizeBusId(branchObject.toBus());
            }
            if (isBlank(fromBus) || isBlank(toBus)) {
                skip(element, report, "missing branch terminal bus");
                continue;
            }

            String circuit = firstPresent(element, "circuit", "circuitid", "ckt", "id");
            if (isBlank(circuit) && branchObject != null) {
                circuit = branchObject.circuit();
            }
            if (isBlank(circuit)) {
                circuit = options.getDefaultCircuitId();
            }
            double baseKv = parseDouble(firstPresent(element, "basekv", "kv"),
                    branchObject != null ? branchObject.baseKv() : 0.0);

            BranchContingencyRecord record = new BranchContingencyRecord(
                    name,
                    "Branch",
                    actionType,
                    fromBus,
                    toBus,
                    circuit,
                    firstPresent(element, "frombusarea", "areafrom"),
                    firstPresent(element, "tobusarea", "areato"),
                    baseKv,
                    parseDouble(firstPresent(element, "precontingencyflowmw", "mw", "flowmw"), 0.0));
            records.add(record);
            report.incrementEmittedBranchRecordCount();
        }
        return records;
    }

    private ResolvedBranchRef resolveBranchObject(String object) {
        return branchResolver.resolve(object).orElse(null);
    }

    private void skip(AuxCtgElement element, AuxConversionReport report, String reason) {
        report.incrementSkippedElementCount();
        report.addWarning("Line " + element.getLineNumber() + ": skipped CTGElement: " + reason);
    }

    private void unsupported(AuxCtgElement element, AuxConversionReport report, String reason) {
        report.incrementUnsupportedElementCount();
        String warning = "Line " + element.getLineNumber() + ": unsupported CTGElement: " + reason;
        if (options.getUnsupportedElementPolicy() == AuxConversionOptions.UnsupportedElementPolicy.FAIL) {
            throw new IllegalArgumentException(warning);
        }
        report.addWarning(warning);
    }

    private boolean isSupportedBranchType(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("branch")
                || normalized.contains("line")
                || normalized.contains("transformer");
    }

    private String normalizeAction(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("open")
                || normalized.contains("trip")
                || normalized.contains("disconnect")
                || normalized.contains("remove")
                || normalized.equals("out")) {
            return "OPEN";
        }
        if (normalized.contains("close") || normalized.contains("connect") || normalized.equals("in")) {
            return "CLOSE";
        }
        return null;
    }

    private String normalizeBusId(String value) {
        if (isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (options.getBusIdMode() == AuxConversionOptions.BusIdMode.PRESERVE) {
            return trimmed;
        }
        if (trimmed.matches("\\d+") && !trimmed.startsWith("Bus")) {
            return "Bus" + trimmed;
        }
        return trimmed;
    }

    private static String firstPresent(AuxCtgElement element, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = element.get(fieldName);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static double parseDouble(String value, double defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
