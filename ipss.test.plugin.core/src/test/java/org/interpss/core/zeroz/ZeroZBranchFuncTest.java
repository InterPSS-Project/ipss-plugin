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

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.simu.net.IpssAclfNet;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Bus;


public class ZeroZBranchFuncTest extends CorePluginTestSetup {
	@Test
	public void busZeroZBranchFuncTest() throws InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAclfNet.createAclfNetwork("Net")
				.setBaseKva(100000.0)
				.getAclfNet();

		// set the network data
	  	set2BusNetworkData(net, msg);
	  	
	  	net.getBranchList().forEach(b -> {
	  		if (b.isZeroZBranch()) {
	  			System.out.println("ZeroZBranch: " + b.getId());
	  			assertTrue(b.getId().equals("Bus2->Bus3(Branch 1)"));
	  		}
	  	});
	  	
	  	assertTrue("", !net.getBus("Bus1").isConnect2ZeroZBranch());
	  	assertTrue("", net.getBus("Bus2").isConnect2ZeroZBranch());
	  	assertTrue("", net.getBus("Bus3").isConnect2ZeroZBranch());
	  	
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
	  	
	  	assertTrue("", net.getBus("Bus2").findZeroZPathBuses().size() == 2);
	  	assertTrue("", net.getBus("Bus3").findZeroZPathBuses().size() == 2);
	}

	private void set2BusNetworkData(AclfNetwork net, IPSSMsgHub msg) throws InterpssException {
		IpssAclfNet.addAclfBus("Bus1", "Bus 1", net)
				.setBaseVoltage(4000.0)
				.setGenCode(AclfGenCode.SWING)
				.setVoltageSpec(1.0, UnitType.PU, 0.0, UnitType.Deg)
				.setLoadCode(AclfLoadCode.NON_LOAD);
  		
		IpssAclfNet.addAclfBus("Bus2", "Bus 2", net)
			.setBaseVoltage(4000.0);

		IpssAclfNet.addAclfBus("Bus3", "Bus 3", net)
  				.setBaseVoltage(4000.0)
  				.setGenCode(AclfGenCode.NON_GEN)
  				.setLoadCode(AclfLoadCode.CONST_P)
  				.setLoad(new Complex(1.0, 0.8), UnitType.PU);
  		
		IpssAclfNet.addAclfBranch("Bus1", "Bus2", "Branch 1", net)
				.setBranchCode(AclfBranchCode.LINE)
				.setZ(new Complex(0.05, 0.1), UnitType.PU);

		IpssAclfNet.addAclfBranch("Bus2", "Bus3", "Branch 1", net)
				.setBranchCode(AclfBranchCode.LINE)
				.setZ(new Complex(0.00000001, 0.000000000001), UnitType.PU);
	}	
	
}
