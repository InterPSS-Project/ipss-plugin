package org.interpss.optadj.texas2k.sparse;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.optadj.texas2k.Texas2K_Sample_Info;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfContingencyHelper;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.optadj.algo.lf.AclfNetContigencyOptimizer;
import com.interpss.optadj.algo.util.AclfNetSsaHelper;
import com.interpss.optadj.result.OptAdjResultContainer;
import com.interpss.optadj.result.SsaResultContainer;

public class Texas2K_OptN1Scan_SsaResult_Sparse_Sample1 {
    public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = Texas2K_Sample_Info.loadNetwork();	
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));

		// define a contingency list
		List<DclfBranchOutage> dclfContList = new ArrayList<>();
		aclfNet.getBranchList().stream()
			// make sure the branch is not connected to a reference bus.
			.filter(branch -> !((AclfBranch)branch).isConnect2RefBus())
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				DclfBranchOutage cont = DclfAlgoObjectFactory.createContingency("contBranch:" + branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				DclfOutageBranch outage = DclfAlgoObjectFactory.createCaOutageBranch(
						dclfAlgo.getDclfAlgoBranch(branch.getId()),
						ContingencyBranchOutageType.OPEN);
				cont.setOutageEquip(outage);
				dclfContList.add(cont);
			});
			
		double loadingThreshold = 100.0;
		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).contingencyScan(dclfContList, loadingThreshold);
		//ssaResult.printCaOverLimitInfo();
		System.out.println("Total number of branches over limit before OptAdj: " + ssaResult.getCaOverLimitInfo().size());
		
		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		new AclfNetContigencyOptimizer(true).optimize(dclfAlgo, optAdjResult, loadingThreshold);
		optAdjResult.getOptAdjResults().forEach((genName, result) -> {
			System.out.println("GenAdjustResult: " + genName + ", " + result.toString());
		});

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		SsaResultContainer ssaResultAfter = new AclfNetSsaHelper(dclfAlgo).contingencyScan(dclfContList, ssaResult.getCaOverLimitInfo());	
		System.out.println("Total number of branches over limit after OptAdj: " + ssaResultAfter.getCaOverLimitInfo().size());
		ssaResultAfter.printCaOverLimitInfo(ssaResult.getCaOverLimitInfo());
	}
}
