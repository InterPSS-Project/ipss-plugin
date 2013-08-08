 /*
  * @(#)Test_IEEECommonFormat.java   
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

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;

public class IEEECDFTempTest extends CorePluginTestSetup {
	@Test
	public void bus39testCase() throws Exception{
		IpssFileAdapter adapter = CorePluginObjFactory.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF);
		SimuContext simuCtx = adapter.load("testData/ieee_format/ieee039.DAT");

		AclfNetwork net = simuCtx.getAclfNet();
  		assertTrue(net.getBusList().size() == 39);

	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
 		AclfBus swingBus = (AclfBus)net.getBus("Bus31");
 		AclfSwingBus swing = swingBus.toSwingBus();
//		  31 BUS-31  100   1  1  3 0.982 0.     9.2      4.6       572.8349207.0362 100.    .98200 999900 -99990    0.      0.        0                                                                                                                                                                            
  		assertTrue( Math.abs(swing.getGenResults(UnitType.PU).getReal()-5.7286653)<0.0001);
  		assertTrue( Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.0766519)<0.0001);
  		
  		//System.out.println(AclfOut.lfResultsBusStyle(net));
	}
}

