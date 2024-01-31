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

package org.interpss.core.aclf;

import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetHelper;
import com.interpss.core.funcImpl.CoreCopyFunc;

public class SwingBusSubAreaTest extends CorePluginTestSetup {
	@Test
	public void connectedSubAreaTest() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				.load("testData/ipssdata/BUS1824.ipssdat")
				.getAclfNet();	

		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 1824));
  	
		AclfNetHelper helper = new AclfNetHelper(net);
		Set<String> busSet = helper.calConnectedSubArea("1a");
	  	assertTrue("", busSet.size() == 57);
	  	
		busSet = helper.calConnectedSubArea("1z");
	  	assertTrue("", busSet.size() == 57);
	  	
	  	AclfNetwork subNet = net.createSubNet(busSet);
	  	assertTrue("", subNet.getNoBus() == 57);
	  	
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(subNet);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		//System.out.println(AclfOutFunc.loadFlowSummary(subNet, true));
	  	
  		assertTrue(subNet.isLfConverged());	
	}
}

