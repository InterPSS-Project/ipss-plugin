package org.interpss.optadj.ei;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;

public class OptAdjGenOnly_EInterconnect_Sample {


	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = EInterconnect_Info_Sample.loadCase();
		ContingencyAnalysisAlgorithm dclfAlgo = EInterconnect_Info_Sample.createDclfAlgo(aclfNet);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		System.out.println("=== Base case overloads ===");
		EInterconnect_Info_Sample.printOverloadSummary(dclfAlgo, EInterconnect_Info_Sample.OPT_THRESHOLD);

		EInterconnect_Info_Sample.runBusOptimization(dclfAlgo, aclfNet, true, "Gen-only");
	}
}
