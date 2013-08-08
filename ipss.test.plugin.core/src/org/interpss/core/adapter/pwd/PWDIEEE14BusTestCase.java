 /*
  * @(#)CR_UserTestCases.java   
  *
  * Copyright (C) 2008 www.interpss.org
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
  * @Date 02/15/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.adapter.pwd;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileReader;

import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.ieee.odm.ODMFileFormatEnum;
import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.spring.CorePluginSpringFactory;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PWDIEEE14BusTestCase extends CorePluginTestSetup {
	//@Test
	public void odmAdapterTestCase() throws Exception {
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PWD)
				.load("testData/pwd/ieee14.AUX")
				.getAclfNet();	
		
		System.out.println(net.net2String());

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
		
	  	System.out.println(AclfOutFunc.loadFlowSummary(net));
	  	
  		assertTrue(net.isLfConverged());		
  		//AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		//AclfSwingBus swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU).getImaginary());
  		//assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-1.0586)<0.01);
  		//assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.4366)<0.01);
	}	
	@Test
	public void testCaseStringInput() throws Exception{
		String file = "testData/pwd/ieee14.AUX";
  		IODMAdapter adapter = ODMObjectFactory
				.createODMAdapter(ODMFileFormatEnum.PWD);

  		BufferedReader br = new BufferedReader(new FileReader(file));
  		String everything = null;
  	    try {
  	        StringBuilder sb = new StringBuilder();
  	        String line = br.readLine();
  	        while (line != null) {
  	            sb.append(line);
  	            sb.append("\n");
  	            line = br.readLine();
  	        }
  	        everything = sb.toString();
  	    } finally {
  	        br.close();
  	    }					

		//this.log.info("casedata.inputDataFile: " + casedata.inputDataFile);
		adapter.parseFileContent(everything);		
		
		AclfNetwork aclfNet = CorePluginSpringFactory
				.getOdm2AclfParserMapper(ODMAclfNetMapper.XfrBranchModel.InterPSS)
				.map2Model((AclfModelParser) adapter.getModel())
				.getAclfNet();
		
	}
}

