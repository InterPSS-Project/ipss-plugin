 /*
  * @(#)POC_Test1.java   
  *
  * Copyright (C) 2008 www.interpss.org
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
  * @Date 02/01/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.dcsys;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFunction;
import org.interpss.CorePluginTestSetup;
import org.interpss.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.DcSysObjectFactory;
import com.interpss.dc.DcBus;
import com.interpss.dc.DcNetwork;
import com.interpss.dc.algo.DcPowerFlowAlgorithm;
import com.interpss.dc.algo.DcPowerFlowMethod;

public class POC_Test1  extends CorePluginTestSetup { 
	//@Test
	public void baseTest() throws Exception {
		//DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/poc/Test1_odm.xml");
		DcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/poc/Test1_odm.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		

		DcBus bus = dcNet.getDcBus("Inverter");
		bus.setVoltage(375.0/400.0);
		//System.out.println(dcNet.net2String());
		
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowAlgorithm();
		algo.setMethod(DcPowerFlowMethod.PATH);
		
        dcNet.accept(algo);
		assertTrue(dcNet.isLfConverged());		
		//System.out.println(net.net2String());
		System.out.println(CorePluginFunction.OutputSolarNet.fx(dcNet));
	}

	//@Test
	public void base1Test() throws Exception {
//		DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/poc/Test1_odm.xml");
		DcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/poc/Test1_odm.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
		
		DcBus bus = dcNet.getDcBus("Inverter");
		bus.setVoltage(378.0/400.0);
		//System.out.println(dcNet.net2String());
		
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowAlgorithm();
		algo.setMethod(DcPowerFlowMethod.PATH);
		
        dcNet.accept(algo);
		assertTrue(dcNet.isLfConverged());		
		//System.out.println(net.net2String());
		System.out.println(CorePluginFunction.OutputSolarNet.fx(dcNet));
	}

	//@Test
	public void mpptTest() throws Exception {
		//DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/poc/Test1_odm.xml");
		DcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/poc/Test1_odm.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
		//System.out.println(dcNet.net2String());
		
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowMppt(dcNet.getDcBus("Inverter"));
		
        dcNet.accept(algo);
		assertTrue(dcNet.isLfConverged());		
		//System.out.println(net.net2String());
		System.out.println(CorePluginFunction.OutputSolarNet.fx(dcNet));
	}
}

