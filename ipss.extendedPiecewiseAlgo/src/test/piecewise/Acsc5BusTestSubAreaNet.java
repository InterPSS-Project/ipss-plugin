 /*
  * @(#)AcscSampleTest.java   
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

package test.piecewise;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.util.TestUtilFunc;
import org.interpss.piecewise.SubAreaNetProcessor;
import org.interpss.piecewise.seq012.CuttingBranch012;
import org.interpss.piecewise.seq012.SubAcscNetwork;
import org.interpss.piecewise.seq012.SubArea012;
import org.interpss.piecewise.seq012.impl.SubNetworkAcscProcessorImpl;
import org.interpss.piecewise.seq012.impl.SubAreaAcscProcessorImpl;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.SimpleFaultAlgorithm;
import com.interpss.simu.util.sample.SampleCases;

public class Acsc5BusTestSubAreaNet {
	@Test
	public void subAreaTest() throws InterpssException {
		IpssCorePlugin.init();
		
  		AcscNetwork net = CoreObjectFactory.createAcscNetwork();
		SampleCases.load_SC_5BusSystem(net);
		//System.out.println(net.net2String());
		
		SubAreaNetProcessor<AcscBus, AcscBranch, SubArea012, Complex3x1> proc = 
				new SubAreaAcscProcessorImpl<SubArea012>(net, new CuttingBranch012[] { 
						new CuttingBranch012("1->2(1)"),
						new CuttingBranch012("1->3(1)"),
						new CuttingBranch012("2->3(1)")});	
  		
  		proc.processSubAreaNet();
  		
  		proc.getSubAreaNetList().forEach(subarea -> {
  			System.out.println(subarea);
  		});		
  		
  		assertTrue(proc.getSubAreaNetList().size() == 3);
  		assertTrue(proc.getSubAreaNet(1).getInterfaceBusIdList().size() == 1);
  		assertTrue(proc.getSubAreaNet(2).getInterfaceBusIdList().size() == 1); 		
  		assertTrue(proc.getSubAreaNet(3).getInterfaceBusIdList().size() == 1);
	}

	@Test
	public void subNetworkTest() throws InterpssException {
		IpssCorePlugin.init();
		
  		AcscNetwork net = CoreObjectFactory.createAcscNetwork();
		SampleCases.load_SC_5BusSystem(net);
		//System.out.println(net.net2String());
		
		SubAreaNetProcessor<AcscBus, AcscBranch, SubAcscNetwork, Complex3x1> proc = 
				new SubNetworkAcscProcessorImpl<>(net, new CuttingBranch012[] { 
						new CuttingBranch012("1->2(1)"),
						new CuttingBranch012("1->3(1)"),
						new CuttingBranch012("2->3(1)")});	
  		
  		proc.processSubAreaNet();
  		
  		proc.getSubAreaNetList().forEach(subarea -> {
  			//System.out.println(subarea);
  		});		
  		
  		assertTrue(proc.getSubAreaNetList().size() == 3);
  		assertTrue(proc.getSubAreaNet(1).getInterfaceBusIdList().size() == 1);
  		assertTrue(proc.getSubAreaNet(2).getInterfaceBusIdList().size() == 1); 		
  		assertTrue(proc.getSubAreaNet(3).getInterfaceBusIdList().size() == 1);
  		
  		assertTrue(proc.getSubAreaNet(1).getSubNet().getBusList().size() == 1);
  		assertTrue(proc.getSubAreaNet(1).getSubNet().getBranchList().size() == 0);

  		assertTrue(proc.getSubAreaNet(2).getSubNet().getBusList().size() == 2); 		
  		assertTrue(proc.getSubAreaNet(2).getSubNet().getBranchList().size() == 1); 		

  		assertTrue(proc.getSubAreaNet(3).getSubNet().getBusList().size() == 2);
  		assertTrue(proc.getSubAreaNet(3).getSubNet().getBranchList().size() == 1);
	}
	
	@Test
	public void sampleTest() throws InterpssException {
		IpssCorePlugin.init();
		
  		AcscNetwork faultNet = CoreObjectFactory.createAcscNetwork();
		SampleCases.load_SC_5BusSystem(faultNet);
		//System.out.println(faultNet.net2String());

  		assertTrue((faultNet.getBusList().size() == 5 && faultNet.getBranchList().size() == 5));
  		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
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
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 
	  			0.0, 0.0, 0.0, 32.57142857157701, 0.0, 0.0) );
		
		//System.out.println(AcscOut.faultResult2String(faultNet));
	}
}

