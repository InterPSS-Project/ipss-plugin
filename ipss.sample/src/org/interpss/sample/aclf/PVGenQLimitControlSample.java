 /*
  * @(#)ZBusSample.java   
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
  * @Date 10/15/2010
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.aclf;

import static org.junit.Assert.assertTrue;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleCases;
import com.interpss.spring.CoreCommonSpringFactory;


public class PVGenQLimitControlSample {
	public static void main(String args[]) throws IpssNumericException, InterpssException {
		IpssCorePlugin.init();
		
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

		net.getBus("1").setLoadCode(AclfLoadCode.CONST_Z);
		
		AclfBus bus = net.getBus("4");
		PVBusLimit pvLimit = CoreObjectFactory.createPVBusLimit(bus);
		pvLimit.setQLimit(new LimitType(1.4, 0.0));
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();
//  		System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		AclfBus swingBus = (AclfBus)net.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		System.out.println(swing.getGenResults(UnitType.PU));
	}
}
