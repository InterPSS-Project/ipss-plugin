 /*
  * @(#)Ieee14_CASample.java   
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
  * @Date 03/15/2015
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.ca;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BaseContingency;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.dclf.common.ReferenceBusException;

public class Ieee14_CASetSample {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		sample();
	}
	
	public static void sample() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = Ieee14_CASample.getSampleNet();
        double baseMva = net.getBaseMva();
        
		// run Dclf
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();
		
		List<BaseContingency> list = new ArrayList<>();
		
		// set single outage branch
		Contingency cont = CoreObjectFactory.createContingency("CA1");
		list.add(cont);

		AclfBranch branch = net.getBranch("Bus5->Bus6(1)");
		OutageBranch outageBranch = CoreObjectFactory.createOutageBranch(branch, BranchOutageType.OPEN); 
		cont.setOutageBranch(outageBranch);
		
		
		processContingency(cont, algoDsl, new BiConsumer<AclfBranch, Double>() {
			@Override public void accept(AclfBranch contBranch, Double postContFlow) {
				double preFlow = contBranch.getDclfFlow()*baseMva;
		        System.out.println("branchId, preFlow, postFlow: " + contBranch.getId() + ", " + preFlow + ", " + postContFlow);			}
		});

		/*
		MultiOutageContingency mcont = CoreObjectFactory.createMultiOutageContingency("CA1");
		list.add(mcont);
*/		
	}
	
	private static void processContingency(Contingency cont, DclfAlgorithmDSL algoDsl, 
							BiConsumer<AclfBranch, Double> resultProcessor) throws ReferenceBusException, PSSLException {
		AclfNetwork net = algoDsl.getAclfNetwork();
        double baseMva = net.getBaseMva();
        
		algoDsl.outageBranch(cont.getOutageBranch().getBranch());
        
        double outBanchPreFlow = cont.getOutageBranch().getBranch().getDclfFlow()*baseMva;		
        for (AclfBranch branch : net.getBranchList()) {
        	double 	preFlow = branch.getDclfFlow()*baseMva,
        			LODF = algoDsl.monitorBranch(branch).lineOutageDFactor(),
           			postFlow = preFlow + LODF * outBanchPreFlow;
        	resultProcessor.accept(branch, postFlow);
		}		
	}
}

