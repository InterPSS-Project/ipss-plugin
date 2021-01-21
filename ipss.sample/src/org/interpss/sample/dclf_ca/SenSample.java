 /*
  * @(#) SenSample.java   
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

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.simu.util.sample.SampleCases;

public class SenSample {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		
		/*
		 * GFS samples
		 */
		double x = algo.calGenShiftFactor("4", net.getBranch("1->3(1)"));    // w.r.p to the Ref Bus
		System.out.println("GSF Gen@Bus-4 on Branch 1->3(1): " + x);
		
		/*
		 * PTDF samples
		 */
		x = algo.pTransferDistFactor("4", net.getBranch("1->2(1)"));         // w.r.p to the Ref Bus
		System.out.println("PTDF Inject@Bus-4 on Branch 1->3(1): " + x);
		
		x = algo.pTransferDistFactor("1", "3", net.getBranch("2->3(1)"));
		System.out.println("PTDF Inject@Bus-1 Withdraw@Bus-3 on Branch 2->3(1): " + x);
	}	
}

