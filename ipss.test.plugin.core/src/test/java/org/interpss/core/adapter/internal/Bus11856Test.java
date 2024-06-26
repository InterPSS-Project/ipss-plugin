 /*
  * @(#)Test_Bus11856.java   
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

package org.interpss.core.adapter.internal;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.PerformanceTimer;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetHelper;
import com.interpss.state.aclf.AclfNetworkState;

public class Bus11856Test extends CorePluginTestSetup {
	@Test
	public void testDeepCopy() throws Exception {
		AclfNetwork net = CorePluginFactory
  					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
  					.load("testData/ipssdata/BUS11856.ipssdat")
  					.getAclfNet();	
		
		PerformanceTimer timer = new PerformanceTimer();
		net.hzCopy();
		timer.log("DeepCopy ");
		
		timer.start();
		for (int i = 0; i < 10; i++) {
			net.hzCopy();
		}
		timer.log("10 DeepCopy ");
		
		timer.start();
		net.jsonCopy();
		timer.log("JSonCopy ");

		timer.start();
		for (int i = 0; i < 10; i++) {
			net.jsonCopy();
		}
		timer.log("10 JSonCopy ");
	
		timer.start();
		AclfNetworkState bean = new AclfNetworkState(net);
		for (int i = 0; i < 10; i++) {
			AclfNetworkState.create(bean);
		}
		timer.log("10 JSonCopy 1 ");
	}
	
	@Test
	public void testLoadCase() throws Exception {
  		System.out.println("Start loading data ...");
  		
  		
//		IpssFileAdapter adapter = CorePluginObjFactory.getCustomFileAdapter("ipssdat");
//		
////  		for(int i = 0; i < 10; i++) {
//  			SimuContext simuCtx = adapter.load("testData/ipssdata/BUS11856.ipssdat");
//  	  		System.out.println("End loading data ...");
//  	  		System.out.println("time for loading data : " + (System.currentTimeMillis() - starttime)*0.001);
//  	        
//  			AclfNetwork net = simuCtx.getAclfNet();
  		PerformanceTimer timer = new PerformanceTimer();	
  		AclfNetwork net = CorePluginFactory
  					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
  					.load("testData/ipssdata/BUS11856.ipssdat")
  					.getAclfNet();	
  		timer.log("Load data ");
  		
  	  		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 11856));

  		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  		timer.start();
  		AclfNetHelper helper = new AclfNetHelper(net);
  		assertTrue(helper.checkSwingRefBus());
  		timer.log("time for swing bus check");
	  	
  		timer.start();
  		algo.setLfMethod(AclfMethodType.PQ);
  		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  		algo.loadflow();
  			//	System.out.println(net.net2String());
  		timer.log("time for loadflow calculation");
  			
  		assertTrue(net.isLfConverged());		
//  		}
  	}
	
	@Test
	public void testZiiCase() throws Exception {
        long starttime = System.currentTimeMillis() ;
//  		System.out.println("Start loading data ...");
//		IpssFileAdapter adapter = PluginSpringFactory.getCustomFileAdapter("ipssdat");
//		SimuContext simuCtx = adapter.load("testData/ipssdata/BUS11856.ipssdat");
//  		System.out.println("End loading data ...");
//  		System.out.println("time for loading data : " + (System.currentTimeMillis() - starttime)*0.001);
//  		
//		AclfNetwork net = simuCtx.getAclfNet();
		
		AclfNetwork net = CorePluginFactory
  					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
  					.load("testData/ipssdata/BUS11856.ipssdat")
  					.getAclfNet();	
		
		final ISparseEqnComplex eqn = net.formYMatrix();
		for (AclfBus bus : net.getBusList()) {
			if (bus.isSwing()) {
				int busNo = bus.getSortNumber();
				eqn.setA(new Complex(0.0, 1.0e10), busNo, busNo);		
			}
		}
		eqn.factorization(1.0e-20);
		
		AclfBus bus1 = net.getBus("9a");
		int busNo = bus1.getSortNumber();
		eqn.setB2Unity(busNo);
		
        starttime = System.currentTimeMillis() ;
		eqn.solveEqn();
		Complex z = eqn.getX(busNo);
		System.out.println("Zii: " + ComplexFunc.toString(z));    		
  		System.out.println("time for finding zii : " + (System.currentTimeMillis() - starttime)*0.001);
	}	
}

