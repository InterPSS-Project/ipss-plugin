 /*
  * @(#)AcscSampleTest.java   
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

package com.interpss.pssl.test.acsc;

import static org.ieee.odm.ODMObjectFactory.OdmObjFactory;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.ieee.odm.model.ext.ipss.IpssScenarioHelper;
import org.ieee.odm.schema.AcscBusFaultXmlType;
import org.ieee.odm.schema.AcscFaultAnalysisXmlType;
import org.ieee.odm.schema.AcscFaultCategoryEnumType;
import org.ieee.odm.schema.AcscFaultTypeEnumType;
import org.interpss.numeric.util.TestUtilFunc;
import org.interpss.pssl.plugin.cmd.AcscDslRunner;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.SimpleFaultAlgorithm;
import com.interpss.core.datatype.IFaultResult;
import com.interpss.pssl.test.BaseTestSetup;
import com.interpss.simu.util.sample.SampleCases;

public class AcscSampleTest extends BaseTestSetup {
	@Test
	public void sampleTest() throws InterpssException {
  		AcscNetwork faultNet = CoreObjectFactory.createAcscNetwork();
		SampleCases.load_SC_5BusSystem(faultNet);
		//System.out.println(faultNet.net2String());

  		assertTrue((faultNet.getBusList().size() == 5 && faultNet.getBranchList().size() == 5));
  		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
  		//System.out.println(fault.toString(faultBus.getBaseVoltage(), faultNet.getBaseKva()));
		/*
		 fault amps(1): (  0.0000 + j 32.57143) pu
		 fault amps(2): (  0.0000 + j  0.0000) pu
		 fault amps(0): (  0.0000 + j  0.0000) pu
		 */
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 
	  			0.0, 0.0, 0.0, 32.57142857157701, 0.0, 0.0) );
		
		//System.out.println(AcscOut.faultResult2String(faultNet));
	}

	@Test
	public void dslTest()  throws InterpssException  {
  		AcscNetwork faultNet = CoreObjectFactory.createAcscNetwork();
		SampleCases.load_SC_5BusSystem(faultNet);
		//System.out.println(faultNet.net2String());

  		assertTrue((faultNet.getBusList().size() == 5 && faultNet.getBranchList().size() == 5));
  		
  		AcscFaultAnalysisXmlType acscCaseXml = createCase();
  		IFaultResult result = ((AcscDslRunner) new AcscDslRunner().setNetwork(faultNet)).runAcsc(acscCaseXml);
		
	  	assertTrue(TestUtilFunc.compare(result.getSCCurrent_012(),
	  			0.0, 0.0, 0.0, 32.57142857157701, 0.0, 0.0) );
	}
	
	private AcscFaultAnalysisXmlType createCase() {
		IpssScenarioHelper helper = new IpssScenarioHelper(new AcscModelParser());
		AcscFaultAnalysisXmlType faultCaseXml = helper.getAcscFaultAnalysis();
		
		AcscBusFaultXmlType busFault = helper.createAcscBusFault();
		faultCaseXml.setAcscFault(OdmObjFactory.createAcscBusFault(busFault));
		
		busFault.getRefBus().setBusId("2");
		busFault.setFaultType(AcscFaultTypeEnumType.BUS_FAULT);
		busFault.setFaultCategory(AcscFaultCategoryEnumType.FAULT_3_PHASE);

		return faultCaseXml;
	}
}

