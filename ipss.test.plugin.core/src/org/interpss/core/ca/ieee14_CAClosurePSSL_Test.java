 /*
  * @(#)AclfSampleTest.java   
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

import static org.interpss.CorePluginFunction.DclfResult;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.algo.dclf.LODFSenAnalysisType;
import com.interpss.core.common.OutageConnectivityException;
import com.interpss.core.common.ReferenceBusException;

public class ieee14_CAClosurePSSL_Test extends CorePluginTestSetup {
	@Test 
	public void lodfTest_BaseCase()  throws ReferenceBusException, OutageConnectivityException, InterpssException, IpssNumericException   {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net);
		algoDsl.runDclfAnalysis();
		//System.out.println("Base Case");			
		//System.out.println(DclfResult.f(algoDsl.algo(), false).toString());		
		double pBeforeOutage = net.getBranch("Bus4->Bus5(1)").getDclfFlow();

		algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH)
				.addOutageBranch("Bus2", "Bus5", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus3", "Bus4", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus6", "Bus11", "1", BranchOutageType.OPEN);

		algoDsl.calLineOutageDFactors("ContId");
		
		double[] factors = algoDsl.monitorBranch("Bus4", "Bus5", "1")
				  .getLineOutageDFactors();
		double pShifted = calShiftedPower(algoDsl, factors);
		//System.out.println("Branch Bus4->Bus5");
		//printResult(algoDsl, factors);
		
		//algoDsl.destroy();
		
		net.getBranch("Bus2->Bus5(1)").setStatus(false);
		net.getBranch("Bus3->Bus4(1)").setStatus(false);
		net.getBranch("Bus6->Bus11(1)").setStatus(false);
		
		algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();
		double pAfterOutage = net.getBranch("Bus4->Bus5(1)").getDclfFlow();
		//System.out.println("Open three branches");			
		//System.out.println(DclfResult.f(algoDsl.algo(), false).toString());	
		
/*
Base         Bus4->Bus5(1)       -62.34        
Outage       Bus4->Bus5(1)       -35.21        
Shifted power flow:               27.13 

      P(outage) = P(base) + P(shifted) 
 */
		assertTrue(Math.abs(pAfterOutage - (pBeforeOutage + pShifted)) < 0.00001);
		//algoDsl.destroy();
	}
	
	//@Test 
	public void lodfTest_BranchClosure1()  throws ReferenceBusException, OutageConnectivityException, InterpssException, IpssNumericException   {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		net.getBranch("Bus6->Bus11(1)").setStatus(false);

		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net);
		algoDsl.runDclfAnalysis();
		//System.out.println("Base Case");			
		//System.out.println(DclfResult.f(algoDsl.algo(), false).toString());
		double pBeforeOutage = net.getBranch("Bus4->Bus5(1)").getDclfFlow();
		
		algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH)
				.addOutageBranch("Bus2", "Bus5", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus3", "Bus4", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus6", "Bus11", "1", BranchOutageType.CLOSE);
		
		algoDsl.calLineOutageDFactors("ContId");
		
		double[] factors = algoDsl.monitorBranch("Bus4", "Bus5", "1")
				  				.getLineOutageDFactors();
		double pShifted = calShiftedPower(algoDsl, factors);
		//printResult(algoDsl, factors);
		assertTrue(Math.abs((pBeforeOutage + pShifted) - (-0.322023)) < 0.00001);
		
		//algoDsl.destroy();
		
		net.getBranch("Bus2->Bus5(1)").setStatus(false);
		net.getBranch("Bus3->Bus4(1)").setStatus(false);
		net.getBranch("Bus6->Bus11(1)").setStatus(true);
		
		algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis(true);
		double pAfterOutage = net.getBranch("Bus4->Bus5(1)").getDclfFlow();
		//System.out.println("Open three branches");
		//System.out.println(DclfResult.f(algoDsl.algo(), false).toString());	
