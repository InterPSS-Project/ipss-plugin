 /*
  * @(#)Ieee14_CASample.java   
  *
  * Copyright (C) 2006-2015 www.interpss.org
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
  * @Date 03/15/2015
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.dclf_ca;

import java.util.ArrayList;
import java.util.List;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageContingency;
import com.interpss.core.aclf.contingency.dclf.CaBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class Ieee14_N1ScanSample {
	private static String filename = "testData/ieee14.ieee";
	
	public static void main(String args[]) throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load(filename)
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
		//contList.parallelStream()
		contList.stream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						if (resultRec.calLoadingPercent() >= 100.0) {
							cnt.increment();
							System.out.println(resultRec.aclfBranch.getId() + 
									", contBranch:" + resultRec.contingency.getId() +
									" postContFlow: " + resultRec.getPostFlowMW() +
									" loading: " + resultRec.calLoadingPercent());
							
							AclfNetwork aclfNet = dclfAlgo.getAclfNet();
							
							/*
							 * GSF/PTDF are for capturing the impact of generation and load changes on the flow of a line; 
							 * To capture the impacts of a line outage on other lines, line outage distribution factor (LODF) is used; 
							 * 
							 * The GSF of generation bus i w.r.t line j when line k is outaged is GSF_ijk = GSF_ij + GSF_ik*LODF_kj.
							 */
							
							CaOutageBranch outagedBranch = contingency.getOutageBranch();
							AclfBranch monitoredBranch = resultRec.aclfBranch;
							
							// GFS of monitoredBranch with respect to the reference bus
							aclfNet.getBusList().stream()
								.filter(bus -> bus.isGenPV())
								.forEach(bus -> {
									double gfs = dclfAlgo.calGenShiftFactor(bus.getId(), monitoredBranch);    // w.r.p to the Ref Bus
									System.out.println("   GSF Gen@" + bus.getId() + 
												" on Branch " + resultRec.aclfBranch.getId() + ": " + gfs);
								});
							
							// PTDF of loads
							aclfNet.getBusList().stream()
							.filter(bus -> bus.getLoadP() > 0.0 && !bus.isGenPV())
							.forEach(bus -> {
								try {
									// PTDF of loads with respect to the reference bus
									double ptdf = dclfAlgo.pTransferDistFactor(bus.getId(), monitoredBranch);
									System.out.println("   PTDF Load@" + bus.getId() + 
											" wrt to RefBus on Branch " + resultRec.aclfBranch.getId() + ": " + ptdf);
									
									// PTDF of loads with respect to gen@Bus-2
									ptdf = dclfAlgo.pTransferDistFactor(bus.getId(), "Bus2", resultRec.aclfBranch);
									System.out.println("   PTDF Inject@" + bus.getId() + 
											" Withdraw@Bus-2 on Branch " + resultRec.aclfBranch.getId() + ": " + ptdf);
								} catch (InterpssException e) {
									e.printStackTrace();
								}    
							});
							
							// LODF of monitoredBranch with respect to the outagedBranch
							try {
								double lodf = dclfAlgo.lineOutageDFactor(outagedBranch, monitoredBranch);
								System.out.println("   LODF of Branch " + monitoredBranch.getId() + 
										" w.r.t outaged Branch " + outagedBranch.getId() + ": " + lodf);
							} catch (InterpssException e) {
								e.printStackTrace();
							}
						}
					});
		});
		
		System.out.println("Total number of branches with loading > 100%: " + cnt.getCount());
	}
}

