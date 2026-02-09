 /*
  * @(#)Ieee14_CA_Test.java   
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

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.exp.IpssNumericException;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.dclf.CaBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.common.OutageConnectivityException;
import com.interpss.core.common.ReferenceBusException;

public class Ieee14_CA_Test extends CorePluginTestSetup {
	@Test
	public void singleOutageTest() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();

		// run Dclf
		SenAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();
		
		// set single outage branch
		DclfAlgoBranch dclfBranch1 = dclfAlgo.getDclfAlgoBranch("Bus5->Bus6(1)");
		CaOutageBranch outageBranch = DclfAlgoObjectFactory.createCaOutageBranch(dclfBranch1, CaBranchOutageType.OPEN);
        double outBanchPreFlow = outageBranch.getDclfFlow();
        
        double sum = 0.0;  // Bus4->Bus7(1), Bus4->Bus9(1), Bus5->Bus6(1) interface diff before and after the outage
        for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
       		double f = dclfAlgo.lineOutageDFactor(outageBranch, dclfBranch.getBranch());
           	double postFlow = dclfBranch.getDclfFlow() + f * outBanchPreFlow;
           	
           	// check CA results 
       		if (dclfBranch.getId().equals("Bus4->Bus7(1)") ||
       				dclfBranch.getId().equals("Bus4->Bus9(1)"))
       			sum += postFlow - dclfBranch.getDclfFlow();
       		else if (dclfBranch.getId().equals("Bus5->Bus6(1)"))
       			sum -= dclfBranch.getDclfFlow();
		}
        assertTrue(Math.abs(sum) < 0.00001);
	}

	@Test
	public void singleClosureTest() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		/*
		 *     Branch closure "Bus4->Bus5(1)", 
		 *            Before closure     0.0
		 *            After closure    -62.34
		 *     Monitoring branch "Bus->Bus11(1)"
		 *            Before closure    15.10
		 *            After closure      6.30
		 */
		
		net.getBranch("Bus4->Bus5(1)").setStatus(false);

		// run Dclf
		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();
		//System.out.println("Before closure");			
		//System.out.println(DclfResult.f(dclfAlgo, false));	
		
		CaOutageBranch closureBranch = DclfAlgoObjectFactory.createCaOutageBranch(
				dclfAlgo.getDclfAlgoBranch("Bus4->Bus5(1)"), CaBranchOutageType.CLOSE);
		
  		double closureFlow = dclfAlgo.calBranchClosureFlow(closureBranch);
		//System.out.println("Branch Flow After closure: " + f3);
		assertTrue(Math.abs(closureFlow + 0.623398) < 0.00001);
		
		AclfBranch monitorBranch = net.getBranch("Bus6->Bus11(1)");
   		double f = dclfAlgo.lineOutageDFactor(closureBranch, monitorBranch);
       	double postFlow = dclfAlgo.getDclfAlgoBranch("Bus6->Bus11(1)").getDclfFlow() + f * closureFlow;
		System.out.println("Branch Flow After closure: " + postFlow);
		assertTrue(Math.abs(postFlow - 0.0630) < 0.001);
 	}

	@Test
	public void multipleOutageTest() throws InterpssException, ReferenceBusException, IpssNumericException, OutageConnectivityException  {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		// run Dclf
		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();

		// define outage branches
		dclfAlgo.getOutageBranchList().clear();
		dclfAlgo.getOutageBranchList().add(
				DclfAlgoObjectFactory.createCaOutageBranch(
						dclfAlgo.getDclfAlgoBranch("Bus1->Bus5(1)"), CaBranchOutageType.OPEN));
		dclfAlgo.getOutageBranchList().add(
				DclfAlgoObjectFactory.createCaOutageBranch(
						dclfAlgo.getDclfAlgoBranch("Bus3->Bus4(1)"), CaBranchOutageType.OPEN));
		dclfAlgo.getOutageBranchList().add(
				DclfAlgoObjectFactory.createCaOutageBranch(
						dclfAlgo.getDclfAlgoBranch("Bus6->Bus11(1)"), CaBranchOutageType.OPEN));

		// define reference bus for the multi-outage calculation. Since Bus1 is connected to an outage branch, we
		// to choice a different ref bus.
		dclfAlgo.setRefBus("Bus14");
		
		// calculate multi-outage LODF and return inv[I-PTDF]
		Object invE_PTDF = dclfAlgo.calMultiOutageInvE_PTDF("ContId");

        double baseMva = net.getBaseMva();
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
        	double preFlow = dclfBranch.getDclfFlow()*baseMva,
        		   postFlow = 0.0;
        	// calculate the LODF factors for the monitoring branch
        	// LODF factors are arranged in the OutageBranchList sequence
        	double[] factors = dclfAlgo.calMultiOutageLODFs(dclfBranch.getBranch(), invE_PTDF);
        	if (factors != null) {  // factors = null if branch is an outage branch
            	double sum = 0.0;
            	int cnt = 0;
        		for (CaOutageBranch outBranch : dclfAlgo.getOutageBranchList()) {
        			double flow = outBranch.getDclfFlow();
        			sum += flow * factors[cnt++];
        		}
        		postFlow = sum*baseMva + preFlow;
        	}
      
/* check CA results against the number in the IEEE paper
Cont 1, Bus1->Bus5(1), 71.11943, 0.0000, 100.0000, 0.0000
Cont 1, Bus3->Bus4(1), -24.14976, 0.0000, 100.0000, 0.0000
Cont 1, Bus6->Bus11(1), 6.30476, 0.0000, 100.0000, 0.0000

Cont 1, Bus2->Bus5(1), 40.90397, 69.08805, 100.0000, 69.08805
Cont 1, Bus6->Bus13(1), 17.03369, 17.88058, 100.0000, 17.88058
 */
       		if (dclfBranch.getId().equals("Bus1->Bus5(1)") ||
       				dclfBranch.getId().equals("Bus3->Bus4(1)")||
       				dclfBranch.getId().equals("Bus6->Bus11(1)"))
       			assertTrue(postFlow == 0.0);
       		else if (dclfBranch.getId().equals("Bus2->Bus5(1)"))
       			assertTrue(Math.abs(postFlow - 69.08805) < 0.00001);
       		else if (dclfBranch.getId().equals("Bus6->Bus16(1)"))
       			assertTrue(Math.abs(postFlow - 17.03369) < 0.00001);

       		/*        	
        	System.out.println(
        			contId + ", " + 
        			branch.getId() + ", " +
        			Number2String.toStr(preFlow) + ", " +
        			Number2String.toStr(postFlow) + ", " +
        			Number2String.toStr(branch.getRatingMva1()) + ", " +
        			Number2String.toStr(branch.getRatingMva1() == 0? 0.0 : 100.0*Math.abs(postFlow)/branch.getRatingMva1())
        	);
*/        	
		}
	}
}