/*		
    Before    Bus4->Bus5(1)       -65.50         
    After     Bus4->Bus5(1)       -32.34         
Shifted power flow:          31.59 
*/
		System.out.println("(pBeforeOutag+pShifted), pAfterOutage: " + (pBeforeOutage+pShifted) + ", " + pAfterOutage);
		//assertTrue(Math.abs(pAfterOutage - (pBeforeOutage + pShifted)) < 0.00001);
	}
	
	//@Test 
	public void lodfTest_BranchClosure2()  throws ReferenceBusException, OutageConnectivityException, InterpssException, IpssNumericException   {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		net.getBranch("Bus3->Bus4(1)").setStatus(false);
		net.getBranch("Bus6->Bus11(1)").setStatus(false);
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net);
		algoDsl.runDclfAnalysis();
		//System.out.println("Base Case");			
		//System.out.println(DclfResult.f(algoDsl.algo(), false).toString());		
		double pBeforeOutage = net.getBranch("Bus4->Bus5(1)").getDclfFlow();
		
		algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH)
				.addOutageBranch("Bus2", "Bus5", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus3", "Bus4", "1", BranchOutageType.CLOSE)
				.addOutageBranch("Bus6", "Bus11", "1", BranchOutageType.CLOSE);

		algoDsl.calLineOutageDFactors("ContId");
		
		double[] factors = algoDsl.monitorBranch("Bus4", "Bus5", "1")
				  .getLineOutageDFactors();

		printResult(algoDsl, factors);
		
		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());			
		
		//algoDsl.destroy();
		
		net.getBranch("Bus2->Bus5(1)").setStatus(false);
		net.getBranch("Bus3->Bus4(1)").setStatus(true);
		net.getBranch("Bus6->Bus11(1)").setStatus(true);
		
		algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());	
		
		//algoDsl.destroy();
	}
	
	//@Test 
	public void lodfTest_BranchClosure4()  throws ReferenceBusException, OutageConnectivityException, InterpssException, IpssNumericException   {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		net.getBranch("Bus2->Bus5(1)").setStatus(false);
		net.getBranch("Bus3->Bus4(1)").setStatus(false);
		net.getBranch("Bus6->Bus11(1)").setStatus(false);
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net);
		algoDsl.runDclfAnalysis();
		//System.out.println("Base Case");			
		//System.out.println(DclfResult.f(algoDsl.algo(), false).toString());		
		double pBeforeOutage = net.getBranch("Bus4->Bus5(1)").getDclfFlow();		
		
		algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH)
				.addOutageBranch("Bus2", "Bus5", "1", BranchOutageType.CLOSE)
				.addOutageBranch("Bus3", "Bus4", "1", BranchOutageType.CLOSE)
				.addOutageBranch("Bus6", "Bus11", "1", BranchOutageType.CLOSE);

		algoDsl.calLineOutageDFactors("ContId");
		
		double[] factors = algoDsl.monitorBranch("Bus4", "Bus5", "1")
				  .getLineOutageDFactors();

		printResult(algoDsl, factors);
		
		//algoDsl.destroy();
		
		net.getBranch("Bus2->Bus5(1)").setStatus(true);
		net.getBranch("Bus3->Bus4(1)").setStatus(true);
		net.getBranch("Bus6->Bus11(1)").setStatus(true);
		
		algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());	
		
		//algoDsl.destroy();
	}

	private double calShiftedPower(DclfAlgorithmDSL algoDsl, double[] factors) throws InterpssException, ReferenceBusException, IpssNumericException {
		double sum = 0.0;
		int cnt = 0;
		for (OutageBranch bra : algoDsl.outageBranchList()) {
			AclfBranch aclfBra = bra.getBranch();
			double flow = 0.0;
			if (bra.getOutageType() == BranchOutageType.CLOSE) {
				//flow = algoDsl.algo().getBranchClosureEquivPreFlow(aclfBra);
				OutageBranch outBranch = DclfAlgoObjectFactory.createOutageBranch(aclfBra, BranchOutageType.CLOSE);
				flow = algoDsl.algo().calBranchClosureFlow(outBranch);
			}
			else
				flow = aclfBra.getDclfFlow();
			sum += flow * factors[cnt++];
		}
		return sum;
	}

	private void printResult(DclfAlgorithmDSL algoDsl, double[] factors) throws InterpssException, ReferenceBusException, IpssNumericException {
		System.out.println("Shifted power flow: " + calShiftedPower(algoDsl, factors));
	}
}

