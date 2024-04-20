 /*
  * @(#)Sample2BusTest.java   
  *
  * Copyright (C) 2011 www.interpss.org
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
  * @Date 02/01/2011
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.adapter.odm.dist;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssDist;
import org.interpss.pssl.simu.net.IpssDistNet.DistNetDSL;
import org.junit.Test;

import com.interpss.dist.DistBus;
import com.interpss.dist.DistNetwork;

//DistNet
public class DistSample2BusTest  extends CorePluginTestSetup { 
	//@Test
	public void simple2BusTest() throws Exception {
		DistNetwork distNet = IpssAdapter.importAclfNet("testData/odm/dist/Sample2Bus.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
		//System.out.println(distNet.net2String());
		
		DistNetDSL distNetDSL = IpssDist.wrapDistNetwork(distNet);
		distNetDSL.loadflow();
	  	
	  	DistBus bus = distNet.getBus("Bus-1");
	  	//System.out.println(bus.getAcscBus().getGenResults().getReal());
	  	//System.out.println(bus.getAcscBus().getGenResults().getImaginary());
	  	assertTrue(Math.abs(bus.getBus().calNetGenResults().getReal() + 0.05) < 0.001);
	  	assertTrue(Math.abs(bus.getBus().calNetGenResults().getImaginary() + 0.0349) < 0.0001);
	}

	//@Test
	public void simple2BusDSLTest() throws Exception {
		DistNetwork distNet = IpssAdapter.importAclfNet("testData/odm/dist/Sample2Bus.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		//System.out.println(distNet.getAclfNetwork().net2String());

		DistNetDSL distNetDSL = IpssDist.wrapDistNetwork(distNet);
		distNetDSL.loadflow();	  	

		DistBus bus = distNet.getBus("Bus-1");
	  	//System.out.println(bus.getAcscBus().getGenResults().getReal());
	  	//System.out.println(bus.getAcscBus().getGenResults().getImaginary());
	  	assertTrue(Math.abs(bus.getBus().calNetGenResults().getReal() + 0.05) < 0.001);
	  	assertTrue(Math.abs(bus.getBus().calNetGenResults().getImaginary() + 0.0349) < 0.0001);
	}

	//@Test
	public void simple2BusDSL_MixedLoad_Test() throws Exception {
		DistNetwork distNet = IpssAdapter.importAclfNet("testData/odm/dist/Sample2BusMixedLoad.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
		//System.out.println(distNetDSL.getAclfNetwork().net2String());
		//System.out.println(AclfOutFunc.lfResultsBusStyle(distNetDSL.getAclfNetwork()));
	  	
		DistNetDSL distNetDSL = IpssDist.wrapDistNetwork(distNet);
		distNetDSL.loadflow();		
		
	  	DistBus bus = distNet.getBus("Bus-1");
	  	//System.out.println(bus.getAcscBus().getGenResults().getReal());
	  	//System.out.println(bus.getAcscBus().getGenResults().getImaginary());
	  	assertTrue(Math.abs(bus.getBus().calNetGenResults().getReal() - 0.07998) < 0.001);
	  	assertTrue(Math.abs(bus.getBus().calNetGenResults().getImaginary() - 0.067659) < 0.0001);
	}
}

