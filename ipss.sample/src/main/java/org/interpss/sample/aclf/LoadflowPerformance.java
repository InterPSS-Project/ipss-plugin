 /*
  * @(#)LoadflowPerformance.java   
  *
  * Copyright (C) 2010 www.interpss.org
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
  * @Date 07/15/2010
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.aclf;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.util.PerformanceTimer;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;


public class LoadflowPerformance {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();

	  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());

	  	/*
	  	 * time loading data, create ODM and InterPSS Simulation object
	  	 */
	  	timer.start();
		AclfNetwork adjNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("ipss-plugin/ipss.sample/testData/UCTE_2000_WinterOffPeak.ieee")
				.getAclfNet();		  	
	  	timer.logStd("Time for loading the case: ");

		/*
		 * time running a full NR loadflow
		 */
		timer.start();
	  	LoadflowAlgorithm algoLF = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(adjNet);
	  	algoLF.setLfMethod(AclfMethodType.NR);
	  	algoLF.loadflow();
	  	timer.logStd("Time for running Loadflow: ");

	  	/*
	  	 * time running a step of NR method
	  	 */
		timer.start();
	  	algoLF.setLfMethod(AclfMethodType.NR_STEP);
	  	algoLF.loadflow();
	  	timer.logStd("Time for running one step NR Loadflow: ");
	}	
}
