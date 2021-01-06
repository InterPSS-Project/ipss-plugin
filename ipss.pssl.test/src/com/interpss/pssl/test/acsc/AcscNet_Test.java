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

package com.interpss.pssl.test.acsc;

import org.apache.commons.math3.complex.Complex;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.AcscOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.simu.IpssAclf;
import org.interpss.pssl.simu.net.IpssAcscNet;
import org.interpss.pssl.simu.net.IpssAcscNet.AcscNetworkDSL;
import org.interpss.pssl.util.AcscSample;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.sc.ScBusVoltageType;
import com.interpss.core.algo.sc.SimpleFaultAlgorithm;
import com.interpss.pssl.test.BaseTestSetup;

public class AcscNet_Test extends BaseTestSetup {
	@Test
	public void unitVoltTest()  throws InterpssException  {
		AcscNetwork faultNet = AcscSample.create5BusSampleNet();
		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
		System.out.println("----------- Fault using UnitVolt ---------------------");		
		System.out.println(AcscOutFunc.faultResult2String(faultNet, algo));		
	}

	@Test
	public void lfVoltTest()  throws InterpssException  {
		AcscNetwork faultNet = AcscSample.create5BusSampleNet();
		IpssAclf.createAclfAlgo(faultNet)                        
		            .lfMethod(AclfMethod.NR)
		            .tolerance(0.0001, UnitType.PU)
		            .runLoadflow();               

		System.out.println(AclfOutFunc.loadFlowSummary(faultNet));
		//System.out.println(netDsl.getAcscNet().net2String());
		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
	  	algo.setScBusVoltage(ScBusVoltageType.LOADFLOW_VOLT);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
		System.out.println("----------- Fault using AclfVolt ---------------------");		
		System.out.println(AcscOutFunc.faultResult2String(faultNet, algo));		
	}
}

