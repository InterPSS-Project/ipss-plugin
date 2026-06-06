package org.interpss.optadj.ieee39;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

import java.util.Map;

import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetSsaHelper;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaResultContainer;

public class IEEE39_OptBasecase_SsaResult_Sample {

	public static void main(String args[]) throws Exception {
	    // Load network
	    AclfNetwork net = IEEE39_Sample_Data.createTestCaseNetwork();

		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();

		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).baseCaseScan(50.0);
		
		// perform basecase loaing limit optimization	
		Map<String, OptAdjResultContainer.GenAdjustResult> results = new AclfNetLoadFlowOptimizer().optimize(dclfAlgo, ssaResult, 100.0);
		results.forEach((genName, result) -> {
			System.out.println("GenAdjustResult: " + genName + ", " + result.toString());
		});
		
		// perform DCLF recalculation after optimization
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);	

		// check the branch loading after optimization
		double baseMVA = dclfAlgo.getNetwork().getBaseMva();
		dclfAlgo.getDclfAlgoBranchList().stream() 
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
				if (loading > 100.0) {
					System.out.printf("Over Limit Branch: %s  %.2f rating: %.2f loading: %.2f%n",
							dclfBranch.getId(),
							flowMw,
							dclfBranch.getBranch().getRatingMvaA(),
							loading);
					}
			});
	}
}
