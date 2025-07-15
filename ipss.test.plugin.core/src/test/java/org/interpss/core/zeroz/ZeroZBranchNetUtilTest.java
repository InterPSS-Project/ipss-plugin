 /*
  * @(#)SampleLoadflow.java   
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

package org.interpss.core.zeroz;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;


// ZeroZBranch Mark : Zero Z Branch Util Func Test
public class ZeroZBranchNetUtilTest extends CorePluginTestSetup {
	@Test 
	public void test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());

		//assertTrue("", net.getBus("Bus7").isPureZbrConnectBus());
		//assertTrue("", !net.getBus("Bus71").isPureZbrConnectBus());
		
		assertTrue("", net.getBus("Bus7").getNoConnectedZbr(false) == 3);
		assertTrue("", net.getBus("Bus71").getNoConnectedZbr(false) == 1);
		
		assertTrue("", net.getBus("Bus7").getNoConnectedZbr(true) == 3);
		assertTrue("", net.getBus("Bus71").getNoConnectedZbr(true) == 3);

		assertTrue("", net.getBus("Bus7").findZeroZPathBuses().size() == 4);
		assertTrue("", !net.getBus("Bus7").hasZbrLoop());

		assertTrue("", net.getBus("Bus1").findZeroZPathBuses().size() == 4);
		assertTrue("", net.getBus("Bus1").getNoConnectedZbr(true) == 3);
		assertTrue("", !net.getBus("Bus1").hasZbrLoop());
		
		assertTrue("", net.getBus("Bus18").findZeroZPathBuses().size() == 3);
		assertTrue("", net.getBus("Bus18").getNoConnectedZbr(true) == 2);
		assertTrue("", !net.getBus("Bus18").hasZbrLoop());
    }	
	
	@Test 
	public void test_loop() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker_loop.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());

		//assertTrue("", net.getBus("Bus7").isPureZbrConnectBus());
		//assertTrue("", !net.getBus("Bus71").isPureZbrConnectBus());
		
		assertTrue("", net.getBus("Bus7").getNoConnectedZbr(false) == 3);
		assertTrue("", net.getBus("Bus71").getNoConnectedZbr(false) == 2);
		
		assertTrue("", net.getBus("Bus7").getNoConnectedZbr(true) == 5);
		assertTrue("", net.getBus("Bus71").getNoConnectedZbr(true) == 5);
		assertTrue("", net.getBus("Bus7").findZeroZPathBuses().size() == 5);
		assertTrue("", net.getBus("Bus7").hasZbrLoop());
		
		assertTrue("", net.getBus("Bus1").findZeroZPathBuses().size() == 3);
		assertTrue("", net.getBus("Bus1").getNoConnectedZbr(true) == 2);	
		assertTrue("", !net.getBus("Bus1").hasZbrLoop());
		
		assertTrue("", net.getBus("Bus18").findZeroZPathBuses().size() == 4);
		assertTrue("", net.getBus("Bus18").getNoConnectedZbr(true) == 3);
		assertTrue("", !net.getBus("Bus18").hasZbrLoop());
    }	
}


	
