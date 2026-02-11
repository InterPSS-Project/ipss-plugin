
package org.interpss.plugin.optadj;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.algo.AclfNetContigencyOptimizer;
import org.junit.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.ContingencyBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.DclfBranchOutage;
import com.interpss.core.aclf.contingency.dclf.DclfOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_N1Scan_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();

		// define a contingency list
		List<DclfBranchOutage> contList = new ArrayList<>();
		net.getBranchList().stream()
			// make sure the branch is not connected to a reference bus.
			.filter(branch -> !((AclfBranch)branch).isConnect2RefBus())
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				DclfBranchOutage cont = createContingency("contBranch:"+branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				DclfOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), ContingencyBranchOutageType.OPEN);
				cont.setOutageEquip(outage);
				contList.add(cont);
			});
		
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						double loading = resultRec.calLoadingPercent(resultRec.aclfBranch.getRatingMva2());
						if (loading > 100.0) {
							cnt.increment();
							System.out.println("OverLimit Branch: " + resultRec.aclfBranch.getId() + " outage: "
											+ resultRec.contingency.getId() + " postFlow: " + resultRec.getPostFlowMW()
											+ " rating: " + resultRec.aclfBranch.getRatingMva2() + " loading: "
											+ loading);
						}
					});
			});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		assertTrue(""+cnt.getCount(), cnt.getCount() == 18);
		 
		AclfNetContigencyOptimizer optimizer = new AclfNetContigencyOptimizer(dclfAlgo);
		optimizer.optimize(100);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println(resultMap);
		
		assertEquals(resultMap.get("Bus3-G1"), 0.99, 0.0001);
		assertEquals(resultMap.get("Bus1-G1"), -0.99, 0.0001);
		
//		System.out.println("Optimization gen size." + optimizer.getGenOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getOptimizer().getSecConstrainDataList().size());
//		assertTrue(optimizer.getGenOptimizer().getGenSize() == 5);
		assertTrue(optimizer.getOptimizer().getGenConstrainDataList().size() == 10);
		assertEquals(optimizer.getOptimizer().getSecConstrainDataList().size(), 101);
		
		dclfAlgo.calculateDclf();
		
		AtomicCounter cnt1 = new AtomicCounter();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						double loading = resultRec.calLoadingPercent(resultRec.aclfBranch.getRatingMva2());
						if (loading > 100.0) {
							cnt1.increment();
							System.out.println("Branch: " + resultRec.aclfBranch.getId() + 
									" outage: " + resultRec.contingency.getId() +
									" postFlow: " + resultRec.getPostFlowMW() +
									" rating: " + resultRec.aclfBranch.getRatingMva2() +
									" loading: " + resultRec.calLoadingPercent());
						}
					});
			});
		System.out.println("Total number of branches over limit after OptAdj: " + cnt1.getCount());
		assertTrue(cnt1.getCount() == 0);
	}
}
