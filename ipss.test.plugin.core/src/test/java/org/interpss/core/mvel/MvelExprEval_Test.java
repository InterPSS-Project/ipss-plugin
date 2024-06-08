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

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.mvel.AclfNetMvelExprEvaluator;
import org.junit.Test;

import com.interpss.core.aclf.AclfNetwork;

public class MvelExprEval_Test extends CorePluginTestSetup {
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
