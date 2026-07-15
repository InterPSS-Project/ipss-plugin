 /*
  * @(#)IEEE9Bus_Test.java   
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
  * @Author Stephen Hou
  * @Version 1.0
  * @Date 02/01/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.adapter.psse.json.aclf;
 
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileReader;

import org.apache.commons.math3.complex.Complex;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.interpss.fadapter.psse.bean.PSSESchema;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.export.psse.PSSEJSonBusUpdater;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.interpss.util.FileUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PSSEJSon_IEEE9Bus_FAdapter_Test extends CorePluginTestSetup { 
	@Test
	public void testJSonExport() throws Exception {
	    AclfNetwork net = new PSSEJsonDirectParser().parse("testData/adpter/psse/json/ieee9.rawx");
	    
	    // Read PSSESchema separately from the JSON file
	    PSSESchema psseJson;
	    try (FileReader reader = new FileReader("testData/adpter/psse/json/ieee9.rawx")) {
	        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
	        psseJson = new Gson().fromJson(root, PSSESchema.class);
	    }
		//System.out.println("Before Json String:\n" + json.toString());

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	assertTrue(net.maxMismatch(AclfMethodType.PQ).maxMis.abs() < 0.0001);

	  	AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-0.71646)<0.00001);
  		assertTrue(Math.abs(p.getImaginary()-0.27107)<0.00001);
  		
  		/*
  		 * update the bus field/data into the PSSE Json object
  		 */
  		PSSEJSonBusUpdater busUpdater = new PSSEJSonBusUpdater(psseJson.getNetwork().getBus(), net); 
  		busUpdater.update();
  		
  		//System.out.println("After Json String:\n" + json.toString());
  		
  		FileUtil.writeText2File("testData/adpter/psse/json/ieee9_export.rawx", psseJson.toString());
	}
	
	@Test
	public void testJSonImport() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/json/ieee9_export.rawx")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_JSON)
				.load()
				.getImportedObj();
		
		assertTrue(net.maxMismatch(AclfMethodType.PQ).maxMis.abs() < 0.0001);
		
		AclfBus swingBus = net.getBus("Bus1");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-0.71646)<0.00001);
  		assertTrue(Math.abs(p.getImaginary()-0.27107)<0.00001);
	}
}


