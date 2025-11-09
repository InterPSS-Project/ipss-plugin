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

package org.interpss.core.optadj;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.AclfNetLoadFlowOptimizer;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_Basecase_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();
		
		// check the branch loading
		double baseMVA = net.getBaseMva();
		AtomicCounter cnt = new AtomicCounter();
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
					double flowMw = dclfBranch.getDclfFlow() * baseMVA;
					double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
				if (loading > 100.0) {
					cnt.increment();
					System.out.println("Over Limit Branch: " + dclfBranch.getId() + "  " + flowMw +
							" rating: " + dclfBranch.getBranch().getRatingMva1() +
							" loading: " + loading);
					}
				});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		assertTrue(cnt.getCount() == 1);
		
		// perform the Optimization adjustment
		AclfNetLoadFlowOptimizer optimizer = new AclfNetLoadFlowOptimizer(dclfAlgo);
		optimizer.optimize(100);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println(resultMap);
		
		assertEquals(resultMap.get("Bus2-G1"), 0.5713, 0.0001);
		assertEquals(resultMap.get("Bus1-G1"), -0.5713, 0.0001);
		
		System.out.println("Optimization gen size." + optimizer.getGenOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getGenOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getGenOptimizer().getSecConstrainDataList().size());
		assertEquals(optimizer.getGenOptimizer().getGenSize(), 5);
		assertEquals(optimizer.getGenOptimizer().getGenConstrainDataList().size(), 10);
		assertEquals(optimizer.getGenOptimizer().getSecConstrainDataList().size(), 20);
		
		dclfAlgo.calculateDclf();
		
		// check the branch loading after the optimization adjustment
		AtomicCounter cnt1 = new AtomicCounter();
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
				if (loading > 90) {
					cnt1.increment();
					System.out.println("Branch: " + dclfBranch.getId() + "  " + flowMw +
							" rating: " + dclfBranch.getBranch().getRatingMva1() +
							" loading: " + loading);
						
					}
				});
		System.out.println("Total number of branches over limit (90%) after OptAdj: " + cnt1.getCount());
		assertTrue(cnt1.getCount() == 1);
	}
}
