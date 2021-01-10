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

import com.interpss.CoreObjectFactory;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;
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
		
		AclfBus bus = net.getBus("1");
		System.out.println("Bus-1 P: " + algo.getBusPower(bus));
		System.out.println("Bus-1 Ang: " + algo.getBusAngle("1"));
		System.out.println("Bus-1 P in to the network: " + algo.getBusPowerInfoNet(bus));
	}	
}

