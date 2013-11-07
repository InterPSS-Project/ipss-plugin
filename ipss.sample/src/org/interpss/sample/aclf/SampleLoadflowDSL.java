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

package org.interpss.sample.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.pssl.simu.IpssAclf;
import com.interpss.pssl.simu.net.IpssAclfNet;


public class SampleLoadflowDSL {
	public static void main(String args[]) {
		IpssCorePlugin.init();
		
		AclfNetwork net = IpssAclfNet.createAclfNetwork("Sample AclfNetwork")
				.setBaseKva(100000.0)
				.getAclfNet();

		IpssAclfNet.addAclfBus("Bus3", "Bus 3", net)
				.setBaseVoltage(100000.0)
				.setGenCode(AclfGenCode.SWING)
				.setVoltageSpec(0.90, UnitType.PU, 0.0, UnitType.Deg)
				.setLoadCode(AclfLoadCode.NON_LOAD);
	
		IpssAclfNet.addAclfBus("Bus2", "Bus 2", net)
				.setBaseVoltage(100000.0)
				.setGenCode(AclfGenCode.NON_GEN)
				.setLoadCode(AclfLoadCode.NON_LOAD);
				
		
		IpssAclfNet.addAclfBus("Bus1", "Bus 1", net)
		        .setBaseVoltage(100000.0)
		        .setGenCode(AclfGenCode.NON_GEN)
		        .setLoadCode(AclfLoadCode.CONST_P)
		        .setLoad(new Complex(1.998, 2.96591), UnitType.PU);
	  
		IpssAclfNet.addAclfBranch("Bus2", "Bus1", "Branch 2", net)
				.setBranchCode(AclfBranchCode.LINE)
				.setZ(new Complex(0.00, 0.93), UnitType.PU);
		
		IpssAclfNet.addAclfBranch("Bus2", "Bus1", "Branch 3", net)
		        .setBranchCode(AclfBranchCode.LINE)
		        .setZ(new Complex(0.00, 0.5), UnitType.PU);
		
		IpssAclfNet.addAclfBranch("Bus3", "Bus2", "Branch 2", net)
		        .setBranchCode(AclfBranchCode.LINE)
		        .setZ(new Complex(0.00, 0.15), UnitType.PU);
		
	  	// create the default loadflow algorithm
	  	IpssAclf.createAclfAlgo(net)
	  			.setLfMethod(AclfMethod.NR)
	  			.setTolerance(0.0001, UnitType.PU)
	  			.runLoadflow();

	  	// output loadflow calculation results
	  	System.out.println(AclfOutFunc.loadFlowSummary(net));
	}	
}
