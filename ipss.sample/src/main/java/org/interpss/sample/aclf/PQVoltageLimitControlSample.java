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

import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PQBusLimit;
import com.interpss.core.aclf.adpter.AclfPQGenBusAdapter;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleTestingCases;


public class PQVoltageLimitControlSample {
	public static void main(String args[]) throws IpssNumericException, InterpssException {
		IpssCorePlugin.init();
		
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
  		SampleTestingCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

		net.getBus("1").setLoadCode(AclfLoadCode.CONST_Z);
		
		// change Bus-4 from PV bus to PQ bus
		AclfBus bus = net.getBus("4");
		bus.setGenCode(AclfGenCode.GEN_PQ);
		AclfPQGenBusAdapter pq = bus.toPQBus();
		pq.setGenQ(1.6);
		
		// for the base case, Bus4 : 5 + 1.6, V : 1.06108
		// use bus-4 PQLimit to control bus voltage to 1.05
		PQBusLimit pqLimit = AclfAdjustObjectFactory.createPQBusLimit(bus).get();
		pqLimit.setVLimit(new LimitType(1.05, 0.95), UnitType.PU);
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());

  		AclfBus swingBus = net.getBus("5");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		System.out.println("Swing bus P, Q: " + swing.getGenResults(UnitType.PU));
	}	
}
