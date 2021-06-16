 /*
  * @(#)DclfSample.java   
  *
  * Copyright (C) 2006-2020 www.interpss.org
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
  * @Date 11/20/2020
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.dclf_ca;

import org.interpss.IpssCorePlugin;
import org.interpss.display.DclfOutFunc;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.simu.util.sample.SampleCases;

public class DclfSample {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

		DclfAlgorithm algo = DclfAlgoObjectFactory.createDclfAlgorithm(net);
		
		// perform DCLF calculation
		algo.calculateDclf();
		
		DclfOutFunc.dclfResults(algo, false /*branchViolation*/);
		
		System.out.println("\nBase Case\n==========");
		
		System.out.println("Bus-1 P: " + algo.getBusPower("1"));
		System.out.println("Bus-1 Ang: " + algo.getBusAngle("1"));
		System.out.println("Bus-1 P in to the network: " + algo.getBusPowerInfoNet("1"));

		System.out.println("Bus-4 P: " + algo.getBusPower("4"));
		System.out.println("Bus-4 Ang: " + algo.getBusAngle("4"));
		System.out.println("Bus-4 P in to the network: " + algo.getBusPowerInfoNet("4"));
		
		System.out.println("Bus-5 P: " + algo.getBusPower("5"));
		System.out.println("Bus-5 Ang: " + algo.getBusAngle("5"));
		System.out.println("Bus-5 P in to the network: " + algo.getBusPowerInfoNet("5"));
		
		System.out.println("\nGen/Load Change Case\n======================");
		
		DclfAlgoBus dclfBus1 = algo.getDclfAlgoBus("1");
		dclfBus1.setLoadAdjust(0.2);
		
		DclfAlgoBus dclfBus4 = algo.getDclfAlgoBus("4");
		dclfBus4.setGenAdjust(0.1);
		
		// perform DCLF calculation
		algo.calculateDclf();
		
		System.out.println("Bus-1 P: " + algo.getBusPower("1"));
		System.out.println("Bus-1 Ang: " + algo.getBusAngle("1"));
		System.out.println("Bus-1 P in to the network: " + algo.getBusPowerInfoNet("1"));

		System.out.println("Bus-4 P: " + algo.getBusPower("4"));
		System.out.println("Bus-4 Ang: " + algo.getBusAngle("4"));
		System.out.println("Bus-4 P in to the network: " + algo.getBusPowerInfoNet("4"));
		
		System.out.println("Bus-5 P: " + algo.getBusPower("5"));
		System.out.println("Bus-5 Ang: " + algo.getBusAngle("5"));
		System.out.println("Bus-5 P in to the network: " + algo.getBusPowerInfoNet("5"));
	}	
}

