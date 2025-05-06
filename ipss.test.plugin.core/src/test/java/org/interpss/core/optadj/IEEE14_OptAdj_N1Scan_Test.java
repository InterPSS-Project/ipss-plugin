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

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.funcImpl.AclfAdptFunction.branchRatingAptr;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.AclfBranchRating;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.algo.dclf.CaBranchOutageType;
import com.interpss.core.algo.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_N1Scan_Test extends CorePluginTestSetup {
	@Test
	public void seqTest() throws InterpssException {
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

		// define a contingency list
		List<Contingency> contList = new ArrayList<>();
		net.getBranchList().stream()
			// make sure the branch is not connected to a reference bus.
			.filter(branch -> !((AclfBranch)branch).isConnect2RefBus())
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				Contingency cont = createContingency("contBranch:"+branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				CaOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), CaBranchOutageType.OPEN);
				cont.setOutageBranch(outage);
				contList.add(cont);
			});
		
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						double loading = resultRec.calLoadingPercent();
						if (loading > 100.0) {
							System.out.println("Branch: " + resultRec.aclfBranch.getId() + 
									" outage: " + resultRec.contingency.getId() +
									" postFlow: " + resultRec.preFlowMW +
									" rating: " + resultRec.aclfBranch.getRatingMva1() +
									" loading: " + loading);
						}
					});
		});
		
		// TODO: add optimization adjustment code here
	}
}
