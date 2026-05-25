package org.interpss.optadj.localOpt.ei;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import org.interpss.optadj.localOpt.AclfNetBusOptUtil;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;

public class OptAdjBusGenLoad_EInterconnect_SsaResultSample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = EInterconnect_Info_Sample.loadCase();
		
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		// defined a SSA result container
		AclfNetSsaResultContainer ssaResults = new AclfNetSsaResultContainer(true);

		ssaResults.setBasecaseThreshold(EInterconnect_Info_Sample.OPT_THRESHOLD);

		System.out.println("=== Base case overloads ===");
		AclfNetBusOptUtil.printOverloadSummary(dclfAlgo, EInterconnect_Info_Sample.OPT_THRESHOLD, ssaResults);
		
		AclfNetBusOptUtil.runBaseCaseBusOptimization(dclfAlgo, aclfNet, EInterconnect_Info_Sample.OPT_THRESHOLD, false, "Gen+Load", ssaResults);

		System.out.println(ssaResults.toString());
	}
}
