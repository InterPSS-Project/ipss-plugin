 /*
  * @(#)Test_IEEE14.java   
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

package org.interpss.core.adapter.internal;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.util.AclfNetJsonComparator;
import org.interpss.util.FileUtil;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.state.aclf.AclfNetworkState;

public class IEEE14JsonCompareTest extends CorePluginTestSetup {
	@Test
	public void test() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
					.load("testData/ipssdata/Ieee14.ipssdat")
					.getAclfNet();	
		
		String jsonFile = "testdata/json/inter_format/ieee14Bus.json";
		
		// output aclfNet state to json file
		//FileUtil.writeText2File(jsonFile, new AclfNetworkState(aclfNet).toString());
		
		assertTrue("", new AclfNetJsonComparator("Internal format ieee14Bus")
							.compareJson(aclfNet, new File(jsonFile)));
	}
}

