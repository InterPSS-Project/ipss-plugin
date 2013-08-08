 /*
  * @(#)IEEE14_3WXfrTest.java   
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
  * @Date 05/15/2013
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.aclf;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginObjFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.Xfr3WAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE14_3WXfrTest extends CorePluginTestSetup {
	@Test 
	public void bus14testCase() throws Exception {
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();		
		
		net.removeBranch("Bus4->Bus7(1)");
		net.removeBranch("Bus4->Bus9(1)");
		net.removeBranch("Bus7->Bus9(1)");
		
		Xfr3WAdapter xfr3W = CoreObjectFactory.createAclf3WXfr("Bus4", "Bus7", "Bus9", net);
		
		xfr3W.setZ(new Complex(0.0, 0.01), new Complex(0.0, 0.03), new Complex(0.0, 0.01));
		
		//System.out.println(net.net2String());
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
 		//System.out.println(AclfOutFunc.loadFlowSummary(net));
	  	
  		assertTrue(net.isLfConverged());		
	}
}

