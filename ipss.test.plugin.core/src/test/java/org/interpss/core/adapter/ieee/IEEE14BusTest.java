 /*
  * @(#)Test_IEEECommonFormat_Comma.java   
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

package org.interpss.core.adapter.ieee;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.util.AclfNetJsonComparator;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.JacobianMatrixType;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.NrMethodConfig;

public class IEEE14BusTest extends CorePluginTestSetup {
	@Test 
	public void polarCoordinateTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14_comma.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.loadflow();
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
		
		assertTrue("", aclfNet.isLfConverged());
	}
	
	@Test 
	public void xyCoordinateTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14_comma.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001);
	  	
	  	NrMethodConfig nrMethodConfig = algo.getNrMethodConfig();
	  	//nrMethodConfig.setCoordinate(JacobianMatrixType.NR_XY_COORDINATE);
	  	// re-configure the Nr solver with the updated config
	  	algo.getLfCalculator().getNrSolver().reConfigSolver(nrMethodConfig);
	  	
		// at this point, the network is in polar coordinate
		// transfer voltage (mag, ang) to (Vx, Vy) when changing the coordinate	in the setPolarCoordinate() method.
	  	aclfNet.setPolarCoordinate(false);
		
	  	algo.loadflow();
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
		
		assertTrue("", aclfNet.isLfConverged());
	}
}

