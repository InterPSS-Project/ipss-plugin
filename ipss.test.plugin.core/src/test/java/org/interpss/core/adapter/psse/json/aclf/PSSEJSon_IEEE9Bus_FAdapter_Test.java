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
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.bean.PSSESchema;
import org.ieee.odm.adapter.psse.json.PSSEJSonAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.export.psse.PSSEJSonBusUpdater;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class PSSEJSon_IEEE9Bus_FAdapter_Test extends CorePluginTestSetup { 
	@Test
	public void testJSonExport() throws Exception {
	    IODMAdapter adapter = new PSSEJSonAdapter();
	    assertTrue(adapter.parseInputFile("testdata/adpter/psse/json/ieee9.rawx"));
	    
	    AclfModelParser parser = (AclfModelParser)adapter.getModel();
	    //parser.stdout();
	    
	    SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
	    if (!new ODMAclfParserMapper()
	                .map2Model(parser, simuCtx)) {
	        System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
	   	 return;
	    }		
	    
	    AclfNetwork net = simuCtx.getAclfNet();
		
		PSSESchema psseJson = parser.getJsonObject();
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
  		
  		FileUtil.writeText2File("testdata/adpter/psse/json/ieee9_export.rawx", psseJson.toString());
	}
	
	@Test
	public void testJSonImport() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet("testdata/adpter/psse/json/ieee9_export.rawx")
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


