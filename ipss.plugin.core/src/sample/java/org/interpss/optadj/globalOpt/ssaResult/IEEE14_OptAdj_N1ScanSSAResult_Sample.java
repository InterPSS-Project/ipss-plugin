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

package org.interpss.optadj.globalOpt.ssaResult;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.algo.AclfNetGlobalContingencyOptimizer;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.result.BranchOptAdjustCAResultRec;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class IEEE14_OptAdj_N1ScanSSAResult_Sample {

    public static void main(String args[]) throws Exception {
		AclfNetwork net = IEEE14_OptAdj_BasecaseSSAResult_Sample.createTestCase();
		
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
		
		// defined a SSA result container
		AclfNetSsaResultContainer ssaResults = new AclfNetSsaResultContainer(true);

		ssaResults.setContingencyThreshold(100.0);
		
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW() +
						//		" loading: " + resultRec.calLoadingPercent() + "%");
						double loading = resultRec.calLoadingPercent(resultRec.aclfBranch.getRatingMvaB());
						if (loading > ssaResults.getContingencyThreshold()) {
							cnt.increment();
							// add the over limit branch CA result rec to the SSA result container
							ssaResults.getCaOverLimitInfo().add(new BranchOptAdjustCAResultRec(resultRec));
							System.out.println(String.format("OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f",
									resultRec.aclfBranch.getId(), resultRec.contingency.getId(),
									resultRec.getPostFlowMW(), resultRec.aclfBranch.getRatingMvaB(), loading));
						}
					});
			});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		
		AclfNetGlobalContingencyOptimizer optimizer = new AclfNetGlobalContingencyOptimizer(dclfAlgo);
		optimizer.optimize(ssaResults, ssaResults.getContingencyThreshold(), true);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println(resultMap);

		ssaResults.setOptAdjCAOverLimitResultMap(resultMap);
		
		System.out.println("Optimization gen size." + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getOptimizer().getSecConstrainDataList().size());

		dclfAlgo.calculateDclf();
			
		Map<String, BranchCAResultRec> caOverLimitInfoMap = ssaResults.toCaOverLimitInfoMap();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						String recId = AclfNetSsaResultContainer.caOverLimitInfoMapId(resultRec);
						BranchCAResultRec rec = caOverLimitInfoMap.get(recId);
						if (rec != null) {
							BranchOptAdjustCAResultRec recAdj = (BranchOptAdjustCAResultRec) rec;
							//System.out.println(resultRec.aclfBranch.getId() + 
							//		", " + resultRec.contingency.getId() +
							//		" postContFlow: " + resultRec.getPostFlowMW() + 
							//	    " loading: " + Math.abs(loading) + "%");
							// update the adjusted flow and loading percent
							recAdj.adjustedPostFlowMW = resultRec.getPostFlowMW();
							recAdj.adjustedLoadingPercent = resultRec.calLoadingPercent();
							//System.out.println("Branch: " + resultRec.aclfBranch.getId() + 
							//			" outage: " + resultRec.contingency.getId() +
							//			" postFlow: " + resultRec.getPostFlowMW() +
							//			" rating: " + resultRec.aclfBranch.getRatingMvaB() +
							//			" loading: " + resultRec.calLoadingPercent());
						}
					});
			});

		System.out.println(ssaResults.toString());
	}
}
