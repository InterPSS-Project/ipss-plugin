 /*
  * @(#)SampleLoadflow.java   
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

package org.interpss.sample.mvel;

import java.util.HashMap;
import java.util.Map;

import org.interpss.IpssCorePlugin;
import org.interpss.mvel.AclfNetMvelExprEvaluator;
import org.interpss.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;


public class SampleMvelExpr {
	public static void main(String args[]) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
		
		AclfNetMvelExprEvaluator eval = new AclfNetMvelExprEvaluator(net);
		String id = eval.evalMvelExpression("aclfnet.id");
		System.out.println("Net id: " + id);
		
		eval.evalMvelExpression("aclfnet.setId('New Id')");
		id = eval.evalMvelExpression("aclfnet.id");
		System.out.println("Net id: " + id);
		
		Map<String, Object> vars = new HashMap<>();
		vars.put("id", "New Id1");
		eval.evalMvelExpression("aclfnet.setId(id)", vars);
		id = eval.evalMvelExpression("aclfnet.id");
		System.out.println("Net id1: " + id);
	}	
}
