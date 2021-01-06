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

package com.interpss.pssl.test.dclf;

import static org.interpss.CorePluginFunction.DclfResult;

import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.algo.dclf.LODFSenAnalysisType;
import com.interpss.core.common.OutageConnectivityException;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.pssl.test.BaseTestSetup;

public class DclfLODFPaperClosure_Test extends BaseTestSetup {
	//@Test 
	public void lodfTest_BranchClosure3()  throws ReferenceBusException, OutageConnectivityException, InterpssException, IpssNumericException   {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	

		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
										.runDclfAnalysis();
		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());			

		algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH)
				.addOutageBranch("Bus2", "Bus5", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus3", "Bus4", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus6", "Bus11", "1", BranchOutageType.OPEN);

		algoDsl.calLineOutageDFactors("ContId");
		
		double[] factors = algoDsl.monitorBranch("Bus4", "Bus5", "1")
				  .getLineOutageDFactors();

		printResult(algoDsl, factors);
		
		//algoDsl.destroy();
		
		net.getBranch("Bus2->Bus5(1)").setStatus(false);
		net.getBranch("Bus3->Bus4(1)").setStatus(false);
		net.getBranch("Bus6->Bus11(1)").setStatus(false);
		
		algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());	
		
		//algoDsl.destroy();
	}

	@Test 
	public void lodfTest_BranchClosure1()  throws ReferenceBusException, OutageConnectivityException, InterpssException, IpssNumericException   {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
		
		net.getBranch("Bus6->Bus11(1)").setStatus(false);
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
										.runDclfAnalysis();

		algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH)
				.addOutageBranch("Bus2", "Bus5", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus3", "Bus4", "1", BranchOutageType.OPEN)
				.addOutageBranch("Bus6", "Bus11", "1", BranchOutageType.CLOSE);
		
		algoDsl.calLineOutageDFactors("ContId");
		
		double[] factors = algoDsl.monitorBranch("Bus4", "Bus5", "1")
				  .getLineOutageDFactors();

		printResult(algoDsl, factors);
		
		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());		
		
		for (AclfBus bus : net.getBusList()) {
			if (!bus.isRefBus()) {
				System.out.println("Bus: " + bus.getId() + ", sort number: " + bus.getSortNumber());
				double xAry[] = algoDsl.algo().getDclfSolver().getSenPAngle(bus.getId());
				int cnt = 0;
				for ( double x : xAry) {
					System.out.println("(" + (cnt++) + "," + x + ")");
				}
			}
		}

		//algoDsl.destroy();
		
		net.getBranch("Bus2->Bus5(1)").setStatus(false);
		net.getBranch("Bus3->Bus4(1)").setStatus(false);
		net.getBranch("Bus6->Bus11(1)").setStatus(true);
		
		algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());	
		
		//algoDsl.destroy();
	}
	
	//@Test 
	public void lodfTest_BranchClosure2()  throws ReferenceBusException, OutageConnectivityException, InterpssException, IpssNumericException   {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
		
		net.getBranch("Bus3->Bus4(1)").setStatus(false);
		net.getBranch("Bus6->Bus11(1)").setStatus(false);
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
										.runDclfAnalysis();

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
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
		
		net.getBranch("Bus2->Bus5(1)").setStatus(false);
		net.getBranch("Bus3->Bus4(1)").setStatus(false);
		net.getBranch("Bus6->Bus11(1)").setStatus(false);
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
										.runDclfAnalysis();
		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());			

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

	private void printResult(DclfAlgorithmDSL algoDsl, double[] factors) throws InterpssException {
		double sum = 0.0;
		int cnt = 0;
		for (OutageBranch bra : algoDsl.outageBranchList()) {
			AclfBranch aclfBra = bra.getBranch();
			double flow = 0.0;
			if (bra.getOutageType() == BranchOutageType.CLOSE)
				flow = algoDsl.algo().getBranchClosureEquivPreFlow(aclfBra);
			else
				flow = aclfBra.getDclfFlow();
			sum += flow * factors[cnt++];
		}
		System.out.println("Shifted power flow: " + sum);
	}
}

