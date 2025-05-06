 /*
  * @(#)SampleLoadflow.java   
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

package org.interpss.core.zeroz;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;


public class IEEE14ZeroZBranchAclfTest extends CorePluginTestSetup {
	@Test 
	public void checkData_test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());

		// by default, auto set the zeroZ branch to the threshold (1E-6) is turned on
		net.checkData(); 
		
		net.getBranchList().forEach(branch -> {
			if (branch.isZeroZBranch()) {
				//System.out.println("\nBranch: " + branch.getId() + " is a zeroZ branch");
				// for zeroZ branch, the Z value should be set to the threshold
				assertTrue("", NumericUtil.equals(branch.getZ(), new Complex(0.0, net.getZeroZBranchThreshold()), 1E-6));
			}
		});
    }	
	
	@Test 
	public void zeroZProcessing_test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());
		
		assertTrue(net.getBus("Bus1").isConnect2ZeroZBranch());
		assertTrue(net.getBus("Bus7").isConnect2ZeroZBranch());
		assertTrue(net.getBus("Bus14").isConnect2ZeroZBranch());

		/*
		 * process zeroZ branches by merging the zeroZ branches and connected buses to the retained bus
		 */
		AclfNetZeroZBranchHelper helper = new AclfNetZeroZBranchHelper(net);
		net.getBusList().forEach(bus -> {
			if (bus.isConnect2ZeroZBranch()) 
				helper.zeroZBranchBusMerge(bus.getId());
		});
	  	
		assertTrue(net.getBus("Bus1").isActive() && !net.getBus("Bus1").isConnect2ZeroZBranch());
		// please note that Bus71 is retained, instead of Bus7 
		assertTrue(net.getBus("Bus71").isActive() && !net.getBus("Bus71").isConnect2ZeroZBranch());
		assertTrue(net.getBus("Bus14").isActive() && !net.getBus("Bus14").isConnect2ZeroZBranch());
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	/*
	  	 * Check if loadflow has converged
	  	 */
  		assertTrue(net.isLfConverged());
  		
  		// See IEEE14Bus_odm_Test.java for the expected values
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		System.out.println(swing.getGenResults(UnitType.PU).getReal());
		System.out.println(swing.getGenResults(UnitType.PU).getImaginary());
 		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32393)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16549)<0.0001);
    }
}


	
