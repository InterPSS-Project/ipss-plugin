 /*
  * @(#)TestSimuAppCtx.java   
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

package org.interpss.spring;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.TestUtilFunc;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.SimpleFaultAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.util.sample.SampleCases;
import com.interpss.spring.CoreSimuSpringFactory;
import com.interpss.spring.CoreSpringFactory;

public class SimuAppCtxTest extends CorePluginTestSetup {
	
	@Test
	public void testSimuCtxAclf() {
		SimuContext simuCtx = CoreSimuSpringFactory.getSimuContextTypeAclf();
		SampleCases.load_LF_5BusSystem(simuCtx.getAclfNet());
		simuCtx.setLoadflowAlgorithm(CoreSpringFactory.getLoadflowAlgorithm());
		simuCtx.getLoadflowAlgorithm().setAclfNetwork(simuCtx.getAclfNet());
		//System.out.println(net.net2String());

	  	LoadflowAlgorithm algo = simuCtx.getLoadflowAlgorithm();
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(simuCtx.getAclfNet().isLfConverged());
  		
  		AclfBus swingBus = (AclfBus)(simuCtx.getAclfNet()).getBus("5");
  		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);		
	}

	@Test
	public void testSimuCtxAcsc() throws InterpssException  {
		SimuContext simuCtx = CoreSimuSpringFactory.getSimuContextTypeAcscNet();
		SampleCases.load_SC_5BusSystem(simuCtx.getAcscNet());
		simuCtx.setSimpleFaultAlgorithm(CoreSpringFactory.getSimpleFaultAlgorithm());
		//System.out.println(simuCtx.getAcscFaultNet().net2String());

  		assertTrue((simuCtx.getAcscNet().getBusList().size() == 5 && 
  					       simuCtx.getAcscNet().getBranchList().size() == 5));
  		
	  	SimpleFaultAlgorithm algo = simuCtx.getSimpleFaultAlgorithm();

	  	AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
  		//System.out.println(fault.toString(faultBus.getBaseVoltage(), faultNet.getBaseKva()));
		/*
		 fault amps(1): (  0.0000 + j 32.57143) pu
		 fault amps(2): (  0.0000 + j  0.0000) pu
		 fault amps(0): (  0.0000 + j  0.0000) pu
		 */
		assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 0.0, 0.0, 0.0, 32.57142857157701, 0.0, 0.0) );
		
		//System.out.println(AcscOut.faultResult2String(simuCtx.getAcscFaultNet()));
	}
}
