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

package org.interpss.core.ca;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.interpss.CorePluginTestSetup;
import org.interpss.algo.ZeroZBranchProcesor;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.pssl.plugin.IpssAdapter;


public class IEEE14BusBreakerTest extends CorePluginTestSetup {
	@Test 
	public void processZeroZBranch() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee_odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getAclfNet();
		
	  	double smallBranchZ = 0.00001;
	  	net.accept(new ZeroZBranchProcesor(smallBranchZ, true));
	  	assertTrue(net.isZeroZBranchProcessed());
	  	
	  	// at this point, all buses and small-z branches should be visited
	  	for (Bus b : net.getBusList())
	  		assertTrue(b.isVisited());
	  	for (Branch b : net.getBranchList())
	  		if (((AclfBranch)b).getBranchCode() == AclfBranchCode.ZERO_IMPEDENCE)
	  			assertTrue(b.isVisited());
	}	
	
	@Test 
	public void findZeroZPath() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee_odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getAclfNet();
		
	  	net.setVisitedStatus(false);
	  	net.markSmallZBranch(0.00001, false);		
		
	  	List<Bus> list = net.getBus("Bus1").findZeroZPathBuses(false);
	  	//System.out.println(list);
	  	assertTrue(list.size() == 4);	
	  	
	  	list = net.getBus("Bus14").findZeroZPathBuses(false);
	  	//System.out.println(list);
	  	assertTrue(list.size() == 3);	   
	
	  	// there is a zero-z branch loop
	  	list = net.getBus("Bus7").findZeroZPathBuses(true);
	  	System.out.println(list);
	  	assertTrue(list.size() == 5);	

	  	list = net.getBus("Bus2").findZeroZPathBuses(false);
	  	//System.out.println(list);
	  	assertTrue(list.size() == 1);	
	}	
}
