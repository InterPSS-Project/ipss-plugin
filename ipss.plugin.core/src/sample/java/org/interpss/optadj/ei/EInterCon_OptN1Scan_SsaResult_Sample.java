package org.interpss.optadj.ei;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfContingencyHelper;
import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetSsaHelper;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;

public class EInterCon_OptN1Scan_SsaResult_Sample {
    public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = EInterCon_Sample_Info.loadNetwork();
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		
		//import contingency definitions from CA file
		File contFile = new File("ipss.plugin.core/testData/psse/v33/OpenEI_filtered_contingencies.json");
		List<BranchContingencyRecord> contingencies = ContingencyFileUtil.importContingenciesFromJson(contFile);
				
		List<DclfBranchOutage> dclfContList = new DclfContingencyHelper(dclfAlgo)
								.createDclfContList(contingencies);
		
		//import monitored branches from JSON file
		File monFile = new File("ipss.plugin.core/testData/psse/v33/OpenEI_monitored_branches.json");
		List<MonitoredBranchRecord> monitoredBranches = ContingencyFileUtil.importMonitoredBranchRecordsFromJson(monFile);
		
		Set<String> monitoredBranchIds = monitoredBranches.stream()
												.map(record -> record.getBranchId()).collect(Collectors.toSet());
		double loadingThreshold = 100.0;
		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).contingencyScan(dclfContList, monitoredBranchIds, loadingThreshold);
		ssaResult.printCaOverLimitInfo();
		System.out.println("Total number of branches over limit before OptAdj: " + ssaResult.getCaOverLimitInfo().size());	}
}
