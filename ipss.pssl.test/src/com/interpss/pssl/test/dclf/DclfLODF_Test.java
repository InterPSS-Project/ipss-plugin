 /*
  * @(#)AclfSampleTest.java   
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

import static org.junit.Assert.assertTrue;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.common.ReferenceBusException;
import com.interpss.pssl.test.BaseTestSetup;

public class DclfLODF_Test extends BaseTestSetup {

	//@Test
	public void lodfTest() throws PSSLException, ReferenceBusException, InterpssException, IpssNumericException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net);
		algoDsl.algo().calculateDclf();

		//////////////////////
		algoDsl.outageBranch("Bus4", "Bus7", "1");
		
		double f = algoDsl.monitorBranch("Bus4", "Bus9", "1")
	       				.lineOutageDFactor();
		//System.out.println("LODF (x4->7) -> (4->9): " + f);
		assertTrue(NumericUtil.equals(f, 0.176530585, 0.00001));

		f = algoDsl.monitorBranch("Bus5", "Bus6", "1")
	       			.lineOutageDFactor();
	    System.out.println("LODF (x4->7) -> (5->6): " + f );
		assertTrue(NumericUtil.equals(f, 0.492179, 0.00001));

	    f = algoDsl.monitorBranch("Bus9", "Bus14", "1")
	       			.lineOutageDFactor();
	    //System.out.println("LODF (x4->7) -> (9->14): " + f );
		assertTrue(NumericUtil.equals(f, -0.19580, 0.00001));

	    f =	algoDsl.monitorBranch("Bus12", "Bus13", "1")
	        		.lineOutageDFactor();
	    //System.out.println("LODF (x4->7) -> (12->13): " + f );
		assertTrue(NumericUtil.equals(f, 0.043530, 0.00001));
		
		/////////////////////////
		algoDsl.outageBranch("Bus6", "Bus13", "1");
		
		f = algoDsl.monitorBranch("Bus4", "Bus9", "1")
	       			.lineOutageDFactor();
		//System.out.println("\nLODF (x6->13) -> (4->9): " + f );
		assertTrue(NumericUtil.equals(f, 0.062178, 0.00001));

		f = algoDsl.monitorBranch("Bus5", "Bus6", "1")
	       			.lineOutageDFactor();
		//System.out.println("LODF (x6->13) -> (5->6): " + f );
		assertTrue(NumericUtil.equals(f, -0.170541, 0.00001));

		f = algoDsl.monitorBranch("Bus9", "Bus14", "1")
	       			.lineOutageDFactor();
		//System.out.println("LODF (x6->13) -> (9->14): " + f );
		assertTrue(NumericUtil.equals(f, 0.346406, 0.00001));

		f = algoDsl.monitorBranch("Bus12", "Bus13", "1")
	       			.lineOutageDFactor();
		//System.out.println("LODF (x6->13) -> (12->13): " + f );
		assertTrue(NumericUtil.equals(f, 0.653594, 0.00001));
	}

	@Test
	public void n_1Test() throws PSSLException, ReferenceBusException, InterpssException, IpssNumericException   {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		DclfAlgorithmDSL algoDsl = IpssDclf.createDclfAlgorithm(net)
				.runDclfAnalysis();

		double flow_4_7 = algoDsl.branchFlow("Bus4", "Bus7", "1", UnitType.mW);
		double flow_4_9 = algoDsl.branchFlow("Bus4", "Bus9", "1", UnitType.mW);
		double flow_5_6 = algoDsl.branchFlow("Bus5", "Bus6", "1", UnitType.mW);
		
		algoDsl.outageBranch("Bus4", "Bus7", "1");
		
		double f1_1 = algoDsl.monitorBranch("Bus4", "Bus9", "1")
						   .lineOutageDFactor();

		double f2_1 = algoDsl.monitorBranch("Bus5", "Bus6", "1")
						    .lineOutageDFactor();

		System.out.println("\nN-1 Contingency Analysis");
		System.out.println("4->9 before:" + flow_4_9 + ", after: " + (flow_4_9+f1_1*flow_4_7));
		System.out.println("5->6 before:" + flow_5_6 + ", after: " + (flow_5_6+f2_1*flow_4_7));
		
		double flow_6_13 = algoDsl.branchFlow("Bus6", "Bus13", "1", UnitType.mW);
		double flow_9_14 = algoDsl.branchFlow("Bus9", "Bus14", "1", UnitType.mW);
		double flow_12_13 = algoDsl.branchFlow("Bus12", "Bus13", "1", UnitType.mW);

		algoDsl.outageBranch("Bus6", "Bus13", "1");
		
		double f3_2 = algoDsl.monitorBranch("Bus9", "Bus14", "1")
						   .lineOutageDFactor();

		double f4_2 = algoDsl.monitorBranch("Bus12", "Bus13", "1")
						    .lineOutageDFactor();

		System.out.println("\nN-1 Contingency Analysis");
		System.out.println("9->14 before:" + flow_9_14 + ", after: " + (flow_9_14+f3_2*flow_6_13));
		System.out.println("12->13 before:" + flow_12_13 + ", after: " + (flow_12_13+f4_2*flow_6_13));
	}
}

