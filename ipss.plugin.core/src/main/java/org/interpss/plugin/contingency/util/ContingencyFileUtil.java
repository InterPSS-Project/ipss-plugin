package org.interpss.plugin.contingency.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.interpss.core.algo.dclf.definition.MonitoredBranchRecord;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredInterfaceRecord;
import org.interpss.plugin.contingency.definition.json.ContingencyJson;
import org.interpss.plugin.contingency.definition.json.ContingencyListJson;
import org.interpss.plugin.contingency.definition.json.DclfMonitoringConfigJson;
import org.interpss.plugin.contingency.definition.json.FlowgateContingencyRefJson;
import org.interpss.plugin.contingency.definition.json.FlowgateJson;
import org.interpss.plugin.contingency.definition.json.FlowgateLimitSetJson;
import org.interpss.plugin.contingency.definition.json.MetadataJson;
import org.interpss.plugin.contingency.definition.json.MonitoringExceptionJson;
import org.interpss.plugin.contingency.definition.json.MonitoredBranchJson;
import org.interpss.plugin.contingency.definition.json.MonitoredBranchListJson;
import org.interpss.plugin.contingency.definition.json.MonitoredInterfaceBranchJson;
import org.interpss.plugin.contingency.definition.json.MonitoredInterfaceJson;
import org.interpss.plugin.contingency.definition.json.MonitoredInterfaceListJson;
import org.interpss.plugin.contingency.definition.json.NomogramConstraintJson;
import org.interpss.plugin.contingency.definition.json.NomogramJson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.interpss.core.algo.dclf.check.MonitoringExceptionRecord;
import com.interpss.core.algo.dclf.check.MonitoringExceptionStatus;
import com.interpss.core.algo.dclf.check.MonitoringObjectType;
import com.interpss.core.algo.dclf.definition.DclfMonitoringConfigRecord;
import com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord;
import com.interpss.core.algo.dclf.definition.FlowgateContingencyRef;
import com.interpss.core.algo.dclf.definition.FlowgateContingencyType;
import com.interpss.core.algo.dclf.definition.FlowgateLimitSelection;
import com.interpss.core.algo.dclf.definition.FlowgateLimitSet;
import com.interpss.core.algo.dclf.definition.NomogramConstraintRecord;
import com.interpss.core.algo.dclf.definition.NomogramRecord;

