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
import static com.interpss.core.funcImpl.AclfAdptFunction.branchRatingAptr;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.AclfBranchRating;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_Basecase_Test extends CorePluginTestSetup {
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

		double baseMVA = net.getBaseMva();
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				AclfBranchRating adapter = branchRatingAptr.apply(dclfBranch.getBranch());
				double loading = adapter.mvaLoadingPercent(net.getBaseKva());
				if (loading > 100.0) {
					System.out.println("Branch: " + dclfBranch.getId() + "  " + flowMw +
							" rating: " + dclfBranch.getBranch().getRatingMva1() +
							" loading: " + loading);
				}
			});
		
		// TODO: add optimization adjustment code here
	}
}
