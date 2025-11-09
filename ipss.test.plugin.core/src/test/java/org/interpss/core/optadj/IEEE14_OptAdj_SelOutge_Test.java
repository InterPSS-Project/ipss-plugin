package org.interpss.core.optadj;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.AclfNetContigencyOptimizer;
import org.junit.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.algo.dclf.CaBranchOutageType;
import com.interpss.core.algo.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_SelOutge_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
		
		/*
		Bus2->Bus5(1) Bus9->Bus14(1) Bus13->Bus14(1) Bus12->Bus13(1) Bus3->Bus4(1) Bus5->Bus6(1)
		Bus4->Bus7(1) Bus2->Bus4(1) Bus10->Bus11(1)Bus6->Bus11(1) Bus7->Bus9(1) 
		Bus4->Bus9(1) Bus7->Bus8(1) Bus6->Bus13(1) Bus6->Bus12(1) Bus2->Bus3(1) Bus4->Bus5(1) Bus9->Bus10(1)
		 */
		Set<String> outBranchIdSet = new HashSet<>(Arrays.asList(
				"Bus1->Bus2(1)", "Bus1->Bus5(1)", 
				"Bus2->Bus5(1)", "Bus9->Bus14(1)", "Bus13->Bus14(1)", "Bus12->Bus13(1)",
				"Bus3->Bus4(1)", "Bus5->Bus6(1)", "Bus4->Bus7(1)", "Bus2->Bus4(1)", "Bus10->Bus11(1)",
				"Bus6->Bus11(1)", "Bus7->Bus9(1)", "Bus4->Bus9(1)", "Bus7->Bus8(1)", "Bus6->Bus13(1)",
				"Bus6->Bus12(1)", "Bus2->Bus3(1)", 
				"Bus4->Bus5(1)", 
				"Bus9->Bus10(1)"));
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();

		// define a contingency list
		List<Contingency> contList = new ArrayList<>();
		net.getBranchList().stream()
			// make sure the branch is not connected to a reference bus.
			.filter(branch -> !((AclfBranch)branch).isConnect2RefBus() && outBranchIdSet.contains(branch.getId()))
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				Contingency cont = createContingency("contBranch:"+branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				CaOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), CaBranchOutageType.OPEN);
				cont.setOutageBranch(outage);
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
		optimizer.optimize(100, outBranchIdSet);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println(resultMap);
		
		assertEquals(resultMap.get("Bus3-G1"), 0.99, 0.0001);
		assertEquals(resultMap.get("Bus1-G1"), -0.99, 0.0001);
		
		System.out.println("Optimization gen size." + optimizer.getGenOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getGenOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getGenOptimizer().getSecConstrainDataList().size());
		assertTrue(optimizer.getGenOptimizer().getGenSize() == 5);
		assertTrue(optimizer.getGenOptimizer().getGenConstrainDataList().size() == 10);
		assertEquals(optimizer.getGenOptimizer().getSecConstrainDataList().size(), 101);
		
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