public class ContingencyFileUtil {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContingencyFileUtil.class);

    /**
	 * Import contingencies from JSON file
	 */
	public static List<BranchContingencyRecord> importContingenciesFromJson(File file) throws IOException {

        List<BranchContingencyRecord> contList = new java.util.ArrayList<>();
	    Gson gson = new Gson();
	    ContingencyListJson jsonData;
	    
	    try (java.io.FileReader reader = new java.io.FileReader(file)) {
	        jsonData = gson.fromJson(reader, ContingencyListJson.class);
	    }
	    
	    if (jsonData == null || jsonData.contingencies == null) {
	        throw new IOException("Invalid contingency file format");
	    }
	    
	    // Convert JSON contingencies to ContingencyRecord objects
	    for (ContingencyJson jsonCont : jsonData.contingencies) {
	        BranchContingencyRecord record = new BranchContingencyRecord(
	            jsonCont.name,
	            jsonCont.element_type,
	            jsonCont.action_type,
	            jsonCont.from_bus,
	            jsonCont.to_bus,
	            jsonCont.circuit,
	            jsonCont.from_bus_area,
	            jsonCont.to_bus_area,
	            jsonCont.base_kv,
	            jsonCont.pre_contingency_flow_mw
	        );
	        contList.add(record);
	    }
	    
	    log.info("Imported {} contingencies from file: {}", jsonData.contingencies.size(), file.getName());
	    return contList;
	}
	
	/**
	 * Export contingencies to JSON file
	 */
	public static void exportContingenciesToJson(File file, List<BranchContingencyRecord> contingencies) throws IOException {
	    // Create JSON structure
	    ContingencyListJson jsonData = new ContingencyListJson();
	    jsonData.contingencies = contingencies.stream()
	        .map(rec -> {
	            ContingencyJson c = new ContingencyJson();
	            c.name = rec.name;
	            c.element_type = rec.elementType;
	            c.action_type = rec.actionType;
	            c.from_bus = rec.fromBus;
	            c.to_bus = rec.toBus;
	            c.circuit = rec.ckt;
	            c.from_bus_area = rec.fromBusArea;
	            c.to_bus_area = rec.toBusArea;
	            c.base_kv = rec.baseKv;
	            c.pre_contingency_flow_mw = rec.preContingencyFlowMW;
	            return c;
	        })
	        .collect(Collectors.toList());
	    
	    jsonData.metadata = new MetadataJson();
	    jsonData.metadata.total_count = contingencies.size();
	    jsonData.metadata.created_date = LocalDateTime.now().toString();
	    jsonData.metadata.description = "User-defined contingency list for DCLF analysis";
	    
	    // Write to file with pretty printing
	    Gson gson = new GsonBuilder().setPrettyPrinting().create();
	    try (FileWriter writer = new FileWriter(file)) {
	        gson.toJson(jsonData, writer);
	    }
	}

    public static List<MonitoredBranchRecord> importMonitoredBranchRecordsFromJson(File file) throws IOException {
        List<MonitoredBranchRecord> monitoredBranches = new java.util.ArrayList<>();
    
        Gson gson = new Gson();
        MonitoredBranchListJson jsonData;
        
        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            jsonData = gson.fromJson(reader, MonitoredBranchListJson.class);
        }
        
        if (jsonData == null || jsonData.monitored_branches == null) {
            throw new IOException("Invalid monitored branches file format");
        }
        
        // Clear existing monitored branches
        monitoredBranches.clear();
        
        // Convert JSON monitored branches to MonitoredBranchRecord objects
        for (MonitoredBranchJson jsonBranch : jsonData.monitored_branches) {
            MonitoredBranchRecord record = new MonitoredBranchRecord(
                jsonBranch.from_bus,
                jsonBranch.to_bus,
                jsonBranch.circuit,
                jsonBranch.from_bus_area,
                jsonBranch.to_bus_area,
                jsonBranch.base_kv,
                jsonBranch.pre_contingency_flow_mw
            );
            monitoredBranches.add(record);
        }
        
        log.info("Imported {} monitored branches from file: {}", jsonData.monitored_branches.size(), file.getName());
    
        return monitoredBranches;
	}

    public static List<MonitoredInterfaceRecord> importMonitoredInterfaceRecordsFromJson(File file) throws IOException {
        List<MonitoredInterfaceRecord> monitoredInterfaces = new java.util.ArrayList<>();

        Gson gson = new Gson();
        MonitoredInterfaceListJson jsonData;

        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            jsonData = gson.fromJson(reader, MonitoredInterfaceListJson.class);
        }

        if (jsonData == null || jsonData.monitored_interfaces == null) {
            throw new IOException("Invalid monitored interface file format");
        }

        for (MonitoredInterfaceJson jsonInterface : jsonData.monitored_interfaces) {
            if (jsonInterface.id == null || jsonInterface.id.isBlank()) {
                throw new IOException("Monitored interface id cannot be blank");
            }
            double limitMW = jsonInterface.limit_mw != null
                    ? jsonInterface.limit_mw
                    : jsonInterface.rating_mw != null ? jsonInterface.rating_mw : 0.0;
            MonitoredInterfaceRecord record =
                    new MonitoredInterfaceRecord(jsonInterface.id, limitMW);
            if (jsonInterface.branches != null) {
                for (MonitoredInterfaceBranchJson jsonBranch : jsonInterface.branches) {
                    record.addBranch(toMonitoredInterfaceBranchRecord(jsonBranch));
                }
            }
            monitoredInterfaces.add(record);
        }

        log.info("Imported {} monitored interfaces from file: {}",
                jsonData.monitored_interfaces.size(), file.getName());

        return monitoredInterfaces;
    }

    public static DclfMonitoringConfigRecord importDclfMonitoringConfigFromJson(File file) throws IOException {
        Gson gson = new Gson();
        DclfMonitoringConfigJson jsonData;

        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            jsonData = gson.fromJson(reader, DclfMonitoringConfigJson.class);
        }

        if (jsonData == null) {
            throw new IOException("Invalid DCLF monitoring config file format");
        }

        List<MonitoredBranchRecord> monitoredBranches =
                toMonitoredBranchRecords(jsonData.monitored_branches);
        List<MonitoredInterfaceRecord> monitoredInterfaces =
                toMonitoredInterfaceRecords(jsonData.monitored_interfaces);
        Map<String, MonitoredInterfaceRecord> interfaceById = monitoredInterfaces.stream()
                .collect(Collectors.toMap(
                        MonitoredInterfaceRecord::getId,
                        i -> i,
                        (a, b) -> a,
                        LinkedHashMap::new));
        List<FlowgateConstraintRecord> flowgates = toFlowgateRecords(jsonData.flowgates);
        List<NomogramRecord> nomograms =
                toNomogramRecords(jsonData.nomograms, interfaceById);
        List<MonitoringExceptionRecord> monitoringExceptions =
                toMonitoringExceptionRecords(jsonData.monitoring_exceptions);

        log.info("Imported DCLF monitoring config from file: {}, branches={}, interfaces={}, flowgates={}, nomograms={}, exceptions={}",
                file.getName(),
                monitoredBranches.size(),
                monitoredInterfaces.size(),
                flowgates.size(),
                nomograms.size(),
                monitoringExceptions.size());

        return new DclfMonitoringConfigRecord(
                monitoredBranches,
                monitoredInterfaces,
                flowgates,
                nomograms,
                monitoringExceptions);
    }

    private static List<MonitoredBranchRecord> toMonitoredBranchRecords(
            List<MonitoredBranchJson> jsonBranches) {
        List<MonitoredBranchRecord> monitoredBranches = new ArrayList<>();
        if (jsonBranches == null) {
            return monitoredBranches;
        }
        for (MonitoredBranchJson jsonBranch : jsonBranches) {
            monitoredBranches.add(new MonitoredBranchRecord(
                    jsonBranch.from_bus,
                    jsonBranch.to_bus,
                    jsonBranch.circuit,
                    jsonBranch.from_bus_area,
                    jsonBranch.to_bus_area,
                    jsonBranch.base_kv == null ? 0.0 : jsonBranch.base_kv,
                    jsonBranch.pre_contingency_flow_mw == null ? 0.0 : jsonBranch.pre_contingency_flow_mw));
        }
        return monitoredBranches;
    }

    private static List<MonitoredInterfaceRecord> toMonitoredInterfaceRecords(
            List<MonitoredInterfaceJson> jsonInterfaces) throws IOException {
        List<MonitoredInterfaceRecord> monitoredInterfaces = new ArrayList<>();
        if (jsonInterfaces == null) {
            return monitoredInterfaces;
        }
        for (MonitoredInterfaceJson jsonInterface : jsonInterfaces) {
            monitoredInterfaces.add(toMonitoredInterfaceRecord(jsonInterface));
        }
        return monitoredInterfaces;
    }

    private static MonitoredInterfaceRecord toMonitoredInterfaceRecord(
            MonitoredInterfaceJson jsonInterface) throws IOException {
        if (jsonInterface == null) {
            throw new IOException("Monitored interface cannot be null");
        }
        if (jsonInterface.id == null || jsonInterface.id.isBlank()) {
            throw new IOException("Monitored interface id cannot be blank");
        }
        double limitMW = jsonInterface.limit_mw != null
                ? jsonInterface.limit_mw
                : jsonInterface.rating_mw != null ? jsonInterface.rating_mw : 0.0;
        MonitoredInterfaceRecord record =
                new MonitoredInterfaceRecord(jsonInterface.id, limitMW);
        if (jsonInterface.branches != null) {
            for (MonitoredInterfaceBranchJson jsonBranch : jsonInterface.branches) {
                record.addBranch(toMonitoredInterfaceBranchRecord(jsonBranch));
            }
        }
        return record;
    }

    private static List<FlowgateConstraintRecord> toFlowgateRecords(
            List<FlowgateJson> jsonFlowgates) throws IOException {
        List<FlowgateConstraintRecord> flowgates = new ArrayList<>();
        if (jsonFlowgates == null) {
            return flowgates;
        }
        for (FlowgateJson jsonFlowgate : jsonFlowgates) {
            flowgates.add(toFlowgateRecord(jsonFlowgate));
        }
        return flowgates;
    }

    private static FlowgateConstraintRecord toFlowgateRecord(
            FlowgateJson jsonFlowgate) throws IOException {
        if (jsonFlowgate == null) {
            throw new IOException("Flowgate cannot be null");
        }
        if (jsonFlowgate.id == null || jsonFlowgate.id.isBlank()) {
            throw new IOException("Flowgate id cannot be blank");
        }

        FlowgateConstraintRecord record = FlowgateConstraintRecord.of(
                jsonFlowgate.id,
                toFlowgateContingencyRef(jsonFlowgate.contingency_ref),
                toFlowgateLimitSet(jsonFlowgate.limits));
        record.setConstraintType(jsonFlowgate.constraint_type);
        record.setNercId(jsonFlowgate.nerc_id);
        record.setTlrLevel(jsonFlowgate.tlr_level);
        record.setMarketState(jsonFlowgate.market_state);
        record.setShadowPrice(jsonFlowgate.shadow_price);
        record.setInterval(jsonFlowgate.interval);
        record.setGmtIntervalEnd(jsonFlowgate.gmt_interval_end);
        record.setMonitoredFacilityName(jsonFlowgate.monitored_facility_name);
        record.setContingentFacilityName(jsonFlowgate.contingent_facility_name);
        if (jsonFlowgate.branches != null) {
            for (MonitoredInterfaceBranchJson branch : jsonFlowgate.branches) {
                record.addBranch(toMonitoredInterfaceBranchRecord(branch));
            }
        }
        if (jsonFlowgate.metadata != null) {
            record.getMetadata().putAll(jsonFlowgate.metadata);
        }
        return record;
    }

    private static FlowgateContingencyRef toFlowgateContingencyRef(
            FlowgateContingencyRefJson jsonRef) throws IOException {
        if (jsonRef == null || jsonRef.type == null || jsonRef.type.isBlank()) {
            return FlowgateContingencyRef.base();
        }
        FlowgateContingencyType type = parseEnum(
                FlowgateContingencyType.class,
                jsonRef.type,
                "flowgate contingency type");
        List<String> outageBranchIds = new ArrayList<>();
        if (jsonRef.outage_branch_ids != null) {
            outageBranchIds.addAll(jsonRef.outage_branch_ids);
        }
        if (jsonRef.outage_branch_id != null && !jsonRef.outage_branch_id.isBlank()) {
            outageBranchIds.add(jsonRef.outage_branch_id);
        }
        return switch (type) {
            case BASE -> FlowgateContingencyRef.base();
            case SINGLE_BRANCH_OPEN -> {
                if (outageBranchIds.size() != 1) {
                    throw new IOException("SINGLE_BRANCH_OPEN flowgate contingency requires one outage branch");
                }
                yield FlowgateContingencyRef.singleBranchOpen(outageBranchIds.get(0));
            }
            case MULTI_BRANCH_OPEN -> FlowgateContingencyRef.multiBranchOpen(jsonRef.id, outageBranchIds);
        };
    }

    private static FlowgateLimitSet toFlowgateLimitSet(
            FlowgateLimitSetJson jsonLimits) throws IOException {
        if (jsonLimits == null) {
            throw new IOException("Flowgate limits cannot be null");
        }
        FlowgateLimitSet limits = new FlowgateLimitSet(
                jsonLimits.source_limit_mw,
                jsonLimits.realtime_effective_limit_mw,
                jsonLimits.initial_effective_limit_mw);
        if (jsonLimits.selection_policy != null && !jsonLimits.selection_policy.isBlank()) {
            limits.setSelectionPolicy(parseEnum(
                    FlowgateLimitSelection.class,
                    jsonLimits.selection_policy,
                    "flowgate limit selection policy"));
        }
        limits.selectedLimitMW();
        return limits;
    }

    private static List<NomogramRecord> toNomogramRecords(
            List<NomogramJson> jsonNomograms,
            Map<String, MonitoredInterfaceRecord> interfaceById) throws IOException {
        List<NomogramRecord> nomograms = new ArrayList<>();
        if (jsonNomograms != null) {
            for (NomogramJson jsonNomogram : jsonNomograms) {
                nomograms.add(toNomogramRecord(jsonNomogram, interfaceById));
            }
        }
        return nomograms;
    }

    private static NomogramRecord toNomogramRecord(
            NomogramJson jsonNomogram,
            Map<String, MonitoredInterfaceRecord> interfaceById) throws IOException {
        if (jsonNomogram == null) {
            throw new IOException("Nomogram cannot be null");
        }
        if (jsonNomogram.id == null || jsonNomogram.id.isBlank()) {
            throw new IOException("Nomogram id cannot be blank");
        }
        MonitoredInterfaceRecord axisA = resolveNomogramAxis(
                jsonNomogram.axis_a_id,
                jsonNomogram.axis_a,
                interfaceById,
                "axis_a");
        MonitoredInterfaceRecord axisB = resolveNomogramAxis(
                jsonNomogram.axis_b_id,
                jsonNomogram.axis_b,
                interfaceById,
                "axis_b");
        List<NomogramConstraintRecord> constraints = toNomogramConstraints(
                jsonNomogram.constraints,
                jsonNomogram.id);
        return new NomogramRecord(jsonNomogram.id, axisA, axisB, constraints);
    }

    private static List<NomogramConstraintRecord> toNomogramConstraints(
            List<NomogramConstraintJson> jsonConstraints,
            String nomogramId) throws IOException {
        List<NomogramConstraintRecord> constraints = new ArrayList<>();
        if (jsonConstraints == null || jsonConstraints.isEmpty()) {
            throw new IOException("Nomogram " + nomogramId + " must define at least one constraint");
        }
        for (NomogramConstraintJson jsonConstraint : jsonConstraints) {
            if (jsonConstraint == null) {
                throw new IOException("Nomogram constraint cannot be null");
            }
            if (jsonConstraint.id == null || jsonConstraint.id.isBlank()) {
                throw new IOException("Nomogram constraint id cannot be blank");
            }
            if (jsonConstraint.limit_mw == null) {
                throw new IOException("Nomogram constraint limit_mw is required");
            }
            constraints.add(new NomogramConstraintRecord(
                    jsonConstraint.id,
                    jsonConstraint.coefficient_a == null ? 1.0 : jsonConstraint.coefficient_a,
                    jsonConstraint.coefficient_b == null ? 1.0 : jsonConstraint.coefficient_b,
                    jsonConstraint.limit_mw));
        }
        return constraints;
    }

    private static MonitoredInterfaceRecord resolveNomogramAxis(
            String axisId,
            MonitoredInterfaceJson inlineAxis,
            Map<String, MonitoredInterfaceRecord> interfaceById,
            String fieldName) throws IOException {
        if (axisId != null && !axisId.isBlank()) {
            MonitoredInterfaceRecord axis = interfaceById.get(axisId);
            if (axis == null) {
                throw new IOException("Nomogram " + fieldName + " references unknown interface: " + axisId);
            }
            return axis;
        }
        if (inlineAxis != null) {
            return toMonitoredInterfaceRecord(inlineAxis);
        }
        throw new IOException("Nomogram " + fieldName + " requires an interface id or inline interface definition");
    }

    private static List<MonitoringExceptionRecord> toMonitoringExceptionRecords(
            List<MonitoringExceptionJson> jsonExceptions) throws IOException {
        List<MonitoringExceptionRecord> exceptions = new ArrayList<>();
        if (jsonExceptions == null) {
            return exceptions;
        }
        for (MonitoringExceptionJson jsonException : jsonExceptions) {
            exceptions.add(toMonitoringExceptionRecord(jsonException));
        }
        return exceptions;
    }

    private static MonitoringExceptionRecord toMonitoringExceptionRecord(
            MonitoringExceptionJson jsonException) throws IOException {
        if (jsonException == null) {
            throw new IOException("Monitoring exception cannot be null");
        }
        MonitoringExceptionRecord record = new MonitoringExceptionRecord(
                required(jsonException.contingency_id, "monitoring exception contingency_id"),
                parseEnum(MonitoringObjectType.class, jsonException.object_type, "monitoring exception object_type"),
                required(jsonException.object_id, "monitoring exception object_id"),
                parseEnum(MonitoringExceptionStatus.class, jsonException.status, "monitoring exception status"));
        record.setCheckId(jsonException.check_id);
        return record;
    }

    private static String required(String value, String fieldName) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(fieldName + " is required");
        }
        return value;
    }

    private static <T extends Enum<T>> T parseEnum(
            Class<T> enumType,
            String value,
            String fieldName) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(fieldName + " is required");
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid " + fieldName + ": " + value, e);
        }
    }

    private static MonitoredBranchRecord toMonitoredInterfaceBranchRecord(
            MonitoredInterfaceBranchJson jsonBranch) throws IOException {
        if (jsonBranch == null) {
            throw new IOException("Monitored interface branch cannot be null");
        }

        double coefficient = jsonBranch.coefficient == null ? 1.0 : jsonBranch.coefficient;
        if (jsonBranch.branch_id != null && !jsonBranch.branch_id.isBlank()) {
            return new MonitoredBranchRecord(jsonBranch.branch_id, coefficient);
        }
        if (jsonBranch.from_bus == null || jsonBranch.to_bus == null || jsonBranch.circuit == null) {
            throw new IOException("Monitored interface branch requires branch_id or from_bus/to_bus/circuit");
        }
        return new MonitoredBranchRecord(
                jsonBranch.from_bus,
                jsonBranch.to_bus,
                jsonBranch.circuit,
                coefficient);
    }

    /**
     * @param file
     * @param branches 
     */
    public static void exportMonitoredBranchRecordsToJson(
            File file,
            List<? extends MonitoredBranchRecord> branches) throws IOException {
	    
	        // Create JSON structure
	        MonitoredBranchListJson jsonData = new MonitoredBranchListJson();
	        jsonData.monitored_branches = branches.stream()
	            .map(rec -> {
	                MonitoredBranchJson b = new MonitoredBranchJson();
	                b.from_bus = rec.fromBus;
	                b.to_bus = rec.toBus;
	                b.circuit = rec.ckt;
	                b.from_bus_area = rec.fromBusArea;
	                b.to_bus_area = rec.toBusArea;
	                b.base_kv = rec.baseKv;
	                b.pre_contingency_flow_mw = rec.preContingencyFlowMW;
	                return b;
	            })
	            .collect(Collectors.toList());
	        
	        jsonData.metadata = new MetadataJson();
	        jsonData.metadata.total_count = branches.size();
	        jsonData.metadata.created_date = LocalDateTime.now().toString();
	        jsonData.metadata.description = "User-defined monitored branch list for contingency analysis";
	        
	        // Write to file with pretty printing
	        Gson gson = new GsonBuilder().setPrettyPrinting().create();
	        try (FileWriter writer = new FileWriter(file)) {
	            gson.toJson(jsonData, writer);
	        }
	        
	        log.info("Exported {} monitored branches to file: {}", branches.size(), file.getName());
        }

    public static void exportMonitoredInterfaceRecordsToJson(
            File file,
            List<MonitoredInterfaceRecord> monitoredInterfaces) throws IOException {
        MonitoredInterfaceListJson jsonData = new MonitoredInterfaceListJson();
        jsonData.monitored_interfaces = monitoredInterfaces.stream()
                .map(rec -> {
                    MonitoredInterfaceJson i = new MonitoredInterfaceJson();
                    i.id = rec.getId();
                    i.limit_mw = rec.getLimitMW();
                    i.branches = rec.getBranches().stream()
                            .map(branch -> {
                                MonitoredInterfaceBranchJson b =
                                        new MonitoredInterfaceBranchJson();
                                b.branch_id = branch.getBranchId();
                                b.from_bus = branch.fromBus;
                                b.to_bus = branch.toBus;
                                b.circuit = branch.ckt;
                                b.coefficient = branch.getCoefficient();
                                return b;
                            })
                            .collect(Collectors.toList());
                    return i;
                })
                .collect(Collectors.toList());

        jsonData.metadata = new MetadataJson();
        jsonData.metadata.total_count = monitoredInterfaces.size();
        jsonData.metadata.created_date = LocalDateTime.now().toString();
        jsonData.metadata.description = "User-defined monitored interface list for DCLF analysis";

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(jsonData, writer);
        }

        log.info("Exported {} monitored interfaces to file: {}",
                monitoredInterfaces.size(), file.getName());
    }
	  
   
}
