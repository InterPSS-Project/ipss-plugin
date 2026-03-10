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
import org.dflib.csv.Csv;
import org.interpss.plugin.contingency.DclfContingencyConfig;
import org.interpss.plugin.contingency.ParallelDclfContingencyAnalyzer;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfContingencyHelper;
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

public class DclfContDFAdapter_EICon_Sample {
	//private static final String TEST_ROOT = "ipss.plugin.core/";
	private static final String TEST_ROOT = "";

    public static void main(String args[]) throws Exception {
        AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.psseVersion(PsseVersion.PSSE_33)
				.load()
				.getImportedObj();	
        
		// run Dclf
		ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
		algo.calculateDclf(DclfMethod.INC_LOSS);

		//import contingency definitions from CA file
		File contFile = new File("testData/psse/v33/OpenEI_filtered_contingencies.json");
		List<BranchContingencyRecord> contingencRecs = ContingencyFileUtil.importContingenciesFromJson(contFile);
		
		List<DclfBranchOutage> dclfContList = new DclfContingencyHelper(algo)
					.createDclfContList(contingencRecs);
		
		//import monitored branches from JSON file
		File monFile = new File("testData/psse/v33/OpenEI_monitored_branches.json");
		List<MonitoredBranchRecord> monitoredBranches = ContingencyFileUtil.importMonitoredBranchRecordsFromJson(monFile);

		Set<String> monitoredBranchIds = monitoredBranches.stream()
										.map(record -> record.getBranchId()).collect(Collectors.toSet());

		// define the contingency analysis configuration
	    DclfContingencyConfig config =  new DclfContingencyConfig();
	    config.setDclfInclLoss(true);
		config.setOverloadThreshold(120); // in percentage	

		ConcurrentLinkedQueue<BranchCAResultRec> results = 
				ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
								net, dclfContList, monitoredBranchIds, 
								config.getOverloadThreshold(), config.isDclfInclLoss(), 14);	

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
	  	   	
		System.out.println("Number of rows in dfCaRec: " + dfCaRec.height());
		
		// write the dfBus to a csv file
		Csv.saver().save(dfCaRec, TEST_ROOT + "output/Eastern_Interconnect_DF_contingency.csv");
		System.out.println("Save to csv file: output/Eastern_Interconnect_DF_contingency.csv");
    }

}
