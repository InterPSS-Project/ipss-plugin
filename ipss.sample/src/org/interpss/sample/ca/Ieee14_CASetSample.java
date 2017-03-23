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

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.BusbarOutageContingency;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.MultiOutageContingency;
import com.interpss.core.aclf.contingency.Xfr3WOutageContingency;
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
		
		createContingencies(net);
		algoDsl.setRefBus("Bus14");
		
		net.getContingencyList().stream().forEach(cont -> {
			if (cont instanceof Contingency) {
				algoDsl.processContingency((Contingency)cont, (contBranch, postContFlow) -> {
					// a function to handle the CA results
					double preFlow = contBranch.getDclfFlow()*baseMva;
					// Bus1->Bus2(1), 147.88057464425773, 150.3920508725032
					if (contBranch.getId().equals("Bus1->Bus2(1)"))
						System.out.println("preFlow, postFlow: " + preFlow + ", " + postContFlow);			
				});
			}
			else if (cont instanceof MultiOutageContingency) {
				algoDsl.processMultiOutageContingency((MultiOutageContingency)cont, (contBranch, postContFlow) -> {
		       		if (contBranch.getId().equals("Bus1->Bus5(1)") ||
		       				contBranch.getId().equals("Bus3->Bus4(1)")||
		       				contBranch.getId().equals("Bus6->Bus11(1)"))
		       			System.out.println("Branch " + contBranch.getId() + " should be 0.0: " + postContFlow);
		       		else if (contBranch.getId().equals("Bus2->Bus5(1)"))
		       			System.out.println("Branch " + contBranch.getId() + " should be 69.08805: " + postContFlow);
		       		else if (contBranch.getId().equals("Bus6->Bus13(1)"))
		       			System.out.println("Branch " + contBranch.getId() + " should be 17.88058: " + postContFlow);					
				});
			}
			else if (cont instanceof BusbarOutageContingency) {
				
			}
			else if (cont instanceof Xfr3WOutageContingency) {
				
			}
		});
	}
	
	private static void createContingencies(AclfNetwork net) {
		CoreObjectFactory.createContingency("CA1", "Bus5->Bus6(1)", BranchOutageType.OPEN, net);
		CoreObjectFactory.createContingency("CA2", "Bus5->Bus6(1)", BranchOutageType.OPEN, net);
		
		CoreObjectFactory.createMultiOutageContingency("MCA1", new String[] {"Bus1->Bus5(1)", "Bus3->Bus4(1)", "Bus6->Bus11(1)"}, 
				                     BranchOutageType.OPEN, net);
	}
}

