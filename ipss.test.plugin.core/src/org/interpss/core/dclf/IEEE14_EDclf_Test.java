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

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.dclf.EDclfAlgorithm;
import com.interpss.core.algo.dclf.solver.IEDclfSolver;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class IEEE14_EDclf_Test extends CorePluginTestSetup {
	@Test 
	public void checkVoltageTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateDclf();
		edclfAlgo.transfer2AclfNet(true);
		
		IEDclfSolver solver = DclfAlgoObjectFactory.createEDclfSolver(edclfAlgo);
		
		Complex[] voltAry = solver.calPQBudVoltMag();
		/*
               V_N
		  1.0167 + 0.0009i      (1.016377760628728, -2.7904232436892773E-4)
		  1.0185 + 0.0019i      (1.0180460023843958, 3.696227070719982E-4)
		  1.0609 + 0.0026i
		  1.0552 + 0.0064i
		  1.0504 + 0.0080i
		  1.0566 + 0.0055i
		  1.0549 + 0.0053i
		  1.0501 + 0.0074i
		  1.0345 + 0.0126i      (1.0345801248346052, 0.012910562373645573)
		*/
		assertTrue("", NumericUtil.equals(voltAry[0].getReal(), 1.01638, 0.0001));
		assertTrue("", NumericUtil.equals(voltAry[1].getReal(), 1.01805, 0.0001));
		assertTrue("", NumericUtil.equals(voltAry[8].getReal(), 1.03458, 0.0001));		
	}
	
	@Test 
	public void edclfTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		EDclfAlgorithm edclfAlgo = DclfAlgoObjectFactory.createEDclfAlgorithm(aclfNet, CacheType.SenNotCached);
		edclfAlgo.calculateEDclf();
		/*
                V_N
		  1.0167 + 0.0009i      (1.016377760628728, -2.7904232436892773E-4)    Bus4
		  1.0185 + 0.0019i      (1.0180460023843958, 3.696227070719982E-4)     Bus5
		  1.0345 + 0.0126i      (1.0345801248346052, 0.012910562373645573)      Bus14
		*/
		assertTrue("", NumericUtil.equals(aclfNet.getBus("Bus4").getVoltageMag(), 1.01638, 0.0001));
		assertTrue("", NumericUtil.equals(aclfNet.getBus("Bus5").getVoltageMag(), 1.01805, 0.0001));
		assertTrue("", NumericUtil.equals(aclfNet.getBus("Bus14").getVoltageMag(), 1.03458, 0.0001));	
		
		System.out.println("Mismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
	}
	
}

