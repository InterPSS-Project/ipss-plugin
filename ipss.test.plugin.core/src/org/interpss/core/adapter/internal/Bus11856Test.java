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
import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetHelper;

public class Bus11856Test extends CorePluginTestSetup {
	@Test
	public void testCase1() throws Exception {
        long starttime = System.currentTimeMillis() ;
  		System.out.println("Start loading data ...");
  		
  		
//		IpssFileAdapter adapter = CorePluginObjFactory.getCustomFileAdapter("ipssdat");
//		
////  		for(int i = 0; i < 10; i++) {
//  			SimuContext simuCtx = adapter.load("testData/ipssdata/BUS11856.ipssdat");
//  	  		System.out.println("End loading data ...");
//  	  		System.out.println("time for loading data : " + (System.currentTimeMillis() - starttime)*0.001);
//  	        
//  			AclfNetwork net = simuCtx.getAclfNet();
  			
  			AclfNetwork net = CorePluginObjFactory
  					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
  					.load("testData/ipssdata/BUS11856.ipssdat")
  					.getAclfNet();	
  			
  	  		//System.out.println(net.net2String());
  	  		assertTrue((net.getBusList().size() == 11856));

  		  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  	  		starttime = System.currentTimeMillis() ;
			AclfNetHelper helper = new AclfNetHelper(net);
  	  		assertTrue(helper.checkSwingBus());
  	  		System.out.println("time for swing bus check : " + (System.currentTimeMillis() - starttime)*0.001);
	  	
  			starttime = System.currentTimeMillis() ;
  			algo.setLfMethod(AclfMethod.PQ);
  		  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  			algo.loadflow();
  			//	System.out.println(net.net2String());
  			System.out.println("time for loadflow calculation : " + (System.currentTimeMillis() - starttime)*0.001);
  			
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
		
		AclfNetwork net = CorePluginObjFactory
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
		eqn.luMatrix(1.0e-20);
		
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

