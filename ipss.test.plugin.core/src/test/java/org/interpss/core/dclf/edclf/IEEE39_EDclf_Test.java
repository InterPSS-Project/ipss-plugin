 /*
  * @(#)IEEE14_3WXfrTest.java   
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
  * @Date 05/15/2013
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.dclf.edclf;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.EDclfAlgorithm;
import static com.interpss.core.algo.dclf.solver.IConnectBusProcessor.predicateConnectBus;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.algo.impl.solver.YMatrixSolver;
import com.interpss.core.datatype.Mismatch;

public class IEEE39_EDclf_Test extends CorePluginTestSetup {
	@Test 
	public void edclfTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee039.DAT")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf(DclfMethod.STD);
		
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.busSummaryCommaDelimited(aclfNet, true));
		// "Bus31", 5.7286653
		System.out.println("Swing Bus P(5.7286653): " + edclfAlgo.getBusPower(edclfAlgo.getDclfAlgoBus("Bus31")));
	}

	@Test 
	public void edclfLossTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee039.DAT")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf();
		
		System.out.println("EDclf/Loss Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.busSummaryCommaDelimited(aclfNet, true));
		System.out.println("Swing Bus P(5.7286653): " + edclfAlgo.getBusPower(edclfAlgo.getDclfAlgoBus("Bus31")));
	}
	
	@Test 
	public void edclfVCorrectionTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee039.DAT")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf(DclfMethod.STD);
		
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		Mismatch mis = aclfNet.maxMismatch(AclfMethodType.NR, predicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		
		DclfAlgoObjectFactory.createConnectBusProcessor(aclfNet)
		                     .updateConnectBusVoltage();

		System.out.println("EDclf/VCorrection Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		mis = aclfNet.maxMismatch(AclfMethodType.NR, predicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		assertTrue("", mis.maxMis.abs() < 0.0001);
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
	}	
	
	@Test 
	public void connectionBusTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee039.DAT")
				.getAclfNet();	
		
		//aclfNet.initContributeGenLoad();
		
		ISparseEqnComplex[] ySet = new YMatrixSolver(aclfNet)
				.formYMatrixSet(false, predicateConnectBus);
		
		assertTrue("", ySet[0].getDimension() == 12);
		assertTrue("", ySet[3].getDimension() == 27);
	}	
	
}

