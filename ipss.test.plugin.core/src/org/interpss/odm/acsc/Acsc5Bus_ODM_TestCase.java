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

package org.interpss.odm.acsc;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.mapper.odm.ODMAcscDataMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.TestUtilFunc;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.QA.compare.aclf.AclfNetModelComparator;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.ScBusVoltageType;
import com.interpss.core.algo.SimpleFaultAlgorithm;
import com.interpss.pssl.simu.IpssAclf;
import com.interpss.simu.util.sample.SampleCases;

public class Acsc5Bus_ODM_TestCase extends CorePluginTestSetup {
	@Test
	public void testCaseNoLF() throws Exception {
		File file = new File("testdata/ieee_odm/ODM_AcscNoLF_5Bus.xml");
		AcscModelParser parser = new AcscModelParser();
		parser.parse(new FileInputStream(file));	
		//System.out.println(parser.toXmlDoc());
		
		AcscNetwork faultNet = new ODMAcscDataMapper().map2Model(parser).getAcscNet();
		//System.out.println(faultNet.net2String());
		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
  		//System.out.println(fault.getFaultResult().getSCCurrent_012());
  		// 0.0000 + j0.0000  -0.7531 + j29.05407  0.0000 + j0.0000
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 
	  			0.0, 0.0, -0.7531, 29.05407, 0.0, 0.0) );
	}

	@Test
	public void testCaseLF() throws Exception {
		IpssLogger.ipssLogger.setLevel(Level.INFO);
		
		File file = new File("testdata/ieee_odm/ODM_Acsc_5Bus.xml");
		AcscModelParser parser = new AcscModelParser();
		parser.parse(new FileInputStream(file));		
		//System.out.println(parser.toXmlDoc());
		AcscNetwork faultNet = new ODMAcscDataMapper().map2Model(parser).getAcscNet();
		//System.out.println(faultNet.net2String());

		///////////////////compare aclfNet ///////////////////////////
		
  		AclfNetwork baseNet = CoreObjectFactory.createAclfNetwork();
  		SampleCases.IdPrefix = "Bus";
		SampleCases.load_LF_5BusSystem(baseNet);
  		SampleCases.IdPrefix = "";
		
		AclfNetModelComparator comparator = new AclfNetModelComparator();
		if (!comparator.compare(baseNet, faultNet))
			System.out.println(comparator.getMsg());
		
		/////////////////////////////////////////////////////////////
		
		IpssAclf.createAclfAlgo(faultNet)                        
        		.lfMethod(AclfMethod.NR)
        		.tolerance(0.0001, UnitType.PU)
        		.runLoadflow(); 	
	  	//System.out.println(AclfOutFunc.loadFlowSummary(faultNet));

	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
	  	algo.setScBusVoltage(ScBusVoltageType.LOADFLOW_VOLT);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
  		//System.out.println(fault.getFaultResult().getSCCurrent_012());
  		// 0.0000 + j0.0000  -13.65224 + j30.20264  0.0000 + j0.0000
	  	
	  	/* Note[07/12/2013]: 
	  	 * If the load is considered in the SC when calculating scYii, then
	  	 * iPU_012 = 0.0000 + j0.0000  -13.47708 + j30.27969  0.0000 + j0.0000
	  	 */
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 
	  			0.0, 0.0, -13.65224, 30.20264, 0.0, 0.0) );		
	}
}

