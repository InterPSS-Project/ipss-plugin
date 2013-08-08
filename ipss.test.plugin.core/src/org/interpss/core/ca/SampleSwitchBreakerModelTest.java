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

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.algo.ZeroZBranchProcesor;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.pssl.simu.net.IpssAclfNet;


public class SampleSwitchBreakerModelTest extends CorePluginTestSetup {
	@Test 
	public void case1_regularMethod() {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAclfNet.createAclfNetwork("Net")
				.setBaseKva(100000.0)
				.getAclfNet();

		// set the network data
	  	set2BusNetworkData(net, msg);
	  	
	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.loadflow();
	  	
	  	// output loadflow calculation results
	  	System.out.println(AclfOutFunc.loadFlowSummary(net));
    }	

	@Test 
	public void case2_zeroZBranchProcessing()  throws InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAclfNet.createAclfNetwork("Net")
				.setBaseKva(100000.0)
				.getAclfNet();

		// set the network data
	  	set2BusNetworkData(net, msg);
	  	
	  	// process zero impedance branches in the network
	  	double smallBranchZ = 0.00001;
	  	net.accept(new ZeroZBranchProcesor(smallBranchZ));
	  	assertTrue(net.isZeroZBranchProcessed());

	  	// create the default loadflow algorithm
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

	  	// use the loadflow algorithm to perform loadflow calculation
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.loadflow();
	  	
	  	// output loadflow calculation results
	  	System.out.println(AclfOutFunc.loadFlowSummary(net));
	  	//System.out.println(net.net2String());
    }	

	private void set2BusNetworkData(AclfNetwork net, IPSSMsgHub msg) {
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
