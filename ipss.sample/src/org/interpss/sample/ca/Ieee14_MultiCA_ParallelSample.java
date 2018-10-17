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
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.common.ReferenceBusException;

public class Ieee14_MultiCA_ParallelSample {
	
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		AclfNetwork net = Ieee14_CA_Utils.getSampleNet();
        
		// run Dclf
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();
		
		Ieee14_CA_Utils.createSampleContingencies(net, 100000);
		
		/* To prepare for parallel CA, we need to compute all bus P-Angle sensitivity
		 * used in the parallel CA, before starting actual CA analysis. We need to make 
		 * sure that there is no bus id duplication in the bus list, since we compute the
		 * sensitivity in parallel
		 */
		//algoDsl.getAlgorithm().setCacheSensitivity(true);
		net.getBusList().stream().parallel().forEach(bus -> {
			try {
				if (!bus.isRefBus())
					algoDsl.getAlgorithm().getDclfSolver().getSenPAngle(bus.getId());
						// the P-Angle sensitivity is cached at this point
			} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
				e.printStackTrace();
			}
		});
		
		System.out.println("\nTotal CA: " + Ieee14_CA_Utils.points);
		
		System.out.println("\nStart seq CA ----> ");
		
	  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());		
		net.getContingencyList().stream().forEach(cont -> {
			Ieee14_CA_Utils.funcContProcessor.accept(cont, algoDsl.algo());
		});
		timer.logStd("Total time: ");
		
		System.out.println("\nStart paralle CA ----> ");
		
	  	timer.start();		
		net.getContingencyList().stream().parallel().forEach(cont -> {
			Ieee14_CA_Utils.funcContProcessor.accept(cont, algoDsl.algo());
		});
		timer.logStd("Total time: ");	
	}

	public static void sampleParallel() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = Ieee14_CA_Utils.getSampleNet();
        
		// run Dclf
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();
		
		Ieee14_CA_Utils.createSampleContingencies(net, 100000);
		
		/* To prepare for parallel CA, we need to compute all bus P-Angle sensitivity
		 * used in the parallel CA, before starting actual CA analysis. We need to make 
		 * sure that there is no bus id duplication in the bus list, since we compute the
		 * sensitivity in parallel
		 */
		//algoDsl.getAlgorithm().setCacheSensitivity(true);
		net.getBusList().stream().parallel().forEach(bus -> {
			try {
				if (!bus.isRefBus())
					algoDsl.getAlgorithm().getDclfSolver().getSenPAngle(bus.getId());
						// the P-Angle sensitivity is cached at this point
			} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
				e.printStackTrace();
			}
		});
		
		System.out.println("\nTotal CA: " + Ieee14_CA_Utils.points);
		
		System.out.println("\nStart seq CA ----> ");
		
	  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());		
		net.getContingencyList().stream().forEach(cont -> {
			Ieee14_CA_Utils.funcContProcessor.accept(cont, algoDsl.algo());
		});
		timer.logStd("Total time: ");
		
		System.out.println("\nStart paralle CA ----> ");
		
	  	timer.start();		
		net.getContingencyList().stream().parallel().forEach(cont -> {
			Ieee14_CA_Utils.funcContProcessor.accept(cont, algoDsl.algo());
		});
		timer.logStd("Total time: ");		
	}
}

