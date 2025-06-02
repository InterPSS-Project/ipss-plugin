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
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.AclfNetLoadFlowOptimizer;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_BasecaseSSAResult_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		// set the branch rating.
		net.getBranchList().stream()
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				aclfBranch.setRatingMva1(120.0);
			});
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();
		

		// defined a SSA result container
		AclfNetSsaResultContainer ssaResults = new AclfNetSsaResultContainer();
		
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
					ssaResults.getBaseOverLimitInfo().add(dclfBranch);
					System.out.println("Over Limit Branch: " + dclfBranch.getId() + "  " + flowMw +
							" rating: " + dclfBranch.getBranch().getRatingMva1() +
							" loading: " + loading);
//						container.getBaseOverLimitInfo().add(dclfBranch);
					}
				});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		assertTrue(cnt.getCount() == 1);

		// set the generator Pgen limit
		net.createAclfGenNameLookupTable(false).forEach((k, gen) -> {
			System.out.println("Adj Gen: " + gen.getName());
			if (gen.getPGenLimit() == null) {
				gen.setPGenLimit(new LimitType(5, 0));
			}
		});
		
		// perform the Optimization adjustment
		AclfNetLoadFlowOptimizer optimizer = new AclfNetLoadFlowOptimizer(dclfAlgo);
		optimizer.optimize(ssaResults, 100);
		System.out.println("Optimization gen size." + optimizer.getGenOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getGenOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getGenOptimizer().getSecConstrainDataList().size());
		assertTrue(optimizer.getGenOptimizer().getGenSize() == 13);
		assertTrue(optimizer.getGenOptimizer().getGenConstrainDataList().size() == 26);
		assertTrue(optimizer.getGenOptimizer().getSecConstrainDataList().size() == 20);
		
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
