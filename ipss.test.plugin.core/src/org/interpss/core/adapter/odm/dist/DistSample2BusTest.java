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

import java.io.File;
import java.io.FileInputStream;

import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.model.dist.DistModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.spring.CorePluginSpringFactory;
import org.junit.Test;

import com.interpss.dist.DistBus;
import com.interpss.dist.DistNetwork;
import com.interpss.pssl.simu.IpssDist;
import com.interpss.pssl.simu.net.IpssDistNet.DistNetDSL;

public class DistSample2BusTest  extends CorePluginTestSetup { 
	@Test
	public void simple2BusTest() throws Exception {
		File file = new File("testData/odm/dist/Sample2Bus.xml");
		DistModelParser parser = ODMObjectFactory.createDistModelParser();
		if (!parser.parse(new FileInputStream(file))) {
		}
		//System.out.println(parser.toXmlDoc(false));
			
		DistNetwork distNet = CorePluginSpringFactory
					.getOdm2DistParserMapper().map2Model(parser);
		//System.out.println(distNet.net2String());
		
		DistNetDSL distNetDSL = IpssDist.wrapDistNetwork(distNet);
		distNetDSL.loadflow();
		//System.out.println(distNet.getAclfNetwork().net2String());
		//System.out.println(AclfOutFunc.lfResultsBusStyle(distNetDSL.getAclfNetwork()));
	  	
	  	DistBus bus = (DistBus)distNetDSL.getDistNetwork().getBus("Bus-1");
	  	//System.out.println(bus.getAcscBus().getGenResults().getReal());
	  	//System.out.println(bus.getAcscBus().getGenResults().getImaginary());
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getReal() + 0.05) < 0.001);
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getImaginary() + 0.0349) < 0.0001);
	}

	@Test
	public void simple2BusDSLTest() throws Exception {
		DistNetDSL distNetDSL = IpssDist.loadDistNetwork("testData/odm/dist/Sample2Bus.xml");
		distNetDSL.loadflow();
		//System.out.println(distNet.getAclfNetwork().net2String());
		//System.out.println(AclfOutFunc.lfResultsBusStyle(distNetDSL.getAclfNetwork()));
	  	
	  	DistBus bus = (DistBus)distNetDSL.getDistNetwork().getBus("Bus-1");
	  	//System.out.println(bus.getAcscBus().getGenResults().getReal());
	  	//System.out.println(bus.getAcscBus().getGenResults().getImaginary());
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getReal() + 0.05) < 0.001);
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getImaginary() + 0.0349) < 0.0001);
	}

	@Test
	public void simple2BusDSL_MixedLoad_Test() throws Exception {
		DistNetDSL distNetDSL = IpssDist.loadDistNetwork("testData/odm/dist/Sample2BusMixedLoad.xml");
		distNetDSL.loadflow();
		//System.out.println(distNetDSL.getAclfNetwork().net2String());
		//System.out.println(AclfOutFunc.lfResultsBusStyle(distNetDSL.getAclfNetwork()));
	  	
	  	DistBus bus = (DistBus)distNetDSL.getDistNetwork().getBus("Bus-1");
	  	//System.out.println(bus.getAcscBus().getGenResults().getReal());
	  	//System.out.println(bus.getAcscBus().getGenResults().getImaginary());
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getReal() - 0.07998) < 0.001);
	  	assertTrue(Math.abs(bus.getAcscBus().getGenResults().getImaginary() - 0.067659) < 0.0001);
	}
}

