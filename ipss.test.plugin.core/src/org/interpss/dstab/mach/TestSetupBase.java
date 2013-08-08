 /*
  * @(#)TestSetupBase.java   
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

package org.interpss.dstab.mach;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.spring.CoreCommonSpringFactory;

public class TestSetupBase extends CorePluginTestSetup {
	protected IPSSMsgHub msg;
	protected DStabilityNetwork net = null;

	public TestSetupBase() { 
		msg = CoreCommonSpringFactory.getIpssMsgHub();
	}

	/**
	 * Create a two-bus system for transient stability unit testing
	 * 
	 * @return the net contains the bus
	 */
	public DStabilityNetwork createTestDStabBus() {
		net = DStabObjectFactory.createDStabilityNetwork();
		net.setFrequency(50.0);
		
		// First bus is PQ Gen bus
		DStabBus bus1 = DStabObjectFactory.createDStabBus("Gen", net);
		bus1.setName("Gen Bus");
		bus1.setBaseVoltage(1000);
		bus1.setGenCode(AclfGenCode.GEN_PQ);
		bus1.setLoadCode(AclfLoadCode.CONST_P);
		bus1.setGenP(0.8);
		bus1.setGenQ(0.6);
		
		// Second bus is a Swing bus
		DStabBus bus2 = DStabObjectFactory.createDStabBus("Swing", net);
		bus2.setName("Swing Bus");
		bus2.setBaseVoltage(1000);
		bus2.setGenCode(AclfGenCode.SWING);
		AclfSwingBus swing = bus2.toSwingBus();
		swing.setVoltMag(0.9434, UnitType.PU);
		swing.setVoltAng(-4.86444, UnitType.Deg);

		// a line branch connect the two buses
		DStabBranch branch = DStabObjectFactory.createDStabBranch("Gen", "Swing", net);
		branch.setBranchCode(AclfBranchCode.LINE);
		branch.setZ(new Complex(0.0, 0.1));
		
		// run load flow
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.loadflow();
	  	
	  	// uncommet this line to see the net object states
  		//System.out.println(net.net2String());
	  	
		return net;
	}
}

