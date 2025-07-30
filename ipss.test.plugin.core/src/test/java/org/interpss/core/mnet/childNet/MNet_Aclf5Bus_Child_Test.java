 /*
  * @(#)MNet_Aclf5Bus_Child_Test.java   
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

package org.interpss.core.mnet.childNet;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.dist.DistNetwork;

// DistNet
public class MNet_Aclf5Bus_Child_Test extends CorePluginTestSetup {
	/*
	 *  Please Note: the parent/child relationship has already been built in the 
	 *  case xml file. 
	 */
	
	@Test 
	public void aclfChildNetTest() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/mnet/Aclf_5Bus_AclfChildNet.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		//System.out.println(net.net2String());	
		
		assertTrue(net.getChildNetWrapperList().size() > 0);
		assertTrue(net.getChildNetWrapperList().size() == 1);
		assertTrue(net.getChildNetWrapper("AclfChileNet1") != null);
		assertTrue(net.getChildNetWrapper("AclfChileNet1").getChildNetInterfaces().size() == 2);
		
		ChildNetInterfaceBranch interBra = (ChildNetInterfaceBranch)net.getChildNetWrapper("AclfChileNet1").getInterface("Bus2-Bus1");
		assertTrue(interBra.getInterfaceBusSide() == BranchBusSide.TO_SIDE);
		assertTrue(interBra.getChildNetSide() == BranchBusSide.TO_SIDE);
		assertTrue(interBra.getInterfaceBusIdChildNet().equals("ChildBus-1"));
	}
	
	@Test 
	public void distChildNetTest() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/mnet/Aclf_5Bus_DistChildNet.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		//System.out.println(net.net2String());	
		
		assertTrue(net.getChildNetWrapperList().size() > 0);
		assertTrue(net.getChildNetWrapperList().size() == 1);
		assertTrue(net.getChildNetWrapper("DistChileNet1") != null);
		assertTrue(net.getChildNetWrapper("DistChileNet1").getChildNetInterfaces().size() == 2);
		
		ChildNetInterfaceBranch interBra = (ChildNetInterfaceBranch)net.getChildNetWrapper("DistChileNet1").getInterface("Bus2-Bus1");
		assertTrue(interBra.getInterfaceBusSide() == BranchBusSide.TO_SIDE);
		assertTrue(interBra.getChildNetSide() == BranchBusSide.TO_SIDE);
		assertTrue(interBra.getInterfaceBusIdChildNet().equals("DistBus-1"));		
	}	

	@Test 
	public void distDcSysChildNetTest() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/mnet/Aclf_5Bus_DistDcSysChildNet.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		//System.out.println(net.net2String());	
		
		assertTrue(net.getChildNetWrapperList().size() > 0);
		assertTrue(net.getChildNetWrapperList().size() == 1);
		assertTrue(net.getChildNetWrapper("DistChileNet1") != null);
		assertTrue(net.getChildNetWrapper("DistChileNet1").getChildNetInterfaces().size() == 2);
		
		ChildNetInterfaceBranch interBra = (ChildNetInterfaceBranch)net.getChildNetWrapper("DistChileNet1").getInterface("Bus2-Bus1");
		assertTrue(interBra.getInterfaceBusSide() == BranchBusSide.TO_SIDE);
		assertTrue(interBra.getChildNetSide() == BranchBusSide.TO_SIDE);
		assertTrue(interBra.getInterfaceBusIdChildNet().equals("DistBus-1"));		
		
		DistNetwork distNet = (DistNetwork)net.getChildNetWrapper("DistChileNet1").getNetwork();
		assertTrue(distNet.getChildNetWrapperList().size() > 0);	
		interBra = (ChildNetInterfaceBranch)distNet.getChildNetWrapper("ChildDcSysteNet1").getInterface("DistBranchId");
		assertTrue(interBra.getInterfaceBusSide() == BranchBusSide.TO_SIDE);
		assertTrue(interBra.getChildNetSide() == BranchBusSide.TO_SIDE);
		assertTrue(interBra.getInterfaceBusIdChildNet().equals("DcBus1"));
	}	
}
