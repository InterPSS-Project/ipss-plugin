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

package org.interpss.core.script.gvy;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.script.gvy.AclfNetGvyScriptProcessor;
import org.interpss.script.mvel.AclfNetMvelExprEvaluator;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class GvyScriptEval_Test extends CorePluginTestSetup {
	@Test 
	public void bus14testCase() throws Exception {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();		
		
 		assertTrue("", net.isContributeGenLoadModel());
 		
	  	AclfNetGvyScriptProcessor gvyProcessor = new AclfNetGvyScriptProcessor(net);
    	String groovyCode = "aclfnet.id = 'Modified';";
    	Object result = gvyProcessor.evaluate(groovyCode);
    	//System.out.println("Result: " + result);
    	assertTrue("Net name should be 'Modified'", net.getId().equals("Modified"));
    	
    	groovyCode = "aclfnet.getBus('Bus14').loadP = 0.18;";
		result = gvyProcessor.evaluate(groovyCode);
		//System.out.println("Result: " + result);
		assertTrue("Bus load should be 0.18", NumericUtil.equals(net.getBus("Bus14").getLoadP(), 0.18, 1.0E-4));
		
    	groovyCode = "bus = aclfnet.getBus('Bus14');" +
    				 "load = bus.getContributeLoad('Bus14-L1');" +
    				 "load.loadCP = new Complex(0.18, 0.07);";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);
		assertTrue("Bus contribute load should be 0.18 + j0.07", 
				NumericUtil.equals(net.getBus("Bus14").getContributeLoad("Bus14-L1").getLoadCP(), new Complex(0.18, 0.07), 1.0E-4));
	}
}
