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

package org.interpss.core.dclf;

import static com.interpss.core.algo.dclf.solver.IConnectBusProcessor.predicateConnectBus;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.dclf.EDclfAlgorithm;
import com.interpss.core.algo.dclf.solver.IConnectBusProcessor;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.datatype.Mismatch;
import com.interpss.core.funcImpl.AclfNetHelper;

public class IEEE39_EDclf_Test extends CorePluginTestSetup {
	@Test 
	public void edclfTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee039.DAT")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf();
		
		System.out.println("EDclf Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		
		IConnectBusProcessor processor = DclfAlgoObjectFactory.createConnectBusProcessor(aclfNet);
		Complex[] newConVolt = processor.calConnectBusVoltage();
		
		Counter mCnt = new Counter();
		aclfNet.getBusList().stream()
			.filter(bus -> bus.isActive())
			.forEach(bus -> {
				if (predicateConnectBus.test(bus)) {
					int i = mCnt.getCountThenIncrement();
					bus.setVoltage(newConVolt[i]);
				}
			});
		
		Mismatch mis = aclfNet.maxMismatch(AclfMethodType.NR, predicateConnectBus);
		System.out.println("ConnectBus VAdjustment Mismatch: " + mis);
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet, true));
		
		assertTrue("", mis.maxMis.abs() < 0.0001);
	}
	
	@Test 
	public void connectionBusTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee039.DAT")
				.getAclfNet();	
		
		//aclfNet.initContributeGenLoad();
		
		ISparseEqnComplex[] ySet = new AclfNetHelper(aclfNet)
				.formYMatrixSet(false, predicateConnectBus);
		
		assertTrue("", ySet[0].getDimension() == 12);
		assertTrue("", ySet[3].getDimension() == 27);
	}	
	
}

