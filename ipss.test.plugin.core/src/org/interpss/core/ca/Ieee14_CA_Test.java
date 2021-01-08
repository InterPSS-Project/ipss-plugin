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
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.algo.dclf.LODFSenAnalysisType;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
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
		AclfBranch outageBranch = net.getBranch("Bus5->Bus6(1)");
        double outBanchPreFlow = outageBranch.getDclfFlow();
        
        double sum = 0.0;  // Bus4->Bus7(1), Bus4->Bus9(1), Bus5->Bus6(1) interface diff before and after the outage
        for (AclfBranch branch : net.getBranchList()) {
       		double f = dclfAlgo.lineOutageDFactor(outageBranch, branch);
           	double postFlow = branch.getDclfFlow() + f * outBanchPreFlow;
           	
           	// check CA results 
       		if (branch.getId().equals("Bus4->Bus7(1)") ||
       				branch.getId().equals("Bus4->Bus9(1)"))
       			sum += postFlow - branch.getDclfFlow();
       		else if (branch.getId().equals("Bus5->Bus6(1)"))
       			sum -= branch.getDclfFlow();
		}
        assertTrue(Math.abs(sum) < 0.00001);
	}

	@Test
	public void multipleOutageTest() throws InterpssException, ReferenceBusException, IpssNumericException, OutageConnectivityException  {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		// run Dclf
		SenAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();

		// define outage branches
		dclfAlgo.getOutageBranchList().clear();
		dclfAlgo.getOutageBranchList().add(
					CoreObjectFactory.createOutageBranch(net.getBranch("Bus1", "Bus5", "1")));
		dclfAlgo.getOutageBranchList().add(
				CoreObjectFactory.createOutageBranch(net.getBranch("Bus3", "Bus4", "1")));
		dclfAlgo.getOutageBranchList().add(
				CoreObjectFactory.createOutageBranch(net.getBranch("Bus6", "Bus11", "1")));

		// define reference bus for the multi-outage calculation. Since Bus1 is connected to an outage branch, we
		// to choice a different ref bus.
		dclfAlgo.setRefBus("Bus14");
		
		// calculate multi-outage LODF and return inv[I-PTDF]
		Object invE_PTDF = dclfAlgo.calLineOutageDFactors("ContId");

        double baseMva = net.getBaseMva();
		for (AclfBranch branch : net.getBranchList()) {
        	double preFlow = branch.getDclfFlow()*baseMva,
        		   postFlow = 0.0;
        	// calculate the LODF factors for the monitoring branch
        	// LODF factors are arranged in the OutageBranchList sequence
        	double[] factors = dclfAlgo.getLineOutageDFactors(branch, invE_PTDF);
        	if (factors != null) {  // factors = null if branch is an outage branch
            	double sum = 0.0;
            	int cnt = 0;
        		for (OutageBranch outBranch : dclfAlgo.getOutageBranchList()) {
        			double flow = outBranch.getBranch().getDclfFlow();
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
       		if (branch.getId().equals("Bus1->Bus5(1)") ||
       				branch.getId().equals("Bus3->Bus4(1)")||
       				branch.getId().equals("Bus6->Bus11(1)"))
       			assertTrue(postFlow == 0.0);
       		else if (branch.getId().equals("Bus2->Bus5(1)"))
       			assertTrue(Math.abs(postFlow - 69.08805) < 0.00001);
       		else if (branch.getId().equals("Bus6->Bus16(1)"))
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

