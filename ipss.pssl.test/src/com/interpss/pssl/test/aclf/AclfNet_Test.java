 /*
  * @(#)AclfSampleTest.java   
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
  * @Date 07/15/2007
  * 
  *   Revision History
  *   ================
  *
  */

package com.interpss.pssl.test.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.simu.IpssAclf;
import org.interpss.pssl.simu.net.IpssAclfNet;
import org.interpss.pssl.simu.net.IpssAclfNet.AclfNetworkDSL;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.pssl.test.BaseTestSetup;

public class AclfNet_Test extends BaseTestSetup {
	@Test
	public void singlePointTest1() throws InterpssException {
		AclfNetworkDSL netDsl = IpssAclfNet.createAclfNetwork("Sample DistNetwork");
		netDsl.baseMva(100.0);
		

		netDsl.addAclfBus("Bus1", "name-Bus 1")
		            .baseVoltage(4000.0)
		            .genCode(AclfGenCode.SWING)
		            .voltageSpec(1.0, UnitType.PU, 0.0, UnitType.Deg);
		         
		netDsl.addAclfBus("Bus2", "name-Bus 2")
		            .baseVoltage(4000.0)  
		            .loadCode(AclfLoadCode.CONST_P)
		            .load(new Complex(1.0, 0.8), UnitType.PU);
		         
		netDsl.addAclfBranch("Bus1", "Bus2")
		            .branchCode(AclfBranchCode.LINE)
		            .z(new Complex(0.05, 0.1), UnitType.PU);       
		               
		IpssAclf.createAclfAlgo(netDsl.getAclfNet())                        
		            .lfMethod(AclfMethod.NR)
		            .tolerance(0.0001, UnitType.PU)
		            .runLoadflow();               

		System.out.println(AclfOutFunc.loadFlowSummary(netDsl.getAclfNet()));
	}
	
	public void document() throws InterpssException {
		AclfNetworkDSL netDsl = IpssAclfNet.createAclfNetwork("Sample DistNetwork");
		netDsl.baseMva(100.0);
		
		String id = "";
		netDsl.addAclfBus(id, "Bus-"+id)
			.areaNumber(1)
			.zoneNumber(1)
			.baseVoltage(1000.0)
			.loadCode(AclfLoadCode.CONST_P)
			.load(new Complex(0.1, 1.0), UnitType.PU);
		
		netDsl.addAclfBus(id, "Bus-"+id)
			.areaNumber(1)
			.zoneNumber(1)
			.baseVoltage(1000.0, UnitType.Volt);
		
		netDsl.addAclfBus(id, "Bus-"+id)
			.areaNumber(1)
			.zoneNumber(1)
			.baseVoltage(1000.0)
			.genCode(AclfGenCode.GEN_PQ)
			.gen(new Complex(1.0,1.2), UnitType.kVA);		
	}	
}

