package org.interpss.sample.dclf_ca;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.interpss.plugin.contingency.DclfContingencyConfig;
import org.interpss.plugin.contingency.ParallelDclfContingencyAnalyzer;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.result.DclfContingencyResultRec;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageContingency;
import com.interpss.core.aclf.contingency.dclf.CaBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;

/**
 * Acknowledgment:
 * The synthetic Eastern Interconnect electric grid test case used in this example is provided by Texas A&M University’s energy and power group researchers.
 * https://electricgrids.engr.tamu.edu/
 *  
 * For details of the synthetic grid, please refer to the following references:
 *  [1] A. B. Birchfield; T. Xu; K. M. Gegner; K. S. Shetye; T. J. Overbye, “Grid Structural Characteristics as Validation Criteria for Synthetic Networks,”  in IEEE Transactions on Power Systems, vol. 32, no. 4, pp. 3258-3265, July 2017.
	[2] A. B. Birchfield; K. M. Gegner; T. Xu; K. S. Shetye; T. J. Overbye, “Statistical Considerations in the Creation of Realistic Synthetic PowerGrids for Geomagnetic Disturbance Studies,” in IEEE Transactions on Power Systems, vol. 32, no. 2, pp. 1502-1510, March 2017.
	[3] K. M. Gegner; A. B. Birchfield; T. Xu; K. S. Shetye; T. J. Overbye, “A methodology for the creation of geographically realistic synthetic powerflow models,” 2016 IEEE Power and Energy Conference at Illinois (PECI), Urbana, IL, 2016, pp. 1-6.
 */

public class OpenEI100k_CASample {

    public static void main(String args[]) throws Exception {
        AclfNetwork net = IpssAdapter.importAclfNet("ipss-plugin/ipss.sample/testData/psse/openEI/Base_Eastern_Interconnect_515GW.RAW")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.psseVersion(PsseVersion.PSSE_33)
				.load()
				.getImportedObj();	
        
		// run Dclf
		ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
		algo.calculateDclf(DclfMethod.INC_LOSS);

		//import contingency definitions from CA file
		File contFile = new File("ipss-plugin/ipss.sample/testData/psse/openEI/OpenEI_filtered_contingencies.json");
		List<BranchContingencyRecord> contingencies = ContingencyFileUtil.importContingenciesFromJson(contFile);
		List<BranchOutageContingency> contList = new java.util.ArrayList<>();

		for (BranchContingencyRecord record : contingencies) {
			try {
				// Find the branch based on from_bus and to_bus
				String branchId = record.fromBus + "->" + record.toBus+"("+record.ckt+")";
				if (net.getBranch(branchId) != null) {
					BranchOutageContingency cont = createContingency(record.name);
					
					// Determine outage type based on action type
					CaBranchOutageType outageType;
					switch (record.actionType.toLowerCase()) {
						case "open":
							outageType = CaBranchOutageType.OPEN;
							break;
						case "close":
							outageType = CaBranchOutageType.CLOSE;
							break;
						default:
							outageType = CaBranchOutageType.OPEN; // Default to open
					}
					
					CaOutageBranch outage = createCaOutageBranch(algo.getDclfAlgoBranch(branchId), outageType);
					cont.setOutageBranch(outage);
					contList.add(cont);
				}
			} catch (Exception ex) {
				throw new Exception("Warning: Could not create contingency for " + record.name + ": " + ex.getMessage() + "\n");
			}
		}

		//import monitored branches from JSON file
		File monFile = new File("ipss-plugin/ipss.sample/testData/psse/openEI/OpenEI_monitored_branches.json");
		List<MonitoredBranchRecord> monitoredBranches = ContingencyFileUtil.importMonitoredBranchRecordsFromJson(monFile);

		Set<String> monitoredBranchIds = monitoredBranches.stream()
										.map(record -> record.getBranchId()).collect(Collectors.toSet());

		// define the contingency analysis configuration
	    DclfContingencyConfig config =  new DclfContingencyConfig();
	    config.setDclfInclLoss(true);
		config.setOverloadThreshold(100); // in percentage	

		ConcurrentLinkedQueue<DclfContingencyResultRec> results = 
				ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(net, contList, monitoredBranchIds, config, 4);	

		// print the results
		for (DclfContingencyResultRec rec : results) {
			//System.out.println(rec.toString());
			String branchId = rec.getBranchId();
			String contingencyName = rec.getContingencyName();
			Double postFlowMW = rec.getPostFlowMW();
			Double lineRatingMW = rec.getLineRatingMW();
			Double loadingPercent = rec.getLoadingPercent();
			System.out.println(String.format("{\n  \"branch_id\": \"%s\",\n  \"contingency_name\": \"%s\",\n  \"post_flow_mw\": %.2f,\n  \"line_rating_mw\": %.2f,\n  \"loading_percent\": %.2f\n}", 
                branchId, contingencyName, postFlowMW, lineRatingMW, loadingPercent));
		}

		//Run GSF analysis for the violated branches
		
		double gsfThreshold = 0.05; // only print GSF values above this threshold

        //Note: limit the number of GSF calculations for demo purpose
        int calculateNumber = 100; // number of overloaded branches to calculate GSFs for
		for	 (DclfContingencyResultRec resultRec : results) {
            if (calculateNumber-- <=0)
                break;
			AclfBranch	 monitoredBranch = (AclfBranch) net.getBranch(resultRec.getBranchId());
			net.getBusList().parallelStream()
				.filter(bus -> bus.isActive() && (bus.isGenPV() || bus.isGenPQ()))
				.forEach(bus -> {
					double gfs = algo.calGenShiftFactor(bus.getId(), monitoredBranch);    // w.r.t to the Ref Bus
					if (Math.abs(gfs) > gsfThreshold) {
						System.out.println("   GSF Gen@" + bus.getId() + 
								" on Branch " + resultRec.getBranchId() + ": " + gfs);
					}
				});
		}

    }

}
