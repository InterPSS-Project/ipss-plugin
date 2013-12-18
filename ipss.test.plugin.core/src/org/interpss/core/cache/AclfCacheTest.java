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

package org.interpss.core.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.interpss.CoreObjectFactory;
import com.interpss.cache.UgidGenerator;
import com.interpss.cache.aclf.AclfNetCacheWrapper;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.IpssCacheException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.simu.util.sample.SampleCases;

public class AclfCacheTest extends CorePluginTestSetup {
	HazelcastInstance client;
	
	@Before
	public void init() {
		ClientConfig clientConfig = new ClientConfig();
		
		clientConfig.addAddress("127.0.0.1");
		client = HazelcastClient.newHazelcastClient(clientConfig);
		UgidGenerator.IdGenerator = client.getIdGenerator("GuidGenerator");		
	}
	
	@Test
	public void Bus5SampleTest() throws IpssCacheException {
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);
		long key = cache.put(net);
		 
		AclfNetwork net1 = cache.get(key);
		//System.out.println(net1.net2String());

		/*
		 * run basecase Aclf
		 */
	  	net.accept(CoreObjectFactory.createLfAlgoVisitor());
  		assertTrue(net.isLfConverged());
  		
  		assertEquals(true, Math.abs(net.areaOutputPower(1, UnitType.PU)-1.28164)<0.0001);

  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);
		
		/*
		 * run cached case Aclf
		 */
	  	net1.accept(CoreObjectFactory.createLfAlgoVisitor());
  		assertTrue(net1.isLfConverged());
  		
  		assertEquals(true, Math.abs(net1.areaOutputPower(1, UnitType.PU)-1.28164)<0.0001);

  		swingBus = (AclfBus)net1.getBus("5");
		swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);
	}

	@Test
	public void IEEE14BusTest() throws IpssCacheException, InterpssException {
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();
		//System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(net);
		 
		AclfNetwork net1 = cache.get(key);
		System.out.println(net1.net2String());
		
	  	net1.accept(CoreObjectFactory.createLfAlgoVisitor());
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net1.isLfConverged());		
  		AclfSwingBus swing = net1.getBus("Bus1").toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32393)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16549)<0.0001);	
  	}
	
	@After
	public void cleanup() {
		client.shutdown();		
	}
}

