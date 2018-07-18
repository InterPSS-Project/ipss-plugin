 /*
  * @(#)ContingencyAnalysisTest.java   
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
  * @Date 01/01/2012
  * 
  *   Revision History
  *   ================
  *
  */

package com.interpss.pssl.test.aclf;

import static org.ieee.odm.ODMObjectFactory.OdmObjFactory;
import static org.ieee.odm.model.base.BaseDataSetter.createApparentPower;

import org.ieee.odm.model.aclf.AclfModelParser;
import org.ieee.odm.model.ext.ipss.IpssScenarioHelper;
import org.ieee.odm.schema.ApparentPowerUnitType;
import org.ieee.odm.schema.ContingencyAnalysisEnumType;
import org.ieee.odm.schema.ContingencyAnalysisXmlType;
import org.ieee.odm.schema.IpssAclfAlgorithmXmlType;
import org.ieee.odm.schema.LfMethodEnumType;
import org.interpss.display.ContingencyOutConfigure;
import org.interpss.display.ContingencyOutFunc;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.cmd.ContingencyDslRunner;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.pssl.test.BaseTestSetup;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.multicase.aclf.ContingencyAnalysis;
import com.interpss.simu.multicase.aclf.ContingencyAnalysisType;

public class ContingencyAnalysisTest extends BaseTestSetup {
	@Test
	public void test()  throws PSSLException, InterpssException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
		
	  	ContingencyAnalysis mscase = SimuObjectFactory.createContingencyAnalysis(SimuCtxType.ACLF_NETWORK, net);
	  	
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.setNonDivergent(true);
		algo.setTolerance(0.001);
		
		mscase.analysis(algo, ContingencyAnalysisType.N1);
		
		// example to customize bus voltage limit and branch rating
		System.out.println(ContingencyOutFunc.securityMargin(mscase, new ContingencyOutConfigure(mscase, net)));	
	}	

	@Test
	public void xmlTest()  throws PSSLException, InterpssException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
		
	  	ContingencyAnalysisXmlType contXml = createAlgo();
	  	StringBuffer buffer = new ContingencyDslRunner(net).runAnalysis(contXml);
		System.out.println(buffer);	
	}	
	
	private ContingencyAnalysisXmlType createAlgo() {
		IpssScenarioHelper helper = new IpssScenarioHelper(new AclfModelParser());
		ContingencyAnalysisXmlType xml = helper.getContingencyAnalysis();
		
		xml.setType(ContingencyAnalysisEnumType.N_1);
		xml.setLimitRunCases(false);
		
		IpssAclfAlgorithmXmlType algo = OdmObjFactory.createIpssAclfAlgorithmXmlType();
		xml.setDefaultAclfAlgorithm(algo);
		
		algo.setLfMethod(LfMethodEnumType.NR);
		algo.setTolerance(createApparentPower(0.001, ApparentPowerUnitType.PU));
		algo.setMaxIterations(20);
		algo.setNonDivergent(true);
		
		return xml;
	}
}

