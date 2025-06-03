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

import java.io.File;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.util.AclfNetJsonComparator;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.aclf.AclfNetwork;

public class IEEE14JsonCompareTest extends CorePluginTestSetup {
	@Test
	public void test() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
					.load("testData/ipssdata/Ieee14.ipssdat")
					.getAclfNet();	
		
		String jsonFile = "testData/json/inter_format/ieee14Bus.json";
		
		// output aclfNet state to json file
		//FileUtil.writeText2File(jsonFile, new AclfNetworkState(aclfNet).toString());
		
		// compare the json file with the aclfNet
		assertTrue("", new AclfNetJsonComparator("Internal format ieee14Bus")
							.compareJson(aclfNet, new File(jsonFile)));
	}
}

