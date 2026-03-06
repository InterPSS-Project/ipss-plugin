package sample.dframe.ca;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.dflib.DataFrame;
import org.interpss.plugin.contingency.DclfContingencyConfig;
import org.interpss.plugin.contingency.ParallelDclfContingencyAnalyzer;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.interpss.plugin.result.dframe.ca.DclfContingencyDFrameAdapter;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class DclfContDFAdapter_Texas2kSample {

    public static void main(String args[]) throws Exception {
        AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v36/texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.psseVersion(PsseVersion.PSSE_36)
				.load()
				.getImportedObj();	
        
		// run Dclf
		ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
		algo.calculateDclf(DclfMethod.INC_LOSS);

		//import contingency definitions from CA file
		File contFile = new File("testData/psse/v36/texas2k/2k_contingencies_115kVAbove.json");
		List<BranchContingencyRecord> contingencies = ContingencyFileUtil.importContingenciesFromJson(contFile);
		List<DclfBranchOutage> contList = new java.util.ArrayList<>();

		for (BranchContingencyRecord record : contingencies) {
			try {
				// Find the branch based on from_bus and to_bus
				String branchId = record.fromBus + "->" + record.toBus+"("+record.ckt+")";
				if (net.getBranch(branchId) != null) {
					DclfBranchOutage cont = createContingency(record.name);
					
					// Determine outage type based on action type
					ContingencyBranchOutageType outageType;
					switch (record.actionType.toLowerCase()) {
						case "open":
							outageType = ContingencyBranchOutageType.OPEN;
							break;
						case "close":
							outageType = ContingencyBranchOutageType.CLOSE;
							break;
						default:
							outageType = ContingencyBranchOutageType.OPEN; // Default to open
					}
					
					DclfOutageBranch outage = createCaOutageBranch(algo.getDclfAlgoBranch(branchId), outageType);
					cont.setOutageEquip(outage);
					contList.add(cont);
				}
			} catch (Exception ex) {
				throw new Exception("Warning: Could not create contingency for " + record.name + ": " + ex.getMessage() + "\n");
			}
		}

		//import monitored branches from JSON file
		File monFile = new File("testData/psse/v36/texas2k/2k_monitored_branches.json");
		List<MonitoredBranchRecord> monitoredBranches = ContingencyFileUtil.importMonitoredBranchRecordsFromJson(monFile);

		Set<String> monitoredBranchIds = monitoredBranches.stream()
										.map(record -> record.getBranchId()).collect(Collectors.toSet());

		// define the contingency analysis configuration
	    DclfContingencyConfig config =  new DclfContingencyConfig();
	    config.setDclfInclLoss(true);
		config.setOverloadThreshold(100); // in percentage	

		ConcurrentLinkedQueue<BranchCAResultRec> results = 
				ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
								net, contList, monitoredBranchIds, 
								config.getOverloadThreshold(), config.isDclfInclLoss(), 8);	

		// print the results
		for (BranchCAResultRec rec : results) {
			//System.out.println(rec.toString());
			String branchId = rec.aclfBranch.getId();
			String contingencyName = rec.contingency.getId();
			Double postFlowMW = rec.getPostFlowMW();
			Double lineRatingMW = rec.calBranchRateB();
			Double loadingPercent = rec.calLoadingPercent();
			System.out.println(String.format("{\n  \"branch_id\": \"%s\",\n  \"contingency_name\": \"%s\",\n  \"post_flow_mw\": %.2f,\n  \"line_rating_mw\": %.2f,\n  \"loading_percent\": %.2f\n}", 
                branchId, contingencyName, postFlowMW, lineRatingMW, loadingPercent));
		}
		
		DclfContingencyDFrameAdapter dfAdapter = new DclfContingencyDFrameAdapter();
		DataFrame dfCaRec = dfAdapter.adapt(results);
	  	   	
		System.out.println("Number of rows in dfBus: " + dfCaRec.height());
    }

}
