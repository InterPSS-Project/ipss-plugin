 /*
  * @(#)PSSEResultProcessSample.java   
  *
  * Copyright (C) 2008 www.interpss.org
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
  * @Date 02/15/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.QA.test.compare;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.QA.compare.impl.PSSEResultComparator;
import org.interpss.QA.rfile.BaseResultFileProcessor;
import org.interpss.QA.rfile.QAFileReader;
import org.interpss.QA.rfile.aclf.PSSEResultFileProcessor;
import org.interpss.QA.test.QATestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PSSEResultProcessSample extends QATestSetup {
	@Test
	public void compareNetModel() throws Exception {
		// step-1 : load the study case 
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_30)
				.load("testData/psse/PSSETestFromCR.raw")
				.getAclfNet();	
		
		// Step-2 : read PSSE result file of the study case
		PSSEResultFileProcessor proc = new PSSEResultFileProcessor(" ", BaseResultFileProcessor.Version.PSSE_ALL);
		new QAFileReader("testData/psse/PSSETestFromCR_raw_01252011.txt")
				.processFile(proc);		
		System.out.println("Total bus: " + proc.getTotalBus());
		assertTrue(proc.getTotalBus() == 115);

		// Step-3 : Network model comparison
		
		PSSEResultComparator comp = new PSSEResultComparator(net, proc.getQAResultSet());

		// compare network element
		comp.compareNetElement();
		if (comp.getErrMsgList().size() > 0) {
			System.out.println(comp.getErrMsgList().toString());
			//FileUtil.writeText2File("output/neiso/out.txt", comp.getErrMsgList().toString());
		}		
		
		// use the voltage (mag and angle) in the result file to calculate 
		// max mismatch bus in the network, excluding buses connecting to branches
		// with small Z (z <= 0.005 pu)
		AclfBus bus = comp.getLargestMisBus(0.0);
		
		// output mis info and InterPSS object model bus LF info
		System.out.println(comp.busInfo(bus));
	}

	@Test
	public void compareAclfResuslt() throws Exception {
		// step-1 : load the study case and run Loadflow 
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_30)
				.load("testData/psse/PSSETestFromCR.raw")
				.getAclfNet();	
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	assertTrue(net.isLfConverged());
		
		// Step-2 : read PSSE result file of the study case
		PSSEResultFileProcessor proc = new PSSEResultFileProcessor(" ", BaseResultFileProcessor.Version.PSSE_ALL);
		new QAFileReader("testData/psse/PSSETestFromCR_raw_01252011.txt")
				.processFile(proc);		
		System.out.println("Total bus: " + proc.getTotalBus());
		assertTrue(proc.getTotalBus() == 115);

		// Step-3 : Compare LF result
		
		PSSEResultComparator comp = new PSSEResultComparator(net, proc.getQAResultSet());
		comp.compAclfResult();
		if (comp.getErrMsgList().size() > 0) {
			System.out.println(comp.getErrMsgList().toString());
			//FileUtil.writeText2File("output/neiso/out.txt", comp.getErrMsgList().toString());
		}		
	}	
}

