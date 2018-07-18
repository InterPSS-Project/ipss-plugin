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

import org.apache.commons.math3.complex.Complex;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssAclf;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssAclf.LfAlgoDSL;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.dclf.SenAnalysisType;
import com.interpss.core.dclf.common.ReferenceBusException;
import com.interpss.pssl.test.BaseTestSetup;

public class QSen_Test extends BaseTestSetup {
	@Test
	public void dVdQ_SenTest() throws IpssNumericException, ReferenceBusException , InterpssException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net);
			
		LfAlgoDSL aclfAlgoDsl = IpssAclf.createAclfAlgo(net);
	  	
		aclfAlgoDsl.lfMethod(AclfMethod.NR)
	  			.nonDivergent(true)
	  			.runLoadflow();
	  	//System.out.println(AclfOutFunc.loadFlowSummary(net));
	  	
/*
     BusID       Name           Code           Volt(pu)   Angle(deg)     P(pu)     Q(pu)
  ---------------------------------------------------------------------------------------
  Bus13        Bus 13    LV PQ    + ConstP       1.05038       -15.2      -0.1350   -0.0580
  Bus14        Bus 14    LV PQ    + ConstP       1.03553       -16.0      -0.1490   -0.0500
 */
		
		double dV_dQ = algoDsl.busSensitivity(SenAnalysisType.QVOLTAGE, "Bus2", "Bus4");
	  	System.out.println("dV(Bus4)/dQ(Bus2): " + dV_dQ);
		
		dV_dQ = algoDsl.busSensitivity(SenAnalysisType.QVOLTAGE, "Bus14", "Bus14");
	  	System.out.println("dV(Bus14)/dQ(Bus14): " + dV_dQ);
	  	
		dV_dQ = algoDsl.busSensitivity(SenAnalysisType.QVOLTAGE, "Bus14", "Bus13");
	  	System.out.println("dV(Bus13)/dQ(Bus14): " + dV_dQ);
	  	
	  	net.getBus("Bus14").toLoadBus().setLoad(new Complex(0.1490, 0.1500));
		aclfAlgoDsl.runLoadflow();
		System.out.println(AclfOutFunc.loadFlowSummary(net));
/*
 * 
     BusID       Name           Code           Volt(pu)   Angle(deg)     P(pu)     Q(pu)
  ---------------------------------------------------------------------------------------
  Bus13        Bus 13    LV PQ    + ConstP       1.04552       -15.1      -0.1350   -0.0580
  Bus14        Bus 14    LV PQ    + ConstP       1.01418       -15.6      -0.1490   -0.1500

       dV(Bus14)/dQ(Bus14): 0.4436834940076393
                       (1.01418 - 1.03553) / -0.1 = 0.2135
       dV(Bus13)/dQ(Bus14): 0.2722663457317793
                       (1.04552 - 1.05038) / -0.1 = 0.0486
*/
	}
}

