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

import java.util.ArrayList;
import java.util.List;

import org.interpss.CorePluginTestSetup;
import org.interpss.algo.ZeroZBranchProcesor;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.aclf.contingency.impl.IslandBusProcesor;
import com.interpss.pssl.plugin.IpssAdapter;


public class IEEE14BusBreaker_islandBus_Test extends CorePluginTestSetup {
	@Test 
	public void case1() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee_odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getAclfNet();
	  	//System.out.println(net.net2String());

		List<OutageBranch> list = new ArrayList<OutageBranch>();
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus4->Bus73(1)")));
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus4->Bus9(1)")));
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus5->Bus6(1)")));
		
		IslandBusProcesor proc = new IslandBusProcesor(net);
		proc.findIslandBus(list, net);
	  	System.out.println("Original network with islanding");
	  	System.out.println(proc.getIslandBusIdSet());
	  	//System.out.println(proc.getIslandSubnetInterface());
	  	assertTrue(proc.getIslandBusIdSet().size() == 15);
	  	//assertTrue(proc.getIslandSubnetInterface().size() == 3);

	  	list.clear();
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus4->Bus73(1)")));
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus5->Bus6(1)")));
		
		proc.findIslandBus(list, net);
	  	System.out.println("Original network without islanding");
	  	System.out.println(proc.getIslandBusIdSet());
	  //	System.out.println(proc.getIslandSubnetInterface());
	  	assertTrue(proc.getIslandBusIdSet().size() == 0);
	  	//assertTrue(proc.getIslandSubnetInterface().size() == 0);
	}	

	@Test 
	public void case_smallZ() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee_odm/ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getAclfNet();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(0.00001, true));
	  	assertTrue(net.isZeroZBranchProcessed());

	  	List<OutageBranch> list = new ArrayList<OutageBranch>();
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus4->Bus73(1)")));
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus4->Bus9(1)")));
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus5->Bus6(1)")));
		
		IslandBusProcesor proc = new IslandBusProcesor(net);
		proc.findIslandBus(list, net);
	  	System.out.println("Consolidated network with islanding");
	  	System.out.println(proc.getIslandBusIdSet());
	  	//System.out.println(proc.getIslandSubnetInterface());
	  	assertTrue(proc.getIslandBusIdSet().size() == 9);
	  	//assertTrue(proc.getIslandSubnetInterface().size() == 3);

	  	list.clear();
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus4->Bus73(1)")));
		list.add(CoreObjectFactory.createOutageBranch(net.getBranch("Bus4->Bus9(1)")));
		
		proc.findIslandBus(list, net);
	  	System.out.println("Consolidated network without islanding");
	  	System.out.println(proc.getIslandBusIdSet());
	  	//System.out.println(proc.getIslandSubnetInterface());
	  	assertTrue(proc.getIslandBusIdSet().size() == 0);
	  	//assertTrue(proc.getIslandSubnetInterface().size() == 0);
	}	
}
