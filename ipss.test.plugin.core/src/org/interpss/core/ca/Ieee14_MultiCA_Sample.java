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

package org.interpss.core.ca;

import java.util.function.BiConsumer;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BaseContingency;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.BusbarOutageContingency;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.MultiOutageContingency;
import com.interpss.core.aclf.contingency.Xfr3WOutageContingency;
import com.interpss.core.dclf.DclfAlgorithm;
import com.interpss.core.dclf.common.ReferenceBusException;

public class Ieee14_MultiCA_Sample {
	static int points = 10;
	/*
	 * Define a contingency processing function
	 */
	static BiConsumer<BaseContingency, DclfAlgorithm>	funcContProcessor = (cont, algo) -> {
		DclfAlgorithmDSL algoDsl_i = IpssDclf.copyAlgorithm(algo);
		if (cont instanceof Contingency) {
			algoDsl_i.contingencyAanlysis((Contingency)cont, (contBranch, postContFlow) -> {
				// here we define a function to process the contingency analysis results
				if (points < 100 && contBranch.getId().equals("Bus1->Bus2(1)"))
					System.out.println("CA: " + cont.getId() + " Branch " + contBranch.getId() + " should be 150.39205, error: " + 
		       		          Math.abs(postContFlow - 150.3920508725032));	
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
	       		if (points < 100 && contBranch.getId().equals("Bus2->Bus5(1)"))
	       			System.out.println("CA: " + cont.getId() + " Branch " + contBranch.getId() + " should be 69.08805, error: " + 
	       		          Math.abs(postContFlow - 69.08805));
			});
		}
	};		
	
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		sample();
		
		sampleParallel();
	}
	
	public static void sample() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = getSampleNet();
        
		// run Dclf
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();
		
		createContingencies(net, 10);
		createMultiOutageContingencies(net, 10);
		
		algoDsl.setRefBus("Bus14");  // for the multiple outage analysis case
		
		System.out.println("Total CA: " + points*2);
		
		net.getContingencyList().forEach(cont -> {
			funcContProcessor.accept(cont, algoDsl.algo());
		});
	}

	public static void sampleParallel() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = getSampleNet();
        
		// run Dclf
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();
		
		createContingencies(net, 100000);
		
		/* To prepare for parallel CA, we need to compute all bus P-Angle sensitivity
		 * used in the parallel CA, before starting actual CA analysis. We need to make 
		 * sure that there is no bus id duplication in the bus list, since we compute the
		 * sensitivity in parallel
		 */
		algoDsl.getAlgorithm().setCacheSensitivity(true);
		net.getBusList().stream().parallel().forEach(bus -> {
			try {
				if (!bus.isRefBus())
					algoDsl.getAlgorithm().getDclfSolver().getSenPAngle(bus.getId());
						// the P-Angle sensitivity is cached at this point
			} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
				e.printStackTrace();
			}
		});
		
		System.out.println("\nTotal CA: " + points);
		
		System.out.println("\nStart seq CA ----> ");
		
	  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());		
		net.getContingencyList().stream().forEach(cont -> {
			funcContProcessor.accept(cont, algoDsl.algo());
		});
		timer.logStd("Total time: ");
		
		System.out.println("\nStart paralle CA ----> ");
		
	  	timer.start();		
		net.getContingencyList().stream().parallel().forEach(cont -> {
			funcContProcessor.accept(cont, algoDsl.algo());
		});
		timer.logStd("Total time: ");		
	}
	
	private static void createContingencies(AclfNetwork net, int n) {
		points = n;
		for ( int i = 0; i < n; i++)
			CoreObjectFactory.createContingency("CA"+i, "Bus5->Bus6(1)", BranchOutageType.OPEN, net);
	}

	private static void createMultiOutageContingencies(AclfNetwork net, int n) {
		points = n;
		for ( int i = 0; i < n; i++)
			CoreObjectFactory.createMultiOutageContingency("MCA"+i, new String[] {"Bus1->Bus5(1)", "Bus3->Bus4(1)", "Bus6->Bus11(1)"}, 
				                     BranchOutageType.OPEN, net);
	}
	
	private static AclfNetwork getSampleNet() throws InterpssException {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/ieee_format/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		// set branch mva rating, since it is not defined in the input file.
		for (AclfBranch branch : net.getBranchList()) {
			branch.setRatingMva1(100.0);
		}
		
		return net;
	}	
}

