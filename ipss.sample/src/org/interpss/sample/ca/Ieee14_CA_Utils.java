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

import java.util.function.BiConsumer;

import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BaseContingency;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.BusbarOutageContingency;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.MultiOutageContingency;
import com.interpss.core.aclf.contingency.Xfr3WOutageContingency;
import com.interpss.core.dclf.DclfAlgorithm;

public class Ieee14_CA_Utils {
	static int points = 10;
	
	public static AclfNetwork getSampleNet() throws InterpssException {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		// set branch mva rating, since it is not defined in the input file.
		for (AclfBranch branch : net.getBranchList()) {
			branch.setRatingMva1(100.0);
		}
		
		return net;
	}
	
	/**
	 * Since the purpose of these set of samples is to demonstrate parallel processing, therefore,
	 * a list of n same contingencies are created. 
	 * 
	 * @param net
	 * @param n
	 */
	public static void createSampleContingencies(AclfNetwork net, int n) {
		Ieee14_CA_Utils.points = n;
		for ( int i = 0; i < n; i++)
			CoreObjectFactory.createContingency("CA"+i, "Bus5->Bus6(1)", BranchOutageType.OPEN, net);
	}

	/**
	 * Since the purpose of these set of samples is to demonstrate parallel processing, therefore,
	 * a list of n same MultiOutage contingencies are created. 
	 * 
	 * @param net
	 * @param n
	 */
	public static void createSampleMultiOutageContingencies(AclfNetwork net, int n) {
		Ieee14_CA_Utils.points = n;
		for ( int i = 0; i < n; i++)
			CoreObjectFactory.createMultiOutageContingency("MCA"+i, new String[] {"Bus1->Bus5(1)", "Bus3->Bus4(1)", "Bus6->Bus11(1)"}, 
				                     BranchOutageType.OPEN, net);
	}
	
	/*
	 * Define a contingency processing function
	 */
	static BiConsumer<BaseContingency, DclfAlgorithm>	funcContProcessor = (cont, algo) -> {
		/*
		 * create a wrapper (Monad) of the DclfAlgo for analysis of the contingency 
		 */
		DclfAlgorithmDSL algoDsl_i = IpssDclf.copyAlgorithm(algo);
		
		if (cont instanceof Contingency) {
			algoDsl_i.ca((Contingency)cont, (contBranch, postContFlow) -> {
				// here we define a function to process the contingency analysis results
				if (points < 100 && contBranch.getId().equals("Bus1->Bus2(1)")) {
					//System.out.println("postContFlow: " + postContFlow);
		       		System.out.println("CA: " + cont.getId() + " Branch " + contBranch.getId() + " should be 105.79698, error: " + 
		       		          Math.abs(postContFlow - 105.79697726834374));	
				}
			});
		}
		/*
		 * since BusbarOutageContingency and Xfr3WOutageContingency are subclass of MultiOutageContingency,
		 * make sure they are processed before MultiOutageContingency.
		 */
		else if (cont instanceof BusbarOutageContingency) {
			algoDsl_i.busbarOutageContingencyAanlysis((BusbarOutageContingency)cont, (contBranch, postContFlow) -> {
				// here we define a function to process the contingency analysis results
			});
			
		}
		else if (cont instanceof Xfr3WOutageContingency) {
			algoDsl_i.xfr3WOutageContingencyAanlysis((Xfr3WOutageContingency)cont, (contBranch, postContFlow) -> {
				// here we define a function to process the contingency analysis results
			});
			
		}
		else if (cont instanceof MultiOutageContingency) {
			algoDsl_i.multiOutageContingencyAanlysis((MultiOutageContingency)cont, (contBranch, postContFlow) -> {
				// here we define a function to process the contingency analysis results
	       		if (points < 100 && contBranch.getId().equals("Bus2->Bus5(1)")) {
					//System.out.println("postContFlow: " + postContFlow);
	       			System.out.println("CA: " + cont.getId() + " Branch " + contBranch.getId() + " should be 32.48676, error: " + 
	       		          Math.abs(postContFlow - 32.4867589525026));
	       		}
			});
		}
	};		
}

