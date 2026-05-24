package org.interpss.plugin.optadj.genLoadOpt;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.IEEE14_SensHelper_Test;
import org.interpss.plugin.optadj.algo.AclfNetGenLoadOptimizer;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.result.BranchDclfResultRec;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_BasecaseSSAResult_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();
		

		// defined a SSA result container
		AclfNetSsaResultContainer ssaResults = new AclfNetSsaResultContainer(true);
		
		// check the branch loading
		double baseMVA = net.getBaseMva();
		AtomicCounter cnt = new AtomicCounter();
		dclfAlgo.getDclfAlgoBranchList().stream() 
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
				if (loading > 100.0) {
					cnt.increment();
					// add the over limit branch to the SSA result container
					ssaResults.getBaseOverLimitInfo().add(new BranchDclfResultRec(dclfBranch));
					System.out.println("Over Limit Branch: " + dclfBranch.getId() + "  " + flowMw +
							" rating: " + dclfBranch.getBranch().getRatingMva1() +
							" loading: " + loading);
//						container.getBaseOverLimitInfo().add(dclfBranch);
				}
			});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		assertTrue(cnt.getCount() == 1);
		
		// perform the Optimization adjustment
		AclfNetGenLoadOptimizer optimizer = new AclfNetGenLoadOptimizer(dclfAlgo);
		optimizer.optimize(ssaResults, 100, true);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println(resultMap);
		
		assertEquals(resultMap.get("Gen:Bus2-G1"), 0.5713, 0.0001);
		
		System.out.println("Optimization gen size." + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getOptimizer().getSecConstrainDataList().size());
		assertTrue(optimizer.getOptimizer().getGenSize() == 4);
		assertTrue(optimizer.getOptimizer().getGenConstrainDataList().size() == 8);
		assertTrue(optimizer.getOptimizer().getSecConstrainDataList().size() == 20);
		
		dclfAlgo.calculateDclf();
		
		// check the branch loading after the optimization adjustment
		AtomicCounter cnt1 = new AtomicCounter();
		Map<String, BranchDclfResultRec> baseOverLimitInfoMap = ssaResults.toBaseOverLimitInfoMap();
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
				if (loading > 90) {
					cnt1.increment();
					BranchDclfResultRec rec = baseOverLimitInfoMap.get(dclfBranch.getId());
					double originalFlowMw = rec.mwFlow;
					double originalLoading = rec.loadingPercent;
					System.out.println("Branch: " + dclfBranch.getId() + " flowMw(after): " + flowMw
							+ " flowMw(original): " + originalFlowMw
							+ " rating: " + dclfBranch.getBranch().getRatingMva1()
							+ " loading%(after): " + loading
							+ " loading%(original): " + originalLoading);
						
					}
				});
		System.out.println("Total number of branches over limit (90%) after OptAdj: " + cnt1.getCount());
		assertTrue(cnt1.getCount() == 1);
	}
}
