package org.interpss.optadj.ei;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;

public class OptAdjGenLoad_EInterconnect_Sample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = EInterconnect_Info_Sample.loadCase();
		ContingencyAnalysisAlgorithm dclfAlgo = EInterconnect_Info_Sample.createDclfAlgo(aclfNet);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		System.out.println("=== Base case overloads ===");
		EInterconnect_Info_Sample.printOverloadSummary(dclfAlgo, 100.0);
		
		EInterconnect_Info_Sample.runBusOptimization(dclfAlgo, aclfNet, false, "Gen+Load");
	}
}
