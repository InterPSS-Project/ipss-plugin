/*
 * @(#)DclfSampleTest.java   
 *
 * Copyright (C) 2006 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * @Author Mike Zhou
 * @Version 1.0
 * @Date 07/15/2007
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.plugin.optadj;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.AclfNetContigencyOptimizer;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.junit.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.CaBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.BranchOutageContingency;
import com.interpss.core.aclf.contingency.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_N1ScanSSAResult_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();

		// define a contingency list
		List<BranchOutageContingency> contList = new ArrayList<>();
		net.getBranchList().stream()
			// make sure the branch is not connected to a reference bus.
			.filter(branch -> !((AclfBranch)branch).isConnect2RefBus())
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				BranchOutageContingency cont = createContingency("contBranch:"+branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				CaOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), CaBranchOutageType.OPEN);
				cont.setOutageBranch(outage);
				contList.add(cont);
			});
		
		// defined a SSA result container
		AclfNetSsaResultContainer ssaResults = new AclfNetSsaResultContainer();
		
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
							// add the over limit branch CA result rec to the SSA result container
							ssaResults.getCaOverLimitInfo().add(resultRec);
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
		optimizer.optimize(ssaResults, 100);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println(resultMap);
		
		assertEquals(resultMap.get("Bus3-G1"), 0.99, 0.0001);
		
		System.out.println("Optimization gen size." + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getOptimizer().getSecConstrainDataList().size());

		assertEquals(optimizer.getOptimizer().getGenSize(), 4);
		assertTrue(optimizer.getOptimizer().getGenConstrainDataList().size() == 8);
		assertTrue(optimizer.getOptimizer().getSecConstrainDataList().size() == 101);
		
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
