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

package com.interpss.pssl.test.dclf;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.interpss.mapper.odm.impl.aclf.AclfScenarioHelper;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.MonitoringBranch;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.algo.dclf.LODFSenAnalysisType;
import com.interpss.core.common.OutageConnectivityException;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.pssl.test.BaseTestSetup;

public class Ieee14_CA_Test extends BaseTestSetup {
	@Test
	public void singleOutageTest() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		// set branch mva rating, since it is not defined in the input file
		for (AclfBranch branch : net.getBranchList()) {
			branch.setRatingMva1(100.0);
		}
		
		// run Dclf
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		// set single outage branch
		AclfBranch outBranch = net.getBranch("Bus5->Bus6(1)");
		try {
			algoDsl.outageBranch(outBranch);
		} catch (PSSLException e) {
			IpssLogger.ipssLogger.severe(e.toString());
		}
		
        //System.out.println("Contingency, Monitoring Branch, Pre Contingency Flow (MW), Post Contingency Flow (MW), Limit (MW), Loading (%)");	
        //String contId = "Cont 1";
        double baseMva = net.getBaseMva();
        
        double outBanchPreFlow = outBranch.getDclfFlow()*baseMva;
        
        double sum = 0.0;
        for (AclfBranch branch : net.getBranchList()) {
        	double preFlow = branch.getDclfFlow()*baseMva,
        		   postFlow = 0.0;
       		try {
       			double f = algoDsl.monitorBranch(branch).lineOutageDFactor();
           		postFlow = preFlow + f * outBanchPreFlow;
           	} catch (ReferenceBusException e) {
       			IpssLogger.ipssLogger.severe(e.toString());
       		}

/*
Cont 1, Bus4->Bus7(1), 28.98508, 55.72538, 100.0000, 55.72538
Cont 1, Bus4->Bus9(1), 16.63132, 31.97462, 100.0000, 31.97462

Cont 1, Bus5->Bus6(1), 42.0836, 0.0000, 100.0000, 0.0000

55.72538-28.98508 + 31.97462-16.63132 = 42.0836
 */
       		if (branch.getId().equals("Bus4->Bus7(1)") ||
       				branch.getId().equals("Bus4->Bus9(1)"))
       			sum += postFlow - preFlow;
       		else if (branch.getId().equals("Bus5->Bus6(1)"))
       			sum -= preFlow;
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
        assertTrue(Math.abs(sum) < 0.00001);
	}

	@Test
	public void multipleOutageTest() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		// set branch mva rating, since it is not defined in the input file.
		for (AclfBranch branch : net.getBranchList()) {
			branch.setRatingMva1(100.0);
		}
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH)
				.addOutageBranch("Bus1", "Bus5", "1")
				.addOutageBranch("Bus3", "Bus4", "1")
				.addOutageBranch("Bus6", "Bus11", "1");
		algoDsl.setRefBus("Bus14");


        //System.out.println("Contingency, Monitoring Branch, Pre Contingency Flow (MW), Post Contingency Flow (MW), Limit (MW), Loading (%)");	
        //String contId = "Cont 1";
        double baseMva = net.getBaseMva();
        
		try {
			algoDsl.calLineOutageDFactors("ContId");
		} catch (IpssNumericException | OutageConnectivityException | ReferenceBusException e) {
			IpssLogger.ipssLogger.severe(e.toString());
		}

		for (AclfBranch branch : net.getBranchList()) {
        	double preFlow = branch.getDclfFlow()*baseMva,
        		   postFlow = 0.0;
        	try {
        		double[] factors = algoDsl.monitorBranch(branch)
  				  					  .getLineOutageDFactors();
        		if (factors != null) {  // factors = null if branch is an outage branch
            		double sum = 0.0;
            		int cnt = 0;
        			for (OutageBranch outBranch : algoDsl.outageBranchList()) {
        				double flow = outBranch.getBranch().getDclfFlow();
        				sum += flow * factors[cnt++];
        			}
        			postFlow = sum*baseMva + preFlow;
        		}
        	} catch (IpssNumericException | ReferenceBusException e) {
    			IpssLogger.ipssLogger.severe(e.toString());
        	}
      
/*
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

