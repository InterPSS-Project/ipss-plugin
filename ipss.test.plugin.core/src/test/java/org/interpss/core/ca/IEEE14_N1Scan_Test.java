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

package org.interpss.core.ca;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageContingency;
import com.interpss.core.aclf.contingency.dclf.CaBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_N1Scan_Test extends CorePluginTestSetup {
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
		
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						if (resultRec.aclfBranch.getId().equals("resultRec.aclfBranch.getId()") &&
								resultRec.contingency.getId().equals("contBranch:Bus4->Bus5(1)"))
							assertTrue("", NumericUtil.equals(resultRec.getPostFlowMW(), 166.08, 0.01));
						else if (resultRec.aclfBranch.getId().equals("resultRec.aclfBranch.getId()") &&
								resultRec.contingency.getId().equals("contBranch:Bus3->Bus4(1)"))
							assertTrue("", NumericUtil.equals(resultRec.getPostFlowMW(), 152.90, 0.01));
						
						if (resultRec.calLoadingPercent() >= 100.0) {
							cnt.increment();
							System.out.println(resultRec.aclfBranch.getId() + 
									", contBranch:" + resultRec.contingency.getId() +
									" postContFlow: " + resultRec.getPostFlowMW() +
									" loading: " + resultRec.calLoadingPercent());
						}
					});
		});
		
		assertTrue("Total number of branches with loading > 100%: " + cnt.getCount(), cnt.getCount() == 18);
	}
}
/*
 * Bus1->Bus2(1), contBranch:Bus4->Bus5(1) postContFlow: 166.07624704742386
   Bus1->Bus2(1), contBranch:Bus3->Bus4(1) postContFlow: 152.89470786853144
*/
