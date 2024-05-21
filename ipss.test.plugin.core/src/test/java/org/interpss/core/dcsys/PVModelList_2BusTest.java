 /*
  * @(#)PVModelList_2BusTest.java   
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

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.plugin.IpssAdapter;

import com.interpss.dc.DcBus;
import com.interpss.dc.DcNetwork;
import com.interpss.dc.DcSysObjectFactory;
import com.interpss.dc.algo.DcPowerFlowAlgorithm;

public class PVModelList_2BusTest  extends CorePluginTestSetup { 
	//@Test
	public void pvModel2BusCase() throws Exception {
		//DcNetwork dcNet = CorePluginObjFactory.createDcNetwork("testData/odm/dcsys/PVModelList2Bus.xml");
		DcNetwork dcNet = IpssAdapter.importAclfNet("testData/odm/dcsys/PVModelList2Bus.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
		//System.out.println(dcNet.net2String());
		
		DcPowerFlowAlgorithm algo = DcSysObjectFactory.createDcPowerFlowAlgorithm();
		algo.calLoadflow(dcNet);
		assertTrue(dcNet.isLfConverged());
		//System.out.println(dcNet.net2String());
		
		DcBus bus = dcNet.getDcBus("Bus1");
		//System.out.println("0" + bus.powerInjection());
		//System.out.println("1 " + bus.getVoltage(UnitType.Volt));
		assertTrue(Math.abs(bus.powerInjection() + 0.0) < 0.001);
		
		bus = dcNet.getDcBus("Bus2");
		//System.out.println("2 " + bus.getVoltage(UnitType.Volt));
		assertTrue(Math.abs(bus.getVoltage(UnitType.Volt) - 120.0) < 0.001);	
		
		//dcNet.accept(new DcResultOutput());
	}
}

