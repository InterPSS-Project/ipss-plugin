 /*
  * @(#)Dclf_Test.java   
  *
  * Copyright (C) 2006 www.interpss.org
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
  * @Date 07/15/2007
  * 
  *   Revision History
  *   ================
  *
  */

package com.interpss.pssl.test.dclf;

import static org.interpss.CorePluginFunction.DclfResult;

import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnDouble;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssAclf;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssAclf.LfAlgoDSL;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.net.Bus;
import com.interpss.pssl.test.BaseTestSetup;

public class Dclf_Test extends BaseTestSetup {
	//@Test
	public void dclfTest() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		System.out.println(DclfResult.f(algoDsl.algo(), false).toString());		
	}
	
	@Test
	public void b11MatrixTest() throws IpssNumericException, InterpssException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();	
		
		LfAlgoDSL aclfAlgoDsl = IpssAclf.createAclfAlgo(net);
	  	
		aclfAlgoDsl.lfMethod(AclfMethod.NR)
	  			.nonDivergent(true)
	  			.runLoadflow();
	  	//System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net);
		
		ISparseEqnDouble b11Eqn = algoDsl.getB1Matrix();
		
/*
 * Base case
 * 
      BusID     Vact     Vspec      Q      Qmax     Qmin   Status
     -------- -------- -------- -------- -------- -------- ------
     Bus2       1.0450   1.0450     0.44     0.50    -0.40    on
     Bus3       1.0100   1.0100     0.25     0.40     0.00    on
     Bus6       1.0700   1.0700     0.13     0.24    -0.06    on
     Bus8       1.0900   1.0900     0.18     0.24    -0.06    on

  After increasing dQ = 0.1 pu
  
      BusID     Vact     Vspec      Q      Qmax     Qmin   Status
     -------- -------- -------- -------- -------- -------- ------
     Bus2       1.0620   1.0450     0.54     0.50    -0.40    on
     Bus3       1.0422   1.0100     0.34     0.40     0.00    on
     Bus6       1.1173   1.0700     0.21     0.24    -0.06    on
     Bus8       1.1533   1.0900     0.29     0.24    -0.06    on
     
 */
		double dQ = 0.1;
		
		// bus.getSortNumber() used in the b11Eqn 
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (bus.isGenPV())
				b11Eqn.setBi(dQ, bus.getSortNumber());
		}
		
		b11Eqn.solveEqn(1.0e-20);

		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (bus.isGenPV()) {
				double newv = bus.getVoltageMag() - b11Eqn.getXi(bus.getSortNumber());
				bus.toPVBus().setDesiredVoltMag(newv);
			}
		}
	  	
		// turn off PV Limit control
		aclfAlgoDsl.applyAdjustAlgo(false)
			       .runLoadflow();
		System.out.println(AclfOutFunc.loadFlowSummary(net));
	}
}

