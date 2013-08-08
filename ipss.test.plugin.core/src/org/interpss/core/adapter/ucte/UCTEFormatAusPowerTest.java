 /*
  * @(#)Test_IEEECommonFormat.java   
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.adapter.ucte;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;

public class UCTEFormatAusPowerTest extends CorePluginTestSetup { 
	@Test 
	public void testCaseAclfNet() throws Exception {
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.UCTE)
				.load("testData/ucte/MarioTest1_Simple.uct")
				.getAclfNet();
		
  		//System.out.println(net.net2String());

	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		AclfBus swingBus = net.getBus("B4____1");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.mW);
  		assertTrue(Math.abs(p.getReal()-6.326)<0.01);
  		assertTrue(Math.abs(p.getImaginary()+1289.429)<0.01);
	}
	
	@Test
	public void testCase1() throws Exception {
		IpssFileAdapter adapter = CorePluginObjFactory.getFileAdapter(IpssFileAdapter.FileFormat.UCTE);
		SimuContext simuCtx = adapter.load("testData/ucte/MarioTest1_Simple.uct");

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(simuCtx.getAclfNet());
	  	algo.loadflow();
  		//System.out.println(simuCtx.getAclfNet().net2String());
  		
  		//System.out.println(AclfOutFunc.lfResultsBusStyle(simuCtx.getAclfNet()));
	  	
  		AclfBus swingBus = simuCtx.getAclfNet().getBus("B4____1");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.mW);
  		assertTrue(Math.abs(p.getReal()-6.326)<0.01);
  		assertTrue(Math.abs(p.getImaginary()+1289.429)<0.01);
	}

	//@Test
	public void testCase2() throws Exception {
		IpssFileAdapter adapter = CorePluginObjFactory.getFileAdapter(IpssFileAdapter.FileFormat.UCTE);
		SimuContext simuCtx = adapter.load("testData/ucte/MarioTest2_Xfr.uct");

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(simuCtx.getAclfNet());
	  	algo.loadflow();
  		//System.out.println(simuCtx.getAclfNet().net2String());
  		//System.out.println(AclfOutFunc.lfResultsBusStyle(simuCtx.getAclfNet()));
	  	
  		AclfBus swingBus = simuCtx.getAclfNet().getBus("B4____1");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.mW);
  		assertTrue(Math.abs(p.getReal()-6.483)<0.01);
  		assertTrue(Math.abs(p.getImaginary()+1200.454)<0.01);
	}

	//@Test
	public void testCase3() throws Exception {
		IpssFileAdapter adapter = CorePluginObjFactory.getFileAdapter(IpssFileAdapter.FileFormat.UCTE);
		SimuContext simuCtx = adapter.load("testData/ucte/MarioTest3_XfrReg.uct");
  		//System.out.println(simuCtx.getAclfNet().net2String());

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(simuCtx.getAclfNet());
	  	algo.loadflow();
  		//System.out.println(simuCtx.getAclfNet().net2String());
  		//System.out.println(AclfOutFunc.lfResultsBusStyle(simuCtx.getAclfNet()));
	  	
  		AclfBus swingBus = simuCtx.getAclfNet().getBus("OB4___1");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.mW);
  		assertTrue(Math.abs(p.getReal()-13.981)<0.01);
  		assertTrue(Math.abs(p.getImaginary()+987.239)<0.01);
	}

	//@Test
	public void testCase4() throws Exception {
		IpssFileAdapter adapter = CorePluginObjFactory.getFileAdapter(IpssFileAdapter.FileFormat.UCTE);
		SimuContext simuCtx = adapter.load("testData/ucte/MarioTest4_PSXfr1.uct");
  		//System.out.println(simuCtx.getAclfNet().net2String());

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(simuCtx.getAclfNet());
	  	algo.loadflow();
  		//System.out.println(simuCtx.getAclfNet().net2String());
  		//System.out.println(AclfOutFunc.lfResultsBusStyle(simuCtx.getAclfNet()));
	  	
  		AclfBus swingBus = simuCtx.getAclfNet().getBus("OB4___1");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.mW);
  		assertTrue(Math.abs(p.getReal()-8.172)<0.01);
  		assertTrue(Math.abs(p.getImaginary()+1077.244)<0.01);
	}}

