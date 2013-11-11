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
import org.junit.Test;

import com.interpss.dist.DistBus;
import com.interpss.dist.DistNetwork;
import com.interpss.pssl.plugin.IpssAdapter;
import com.interpss.pssl.simu.IpssDist;
import com.interpss.pssl.simu.net.IpssDistNet.DistNetDSL;

public class DistLF14BusTest  extends CorePluginTestSetup { 
	@Test
	public void simple2BusTest() throws Exception {
		DistNetwork distNet = IpssAdapter.importNet("testData/odm/dist/Dist_14Bus.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();		
		//System.out.println(distNet.net2String());
		
		DistNetDSL distNetDSL = IpssDist.wrapDistNetwork(distNet);
		distNetDSL.loadflow();
	  	
	  	DistBus bus = distNet.getBus("Bus-1");
	  	//System.out.println(bus.getAcscBus().getGenResults().getReal());
	  	//System.out.println(bus.getAcscBus().getGenResults().getImaginary());
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getReal() - 0.26) < 0.001);
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getImaginary() - 0.168) < 0.001);
	}
}

