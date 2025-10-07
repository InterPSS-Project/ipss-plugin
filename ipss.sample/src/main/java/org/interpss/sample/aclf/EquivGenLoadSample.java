 /*
  * @(#)EquivGenLoadSample.java   
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

package org.interpss.sample.aclf;

import static org.junit.Assert.assertTrue;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleTestingCases;


public class EquivGenLoadSample {
	public static void main(String args[]) throws InterpssException {
		IpssCorePlugin.init();
		
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
  		SampleTestingCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
		System.out.println("Largest mismatch-1 " + net.maxMismatch(AclfMethodType.NR)); 
		
  		// init Equiv Gen/Load configuration
		net.setHasEquivGenLoad(true);
		net.setEquivGenLoadCache(new Hashtable<>());
		
  		/*
  		 * The power flow on the branch is replaced by the Equiv Gen/Load concept
  		 */
  		
  		AclfBranch branch = net.getBranch("2->3(1)");
  		net.getEquivGenLoadCache().put("2", branch.powerFrom2To());
  		net.getEquivGenLoadCache().put("3", branch.powerTo2From());
  		
  		branch.setStatus(false);
  		
  		Complex maxMismatch = new Complex(0.0, 0.0);
  		for (AclfBus bus : net.getBusList()) {
			Complex mismatch = bus.mismatch(AclfMethodType.NR);
			// adjust the bus mismatch using the Equiv Gen/Load
			Complex equivGenLoad = (Complex)net.getEquivGenLoadCache().get(bus.getId());
			if (equivGenLoad != null)
				mismatch = mismatch.subtract(equivGenLoad);
			
			if ( mismatch.abs() > 0.0001)
				System.out.println("Bus with large mismatch, " + bus.getId()); 
			
			if (mismatch.abs() > maxMismatch.abs())
				maxMismatch = mismatch;
  		};
  		
		System.out.println("Largest mismatch-2 " + maxMismatch); 
	}	
}
