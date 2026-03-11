package sample.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

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
import org.interpss.plugin.contingency.util.DclfContingencyHelper;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class Texas2k_CASample {

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
		
		List<DclfBranchOutage> dclfContList = new DclfContingencyHelper(algo)
						.createDclfContList(contingencies);

		//import monitored branches from JSON file
		File monFile = new File("testData/psse/v36/texas2k/2k_monitored_branches.json");
		List<MonitoredBranchRecord> monitoredBranches = ContingencyFileUtil.importMonitoredBranchRecordsFromJson(monFile);

		Set<String> monitoredBranchIds = monitoredBranches.stream()
										.map(record -> record.getBranchId()).collect(Collectors.toSet());

		// define the contingency analysis configuration
	    DclfContingencyConfig config =  new DclfContingencyConfig();
	    config.setDclfInclLoss(true);
		config.setOverloadThreshold(100); // in percentage	

		ConcurrentLinkedQueue<DclfContingencyResultRec> results = 
				ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(net, dclfContList, monitoredBranchIds, config, 8);	

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
    }

}
