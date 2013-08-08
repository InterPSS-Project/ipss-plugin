 /*
  * @(#)Test_IEEE14.java   
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

import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE14Test extends CorePluginTestSetup {
	@Test
	public void testCase1() throws Exception {
  		/*
  		 * Load the loadflow datafile into the application
  		 */
//		IpssFileAdapter adapter = PluginSpringFactory.getCustomFileAdapter("ipssdat");
//		SimuContext simuCtx = adapter.load("testData/ipssdata/ieee14.ipssdat");
		
		/*
		 * Check the loadflow network has 14 buses and 20 branches
		 */
//		AclfNetwork net = simuCtx.getAclfNet();
		
		AclfNetwork net = CorePluginObjFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
					.load("testData/ipssdata/ieee14.ipssdat")
					.getAclfNet();	
		
  		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 14 && net.getBranchList().size() == 20));

  		/*
  		 * Get the default loadflow algorithm and Run loadflow analysis. By default, it uses
  		 * NR method with convergence error tolerance 0.0001 pu
  		 */
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	/*
	  	 * Check if loadflow has converged
	  	 */
  		assertTrue(net.isLfConverged());
  		
  		/*
  		 * Bus (id="1") is a swing bus. Make sure the P and Q results are with the expected values
  		 */
  		AclfBus swingBus = (AclfBus)net.getBus("1");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32386)<0.0001);
  		assertTrue( Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16889)<0.0001);
	}
	
	//@Test
	public void testCasePQ() throws Exception {
  		/*
  		 * Load the loadflow datafile into the application
  		 */
//		IpssFileAdapter adapter = PluginSpringFactory.getCustomFileAdapter("ipssdat");
//		SimuContext simuCtx = adapter.load("testData/ipssdata/ieee14.ipssdat");
		
		/*
		 * Check the loadflow network has 14 buses and 20 branches
		 */
//		AclfNetwork net = simuCtx.getAclfNet();
		
		
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				.load("testData/ipssdata/ieee14.ipssdat")
				.getAclfNet();	
  		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 14 && net.getBranchList().size() == 20));

  		/*
  		 * Get the default loadflow algorithm and Run loadflow analysis. By default, it uses
  		 * NR method with convergence error tolerance 0.0001 pu
  		 */
  		//IpssLogger.getLogger().setLevel(Level.INFO);
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	/*
	  	 * Check if loadflow has converged
	  	 */
  		assertTrue(net.isLfConverged());
  		
  		/*
  		 * Bus (id="1") is a swing bus. Make sure the P and Q results are with the expected values
  		 */
  		AclfBus swingBus = (AclfBus)net.getBus("1");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32386)<0.0001);
  		assertTrue( Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16889)<0.0001);
	}

//	@Test
//	public void testCase2() throws Exception {
//  		/*
//  		 * Load the loadflow datafile into the application
//  		 */
//		IpssFileAdapter adapter = PluginSpringFactory.getCustomFileAdapter("ipssdat");
//		SimuContext simuCtx = adapter.load("testData/ipssdata/ieee14.ipssdat");
//		assertTrue(adapter.save("ieee14.ipssout", simuCtx));
//
//		simuCtx = adapter.load("ieee14.ipssout");
//	}
}

