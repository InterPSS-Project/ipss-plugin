package org.interpss.optadj.ieee39.dense;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.optadj.ieee39.IEEE39_Sample_Data;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

import org.interpss.plugin.optadj.algo.lf.AclfNetContigencyOptimizer;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;

public class IEEE39_OptN1Scan_Sample {

	public static void main(String args[]) throws Exception {
	    // Load network
	    AclfNetwork net = IEEE39_Sample_Data.createTestCaseNetwork();

		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();
				
		// define a contingency list
		List<DclfBranchOutage> contList = new ArrayList<>();
		net.getBranchList().stream()
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
				contList.add(cont);
			});
	
		AtomicCounter cnt = new AtomicCounter();
		// perform N-1 outage scan
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW() +
						//		" loading: " + resultRec.calLoadingPercent() + "%");
						double loading = resultRec.calLoadingPercent();
						if (loading > 100.0) {
							cnt.increment();
							// add the over limit branch CA result rec to the SSA result container
							System.out.println(String.format("OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f",
									resultRec.aclfBranch.getId(), resultRec.contingency.getId(),
									resultRec.getPostFlowMW(), resultRec.aclfBranch.getRatingMvaB(), loading));
						}
					});
			});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		
		Map<String, OptAdjResultContainer.GenAdjustResult> results = new AclfNetContigencyOptimizer().optimize(dclfAlgo, null, 100.0);
		results.forEach((genName, result) -> {
			System.out.println("GenAdjustResult: " + genName + ", " + result.toString());
		});

		dclfAlgo.calculateDclf();
	
		AtomicCounter cntAfter = new AtomicCounter();
		// perform N-1 outage scan
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW() +
						//		" loading: " + resultRec.calLoadingPercent() + "%");
						double loading = resultRec.calLoadingPercent();
						if (loading > 100.0) {
							cntAfter.increment();
							// add the over limit branch CA result rec to the SSA result container
							System.out.println(String.format("OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f",
									resultRec.aclfBranch.getId(), resultRec.contingency.getId(),
									resultRec.getPostFlowMW(), resultRec.aclfBranch.getRatingMvaB(), loading));
						}
					});
			});
		System.out.println("Total number of branches over limit after OptAdj: " + cntAfter.getCount());
	}
}
