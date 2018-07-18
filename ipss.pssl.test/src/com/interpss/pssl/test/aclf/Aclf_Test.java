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

package com.interpss.pssl.test.aclf;

import static org.ieee.odm.ODMObjectFactory.OdmObjFactory;
import static org.interpss.CorePluginFunction.aclfResultSummary;
import static org.interpss.pssl.plugin.IpssAdapter.importNet;
import static org.interpss.pssl.plugin.IpssAdapter.FileFormat.IEEECommonFormat;
import static org.interpss.pssl.simu.IpssAclf.createAclfAlgo;
import static org.junit.Assert.assertTrue;

import org.ieee.odm.model.aclf.AclfModelParser;
import org.ieee.odm.model.ext.ipss.IpssScenarioHelper;
import org.ieee.odm.schema.IpssAclfAlgorithmXmlType;
import org.ieee.odm.schema.LfMethodEnumType;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.cmd.AclfDslRunner;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.pssl.test.BaseTestSetup;

public class Aclf_Test extends BaseTestSetup {
	@Test
	public void lfTest()  throws PSSLException, InterpssException {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IEEECommonFormat)
				.load()
				.getImportedObj();	
		
	  	createAclfAlgo(net)
	  			.lfMethod(AclfMethod.NR)
	  			.nonDivergent(true)
	  			.runLoadflow();	
	  	
	  	System.out.println(aclfResultSummary.apply(net));
	  	
	  	assertTrue(net.isLfConverged());
	}	
	
	// AclfAlgorithmXmlType
	
	@Test
	public void lfXmlTest()  throws PSSLException, InterpssException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
		
		IpssScenarioHelper helper = new IpssScenarioHelper(new AclfModelParser());
		IpssAclfAlgorithmXmlType algoXml = OdmObjFactory.createIpssAclfAlgorithmXmlType();
		
		algoXml.setLfMethod(LfMethodEnumType.NR);
		algoXml.setNonDivergent(true);
		
		((AclfDslRunner) new AclfDslRunner().setNetwork(net)).runAclf(algoXml);
	  	
	  	assertTrue(net.isLfConverged());
	}	
	
}

