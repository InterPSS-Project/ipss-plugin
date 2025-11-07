 /*
  * @(#)Test_IEEECommonFormat_Comma.java   
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

package org.interpss.core.adapter.ieee;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEECommonFormat_CommaTest extends CorePluginTestSetup {
	@Test 
	public void testCase1() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14_comma.ieee")
				.getAclfNet();	
		
		assertTrue((net.getBusList().size() == 14 && net.getBranchList().size() == 20));

		assertTrue("", net.getBus("Bus1").getId().equals("Bus1"));
		assertTrue("", net.getBus("Bus1").getContributeGen("Bus1-G1") != null);
		assertTrue("", net.getBus("Bus1").getContributeGen("Bus1-G1").getName().equals("Bus1-G1"));
		
		assertTrue("", net.getBus("Bus2").getContributeGen("Bus2-G1") != null);
		assertTrue("", net.getBus("Bus2").getContributeGen("Bus2-G1").getName().equals("Bus2-G1"));
		assertTrue("", net.getBus("Bus2").getContributeLoad("Bus2-L1") != null);
		assertTrue("", net.getBus("Bus2").getContributeLoad("Bus2-L1").getName().equals("Bus2-L1"));
		
//		IODMAdapter adapter = new IeeeCDFAdapter(IpssLogger.getLogger());
//		adapter.parseInputFile("testdata/ieee_format/ieee14_comma.ieee");
//		
//		AclfNetwork net = PluginSpringCtx
//				.getOdm2AclfMapper()
//				.map2Model((AclfModelParser)adapter.getModel())
//				.getAclfNet();		
		
		//System.out.println(adapter.getODMModelParser().toString());
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();

	  	//System.out.println(net.net2String());
  		assertTrue(net.isLfConverged());		
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32393)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16549)<0.0001);
  		
  		/*
  		System.out.println(CorePluginFunction.AclfResultBusStyle.f(net));
  		
  		System.out.println(net.getBus("Bus9").powerIntoNet());
  		System.out.println(NetInjectionHelper.powerIntoNet(net.getBus("Bus9")));
  		*/
	}
}

