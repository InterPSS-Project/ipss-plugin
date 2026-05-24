package org.interpss.optadj.busOpt.ei;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import org.interpss.optadj.busOpt.AclfNetBusOptUtil;

public class OptAdjBusGenLoad_EInterconnect_Sample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = EInterconnect_Info_Sample.loadCase();
		
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		System.out.println("=== Base case overloads ===");
		AclfNetBusOptUtil.printOverloadSummary(dclfAlgo, EInterconnect_Info_Sample.OPT_THRESHOLD);
		
		AclfNetBusOptUtil.runBusOptimization(dclfAlgo, aclfNet, EInterconnect_Info_Sample.OPT_THRESHOLD, false, "Gen+Load");
	}
}
