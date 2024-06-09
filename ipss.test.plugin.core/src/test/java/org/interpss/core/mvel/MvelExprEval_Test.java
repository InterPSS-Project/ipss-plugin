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

package org.interpss.core.mvel;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.mvel.AclfNetMvelExprEvaluator;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class MvelExprEval_Test extends CorePluginTestSetup {
	@Test 
	public void bus14GenLoadAjdustTest() throws Exception {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
		
		//System.out.println("Total gen: " + ComplexFunc.toStr(net.totalGeneration(UnitType.PU)));
		//System.out.println("Total load: " + ComplexFunc.toStr(net.totalLoad(UnitType.PU)));
		/*
		 * Total gen: 2.72392 + j0.7885
           Total load: 2.5900 + j0.7350
		 */
 		//System.out.println(AclfOutFunc.loadFlowSummary(net));
 		assertTrue(NumericUtil.equals(net.totalGeneration(UnitType.PU), new Complex(2.72392, 0.7885), 1.0E-4));
 		assertTrue(NumericUtil.equals(net.totalLoad(UnitType.PU), new Complex(2.5900, 0.7350), 1.0E-4));	  	
	  	
		AclfNetMvelExprEvaluator eval = new AclfNetMvelExprEvaluator(net);
		eval.evalMvelExpression("func.adjustGen(1.1)");
		eval.evalMvelExpression("func.adjustLoad(1.1)");
		
	  	algo.loadflow();
	  	
		//System.out.println("Total gen: " + ComplexFunc.toStr(net.totalGeneration(UnitType.PU)));
		//System.out.println("Total load: " + ComplexFunc.toStr(net.totalLoad(UnitType.PU)));
		/*
		 * Total gen: 3.01546 + j0.84737
           Total load: 2.8490 + j0.8085
		 */
 		//System.out.println(AclfOutFunc.loadFlowSummary(net));
 		assertTrue(NumericUtil.equals(net.totalGeneration(UnitType.PU), new Complex(3.01546, 0.84737), 1.0E-4));
 		assertTrue(NumericUtil.equals(net.totalLoad(UnitType.PU), new Complex(2.8490, 0.8085), 1.0E-4));
	}
	
	@Test 
	public void bus14testCase() throws Exception {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();		
		
		AclfNetMvelExprEvaluator eval = new AclfNetMvelExprEvaluator(net);
		String id = eval.evalMvelExpression("aclfnet.id");
		//System.out.println("Net id: " + id);
 		assertTrue("", id.equals("Base_Case_from_IEEECDF_format"));
 		
		eval.evalMvelExpression("aclfnet.setId('New Id')");
		id = eval.evalMvelExpression("aclfnet.id");
		//System.out.println("Net id: " + id);
 		assertTrue("", id.equals("New Id"));
 		
		Map<String, Object> vars = new HashMap<>();
		vars.put("id", "New Id1");
		eval.evalMvelExpression("aclfnet.setId(id)", vars);
		id = eval.evalMvelExpression("aclfnet.id");
		//System.out.println("Net id1: " + id);
 		assertTrue("", id.equals("New Id1"));
	}
}
