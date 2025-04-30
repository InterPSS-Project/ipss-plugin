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

package org.interpss.core.zeroz.topo;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.AclfNetTopoChangeHelper;


public class IEEE14ZeroZBranchFuncTest extends CorePluginTestSetup {
	@Test 
	public void test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());

		/*
		net.getBusList().forEach(bus -> {
			if (bus.isConnect2ZeroZBranch()) {
				System.out.println("\nBus: " + bus.getId() + " is connected to a zeroZ branch");
				List<Bus> busList = bus.findZeroZPathBuses();
				System.out.println(busList.size() + " zeroZ path buses");
				for (Bus b : busList) {
					System.out.println("Bus: " + b.getId());
				}
			}
		});
		*/
		
	  	assertTrue("", net.getBus("Bus7").findZeroZPathBuses().size() == 5);
	  	assertTrue("", net.getBus("Bus71").findZeroZPathBuses().size() == 5);
	  	assertTrue("", net.getBus("Bus72").findZeroZPathBuses().size() == 5);
	  	assertTrue("", net.getBus("Bus73").findZeroZPathBuses().size() == 5);
	  	assertTrue("", net.getBus("Bus74").findZeroZPathBuses().size() == 5);
	  	
	  	assertTrue("", net.getBus("Bus7").findZeroZPathBuses().stream().filter(bus -> 
	  		bus.getId().equals("Bus7") || bus.getId().equals("Bus71") || bus.getId().equals("Bus72") ||
	  		bus.getId().equals("Bus73") || bus.getId().equals("Bus74")).count() == 5);
	  	
	  	assertTrue("", net.getBus("Bus71").findZeroZPathBuses().stream().filter(bus -> 
	  		bus.getId().equals("Bus7") || bus.getId().equals("Bus71") || bus.getId().equals("Bus72") ||
	  		bus.getId().equals("Bus73") || bus.getId().equals("Bus74")).count() == 5);
	  	
	  	assertTrue("", net.getBus("Bus72").findZeroZPathBuses().stream().filter(bus -> 
  			bus.getId().equals("Bus7") || bus.getId().equals("Bus71") || bus.getId().equals("Bus72") ||
  			bus.getId().equals("Bus73") || bus.getId().equals("Bus74")).count() == 5);
	  	
		AclfNetTopoChangeHelper helper = new AclfNetTopoChangeHelper(net);
	  	helper.zeroZBranchBusMerge("Bus1");
	  	helper.zeroZBranchBusMerge("Bus7");
	  	helper.zeroZBranchBusMerge("Bus14");
	  	
		net.getBusList().forEach(bus -> {
			assertTrue("Bus should be not connected any zero Z branch: "+bus.getId(), 
						bus.isConnect2ZeroZBranch() == false);
		});
		
		net.getBranchList().forEach(bra -> {
			AclfBranch aclfBra = (AclfBranch)bra;
			//System.out.println("Branch: " + aclfBra.getId() + ", " + aclfBra.isActive() + " is a zeroZ branch: " + aclfBra.isZeroZBranch());
			assertTrue("There should be no active zero Z branch "+aclfBra.getId(), 
						!(aclfBra.isActive() && aclfBra.isZeroZBranch()));
		});
    }	
}
