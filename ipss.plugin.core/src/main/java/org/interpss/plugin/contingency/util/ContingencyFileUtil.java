package org.interpss.plugin.contingency.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.definition.json.ContingencyJson;
import org.interpss.plugin.contingency.definition.json.ContingencyListJson;
import org.interpss.plugin.contingency.definition.json.MetadataJson;
import org.interpss.plugin.contingency.definition.json.MonitoredBranchJson;
import org.interpss.plugin.contingency.definition.json.MonitoredBranchListJson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

    public static List<MonitoredBranchRecord> importMonitoredBranchesFromJson(File file) throws IOException {
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
    /**
     * @param file
     * @param branches 
     */
    public static void exportMonitoredBranchesToJson(File file, List<MonitoredBranchRecord> branches) throws IOException {
	    
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
	  
    
}
