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

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.common.PSSLException;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.OutageBranch;
import com.interpss.core.common.OutageConnectivityException;
import com.interpss.core.common.ReferenceBusException;

public class Ieee14_CASample {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		singleOutageSample();
		
		multipleOutageSample();
	}
	
	public static void singleOutageSample() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = Ieee14_CA_Utils.getSampleNet();
        double baseMva = net.getBaseMva();
        
		// run Dclf
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		// set single outage branch
		AclfBranch outBranch = net.getBranch("Bus5->Bus6(1)");
		algoDsl.outageBranch(outBranch);
		
        double outBanchPreFlow = outBranch.getDclfFlow()*baseMva;
        
        /*
         * This example checks if the pre-outage-flow on branch 5->6 has been 
         * moved to branch 4->7 and 4->9 
         */
        double sum = 0.0;
        for (AclfBranch branch : net.getBranchList()) {
        	double 	preFlow = branch.getDclfFlow()*baseMva,
        			LODF = algoDsl.monitorBranch(branch).lineOutageDFactor(),
           			postFlow = preFlow + LODF * outBanchPreFlow;

       		if (branch.getId().equals("Bus4->Bus7(1)") ||
       				branch.getId().equals("Bus4->Bus9(1)"))
       			sum += postFlow - preFlow;
       		else if (branch.getId().equals("Bus5->Bus6(1)"))
       			sum -= preFlow;
		}
        System.out.println("The sum should be equal to zero: " + sum);
	}

	public static void multipleOutageSample() throws InterpssException, ReferenceBusException, IpssNumericException, OutageConnectivityException  {
		AclfNetwork net = Ieee14_CA_Utils.getSampleNet();
		
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
		algoDsl.calLineOutageDFactors("ContId");

		for (AclfBranch branch : net.getBranchList()) {
        	double preFlow = branch.getDclfFlow()*baseMva,
        		   postFlow = 0.0;

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
      
       		/*
       		 *  This example checks post outage flow per IEEE paper "Direct Calculation of Line Outage Distribution Factors" 
       		 */
       		if (branch.getId().equals("Bus1->Bus5(1)") ||
       				branch.getId().equals("Bus3->Bus4(1)")||
       				branch.getId().equals("Bus6->Bus11(1)"))
       			System.out.println("Branch " + branch.getId() + " should be 0.0: " + postFlow);
       		else if (branch.getId().equals("Bus2->Bus5(1)"))
       			System.out.println("Branch " + branch.getId() + " should be 69.08805: " + postFlow);
       		else if (branch.getId().equals("Bus6->Bus13(1)"))
       			System.out.println("Branch " + branch.getId() + " should be 17.88058: " + postFlow);
		}
	}
}

