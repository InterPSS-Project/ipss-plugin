package org.interpss.optadj.texas2k.sparse;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import org.interpss.optadj.texas2k.Texas2K_Sample_Info;
import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetSsaHelper;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaResultContainer;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoGen;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class Texas2K_OptBasecase_SsaResult_Sparse_Sample {
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
		new AclfNetLoadFlowOptimizer(true).optimize(dclfAlgo, optAdjResult, loadingThreshold);
		optAdjResult.getOptAdjResults().forEach((genName, result) -> {
			System.out.println("GenAdjustResult: " + genName + ", " + result.toString());
		});
				
		// perform DCLF recalculation after optimization
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);	
		// Here we have the dclf algo object with the optimized gen dispatch

		// check the branch loading after optimization
		/* 
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
			*/

		SsaResultContainer ssaResultAfter = new AclfNetSsaHelper(dclfAlgo).calBaseCaseLoading(ssaResult.getBaseOverLimitInfo());	
		ssaResultAfter.printBaseOverLimitInfo(ssaResult.getBaseOverLimitInfo());	

		String branchId = "Bus4044->Bus4119(1)";
		/*
			calcuate GSF for the branch
		*/
		AclfBranch branch = aclfNet.getBranch(branchId);
		double baseMVA = aclfNet.getBaseMva();
		for (AclfBus bus : aclfNet.getBusList()) {
			if (bus.isGenPV() || bus.isGenPQ()) {
				// we need the dclf algo object with the optimized gen dispatch to calculate the GSF
				// and the bus gen MW, max MW, min MW.
				double gsf = dclfAlgo.calGenShiftFactor(bus.getId(), branch);
				if (Math.abs(gsf) > 0.05) {
					double genMw = dclfAlgo.getDclfAlgoBus(bus.getId()).getGenList().stream().mapToDouble(DclfAlgoGen::getGenP).sum() * baseMVA;
					double genMaxMw = bus.getPGenLimit().getMax() * baseMVA;
					double genMinMw = bus.getPGenLimit().getMin() * baseMVA;
					System.out.println("GSF for " + bus.getId() + " on " + branchId + ": " + gsf + ", genP: " + genMw + ", genMaxP: " + genMaxMw + ", genMinP: " + genMinMw);
				}
			}
		}
	}
}
