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

package org.interpss.core.mnet;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.impl.mnet.AclfBusChildNetHelper;
import com.interpss.core.algo.impl.mnet.DefaultMultiNetLfSolver;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.net.childnet.ChildNetwork;
import com.interpss.core.net.childnet.ChildNetworkFactory;
import com.interpss.core.net.childnet.impl.ChildNetworkProcessor;

public class MNet_IEEE14Bus_Test extends CorePluginTestSetup {
	@Test 
	public void bus14testCase() throws Exception {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();		
		
  		//System.out.println(net.net2String());
		assertTrue((net.getBusList().size() == 14 && net.getBranchList().size() == 20));
		assertTrue(net.getChildNetList().size() == 0);
		
		/*
		 * Define child network configuration info

		 * see diagram - https://docs.google.com/a/interpss.org/file/d/0BzjeDvtdQBeyUHNISk5kVmlHYzg/edit
		 * 
		 * define an interface branch
		 *    Bus9->Bus14(1)  @Bus9
		 *    Bus6->Bus13(1)  @Bus6
		 *    Bus12->Bus13(1) @Bus12
		 */
		ChildNetwork<AclfBus,AclfBranch> childNet = CoreObjectFactory.createChildAclfNet(net, "childNet");
		
		//AclfBranch branch = net.getBranch("Bus9->Bus14(1)");
		ChildNetInterfaceBranch intBranch = ChildNetworkFactory.eINSTANCE.createChildNetInterfaceBranch();
		intBranch.setBranchId("Bus9->Bus14(1)");
		intBranch.setInterfaceBusSide(BranchBusSide.FROM_SIDE);		
		intBranch.setChildNetSide(BranchBusSide.TO_SIDE);
		childNet.getInterfaceBranches().add(intBranch);		

		//branch = net.getBranch("Bus6->Bus13(1)");
		intBranch = ChildNetworkFactory.eINSTANCE.createChildNetInterfaceBranch();
		intBranch.setBranchId("Bus6->Bus13(1)");
		intBranch.setInterfaceBusSide(BranchBusSide.FROM_SIDE);		
		intBranch.setChildNetSide(BranchBusSide.TO_SIDE);
		childNet.getInterfaceBranches().add(intBranch);		

		//branch = net.getBranch("Bus12->Bus13(1)");
		intBranch = ChildNetworkFactory.eINSTANCE.createChildNetInterfaceBranch();
		intBranch.setBranchId("Bus12->Bus13(1)");
		intBranch.setInterfaceBusSide(BranchBusSide.FROM_SIDE);		
		intBranch.setChildNetSide(BranchBusSide.TO_SIDE);
		childNet.getInterfaceBranches().add(intBranch);		

		new ChildNetworkProcessor(net).processChildNet();
		//System.out.println(net.net2String());
		
		// see diagram - https://docs.google.com/a/interpss.org/file/d/0BzjeDvtdQBeyUHNISk5kVmlHYzg/edit
		assertTrue((net.getBusList().size() == 12 && net.getBranchList().size() == 16));
		assertTrue(net.getChildNetList().size() > 0);
		
		// check the created child network
		AclfNetwork childAclfNet = (AclfNetwork)net.getChildNet("childNet").getNetwork();
		assertTrue((childAclfNet.getBusList().size() == 5 && childAclfNet.getBranchList().size() == 4));
		
		for (ChildNetInterfaceBranch cbranch : childNet.getInterfaceBranches()) {
			// interface bus should still be in the parent net
			assertTrue(net.getBus(cbranch.getInterfaceBusParentNet().getId()) != null);
			// interface bus should be defined as Swing bus in the child net
			assertTrue(childAclfNet.getBus(cbranch.getInterfaceBusIdChildNet()).isSwing());
		}
		
		//System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.setMultiNetSolver(new DefaultMultiNetLfSolver(algo));
	  	algo.loadflow();
	  	
  		assertTrue(net.isLfConverged());		
 		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
 		AclfSwingBus swing = swingBus.toSwingBus();
		System.out.println(swing.getGenResults(UnitType.PU));
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		/*
		 * Please note this might not be a correct way to break a strongly coupled network
		 * into Parent/Child network and run Loadflow.
		 */
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-1.8121)<0.0001);
  		assertTrue( Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1243)<0.0001);
	}
}
