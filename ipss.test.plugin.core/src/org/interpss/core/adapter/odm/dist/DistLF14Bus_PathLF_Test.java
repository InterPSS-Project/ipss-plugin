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
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.algo.path.NetPathWalkAlgorithm;
import com.interpss.core.algo.path.NetPathWalkDirectionEnum;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.dist.DistNetwork;
import com.interpss.dist.algo.path.DistPathBranchWalker;
import com.interpss.dist.algo.path.DistPathBusWalker;
import com.interpss.dist.algo.path.DistPathLfAlgorithm;
import com.interpss.dist.algo.path.DistPathNetInitinizer;

public class DistLF14Bus_PathLF_Test  extends CorePluginTestSetup {
	@Test
	public void bus14_lf1_Test() throws Exception {
		DistNetwork distNet = IpssAdapter.importNet("testData/odm/dist/Dist_14Bus.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		
		distNet.createAcscNetData();
		distNet.getFaultNet().setLfDataLoaded(true);
		
		distNet.accept(new DistPathLfAlgorithm(0.001, 20));
		assertTrue(distNet.getAcscNet().isLfConverged());
		
/*		
		for (Bus b : distNet.getBusList()) {
			DistBus bus = (DistBus)b;
			System.out.println(b.getId() + " " + ComplexFunc.toMagAng(bus.getAcscBus().getVoltage()) +
						"  mismatch: " + bus.getAcscBus().mismatch(AclfMethod.NR).abs());
		}
*/		
	}
	
	@Test
	public void bus14_lf_Test() throws Exception {
		DistNetwork distNet = IpssAdapter.importNet("testData/odm/dist/Dist_14Bus.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		
		distNet.createAcscNetData();
		distNet.getFaultNet().setLfDataLoaded(true);
		
		DistPathLfAlgorithm algo = new DistPathLfAlgorithm(distNet, true);
		algo.loadflow();
		assertTrue(distNet.getAcscNet().isLfConverged());
/*		
		for (Bus b : distNet.getBusList()) {
			DistBus bus = (DistBus)b;
			System.out.println(b.getId() + " " + ComplexFunc.toMagAng(bus.getAcscBus().getVoltage()) +
						"  mismatch: " + bus.getAcscBus().mismatch(AclfMethod.NR).abs());
		}
		
		System.out.println(algo.getStatusInfo());
*/
	}

	@Test
	public void bus14_walk_Test() throws Exception {
		DistNetwork distNet = IpssAdapter.importNet("testData/odm/dist/Dist_14Bus.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		//System.out.println(distNet.net2String());
		
		NetPathWalkAlgorithm<?,?> walkAlgo = CoreObjectFactory.createNetPathWalkAlgorithm();

		// forward walking
		walkAlgo.branchWalkThough(distNet, 
				new DistPathNetInitinizer(NetPathWalkDirectionEnum.ALONG_PATH), 
				new DistPathBranchWalker());
		
		for (Bus b : distNet.getBusList()) 
		 	assertTrue(b.isVisited());
		for (Branch b : distNet.getBranchList())
		 	assertTrue(b.isVisited());

		// backward walking
		walkAlgo.busWalkThough(distNet, 
				new DistPathNetInitinizer(NetPathWalkDirectionEnum.OPPOSITE_PATH), 
				new DistPathBusWalker());
		
		for (Bus b : distNet.getBusList()) 
		 	assertTrue(b.isVisited());
		for (Branch b : distNet.getBranchList())
		 	assertTrue(b.isVisited());
	}
}

