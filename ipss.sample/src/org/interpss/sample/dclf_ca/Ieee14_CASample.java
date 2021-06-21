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

package org.interpss.sample.dclf_ca;

import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMonitoringBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createOutageBranch;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.algo.dclf.BranchOutageType;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.OutageBranch;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.common.OutageConnectivityException;
import com.interpss.core.common.ReferenceBusException;

public class Ieee14_CASample {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		singleOutageSample();
		
		multipleOutageSample();
	}
	
	public static void singleOutageSample() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
        
		// run Dclf
		ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
		algo.calculateDclf();

		DclfAlgoBranch dclfBranch1 = algo.getDclfAlgoBranch("Bus5->Bus6(1)");
		DclfAlgoBranch dclfBranch2 = algo.getDclfAlgoBranch("Bus4->Bus7(1)");
		DclfAlgoBranch dclfBranch3 = algo.getDclfAlgoBranch("Bus4->Bus9(1)");

		double preFlowSum = dclfBranch1.getDclfFlow() + dclfBranch2.getDclfFlow() + dclfBranch3.getDclfFlow();

		// define a contingency object
		Contingency cont = createContingency("contId");
		
		// define an outage branch
		cont.setOutageBranch(createOutageBranch(algo.getDclfAlgoBranch("Bus5->Bus6(1)"), BranchOutageType.OPEN));

		// define monitoring branches
		cont.addMonitoringBranch(createMonitoringBranch(algo.getDclfAlgoBranch("Bus4->Bus7(1)")));
		cont.addMonitoringBranch(createMonitoringBranch(algo.getDclfAlgoBranch("Bus4->Bus9(1)")));

		// perform CA analysis
		algo.ca(cont);
		
        /*
         * This example checks if the pre-outage-flow on branch 5->6 has been 
         * moved to branch 4->7 and 4->9 
         */
        double postFlowSum = cont.getMonitoringBranch("Bus4->Bus7(1)").calPostFlow() + 
        					 cont.getMonitoringBranch("Bus4->Bus9(1)").calPostFlow();
        System.out.println("The sum should be the same: " + preFlowSum + ", " + postFlowSum);
	} 

	public static void multipleOutageSample() throws InterpssException, ReferenceBusException, IpssNumericException, OutageConnectivityException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();
		
		// run Dclf
		ContingencyAnalysisAlgorithm algo = createContingencyAnalysisAlgorithm(net);
		algo.calculateDclf();

		algo.setRefBus("Bus14");
		
		algo.multiOpenOutgageAnalysis(new OutageBranch[] {
				createOutageBranch(algo.getDclfAlgoBranch("Bus1->Bus5(1)"), BranchOutageType.OPEN),
				createOutageBranch(algo.getDclfAlgoBranch("Bus3->Bus4(1)"), BranchOutageType.OPEN),
				createOutageBranch(algo.getDclfAlgoBranch("Bus6->Bus11(1)"), BranchOutageType.OPEN)
			});
	      
   		/*
   		 *  This example checks post outage flow per IEEE paper "Direct Calculation of Line Outage Distribution Factors" 
   		 */
		algo.getDclfAlgoBranchList().forEach(dclfBranch -> {
			AclfBranch branch = dclfBranch.getBranch();
			double postFlow = dclfBranch.calPostFlow() * net.getBaseMva();
       		if (branch.getId().equals("Bus1->Bus5(1)") ||
       			branch.getId().equals("Bus3->Bus4(1)")||
       			branch.getId().equals("Bus6->Bus11(1)"))
       			System.out.println("Branch " + branch.getId() + " should be 0.0: " + postFlow);
       		else if (branch.getId().equals("Bus2->Bus5(1)"))
       			System.out.println("Branch " + branch.getId() + " should be 69.08805: " + postFlow);
       		else if (branch.getId().equals("Bus6->Bus13(1)"))
       			System.out.println("Branch " + branch.getId() + " should be 17.88058: " + postFlow);
		});
	}
}

