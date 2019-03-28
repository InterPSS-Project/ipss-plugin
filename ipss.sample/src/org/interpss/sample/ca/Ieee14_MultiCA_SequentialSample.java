 /*
  * @(#)Ieee14_MultiCATest.java   
  *
  * Copyright (C) 2006-2015 www.interpss.org
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
  * @Date 03/15/2017
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.ca;

import org.interpss.IpssCorePlugin;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.core.DclfObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.dclf.DclfAlgorithm;

public class Ieee14_MultiCA_SequentialSample {
	
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		dclfDSLImpl();
		
		monadImpl();
	}
	
	public static void dclfDSLImpl() throws Exception {
		AclfNetwork net = Ieee14_CA_Utils.getSampleNet();
        
		// run Dclf
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();
		
		Ieee14_CA_Utils.createSampleContingencies(net, 10);
		Ieee14_CA_Utils.createSampleMultiOutageContingencies(net, 10);
		
		algoDsl.setRefBus("Bus14");  // for the multiple outage analysis case
		
		System.out.println("DSLImpl Total CA: " + Ieee14_CA_Utils.points*2);
		
		net.getContingencyList().forEach(cont -> {
			Ieee14_CA_Utils.funcContProcessor.accept(cont, algoDsl.algo());
		});
	}
	 
	public static void monadImpl() throws Exception {
		AclfNetwork net = Ieee14_CA_Utils.getSampleNet();
        
		// run Dclf
		DclfAlgorithm dclfAlgo = DclfObjectFactory.createDclfAlgorithm(net);
		dclfAlgo.calculateDclf();
		
		Ieee14_CA_Utils.createSampleContingencies(net, 10);
		
		dclfAlgo.setRefBus("Bus14");  // for the multiple outage analysis case
		
		System.out.println("Monad Total CA: " + Ieee14_CA_Utils.points);
		
		net.getContingencyList().forEach(contingency -> {
			DclfObjectFactory.createContingencyAnalysis()
				.of(dclfAlgo, ((Contingency)contingency).getOutageBranch())
				/*
				 * Call the ca() method by passing a CA result handling function.
				 */
				.ca((contBranch, postFlowMW) -> {
					if (contBranch.getId().equals("Bus1->Bus2(1)")) {
						//System.out.println("postContFlow: " + postContFlow);
			       		System.out.println("CA: " + contingency.getId() + " Branch " + contBranch.getId() + " should be 105.79698, error: " + 
			       		          Math.abs(postFlowMW - 105.79697726834374));	
					}
		       });
		});
	}
}

