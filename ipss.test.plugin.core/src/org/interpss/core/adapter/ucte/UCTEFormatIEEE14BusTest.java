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

package org.interpss.core.adapter.ucte;

import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.Test;

import com.interpss.core.aclf.AclfNetwork;

public class UCTEFormatIEEE14BusTest extends CorePluginTestSetup {
	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.UCTE)
				.load("testData/ucte/ieee14.uct")
				.getAclfNet();
		
//		IpssFileAdapter adapter = PluginSpringCtx.getCustomFileAdapter("uct");
//		SimuContext simuCtx = adapter.load("testData/ucte/ieee14.uct");
//  		//System.out.println(simuCtx.getAclfNet().net2String());
	}
}

