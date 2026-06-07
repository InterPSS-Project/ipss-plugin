package org.interpss.optadj.texas2k;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetSsaHelper;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class Texas2K_OptBasecase_SsaResult_Sample {
    public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = Texas2K_Sample_Info.loadNetwork();
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		
		double loadingThreshold = 90.0;
		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).baseCaseScan(loadingThreshold);   
		ssaResult.printBaseOverLimitInfo();
		
		// perform basecase loaing limit optimization	
		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		new AclfNetLoadFlowOptimizer().optimize(dclfAlgo, optAdjResult, loadingThreshold);
		optAdjResult.getOptAdjResults().forEach((genName, result) -> {
			System.out.println("GenAdjustResult: " + genName + ", " + result.toString());
		});
				
		// perform DCLF recalculation after optimization
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);	

		// check the branch loading after optimization
		double baseMVA = dclfAlgo.getNetwork().getBaseMva();
		dclfAlgo.getDclfAlgoBranchList().stream() 
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMvaA())*100;
				if (loading >= optAdjResult.getOptAdjThreshold()) {
					System.out.printf("Over Limit Branch: %s  %.2f rating: %.2f loading: %.2f%n",
							dclfBranch.getId(),
							flowMw,
							dclfBranch.getBranch().getRatingMvaA(),
							loading);
				}
			});
	}
}
