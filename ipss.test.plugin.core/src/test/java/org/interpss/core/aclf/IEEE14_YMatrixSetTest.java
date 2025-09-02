 /*
  * @(#)IEEE14_3WXfrTest.java   
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
  * @Date 05/15/2013
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.aclf;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.sparse.base.ISparseEquation.IndexType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.impl.solver.YMatrixSolver;

public class IEEE14_YMatrixSetTest extends CorePluginTestSetup {
	@Test 
	public void bus14TestCase() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		ISparseEqnComplex[] ySet = new YMatrixSolver(aclfNet)
				.formYMatrixSet(true, /* mPartOnly */ 
						        bus -> !(bus.isGenPV() || bus.isSwing()));
		ISparseEqnComplex ynn = ySet[0];
		//System.out.println(ynn);
/*
		Y_NN
		   (1,1)     10.5130 -38.6542i
		   (2,1)     -6.8410 +21.5786i
		   (3,1)      0.0000 + 4.8895i
		   (4,1)      0.0000 + 1.8555i
		   (1,2)     -6.8410 +21.5786i
		   (2,2)      9.5680 -35.5336i
		   (1,3)      0.0000 + 4.8895i
		   (3,3)      0.0000 -19.5490i
		   (4,3)      0.0000 + 9.0901i
		   (1,4)      0.0000 + 1.8555i
		   (3,4)      0.0000 + 9.0901i
		   (4,4)      5.3261 -24.0925i
		   (5,4)     -3.9020 +10.3654i
		   (9,4)     -1.4240 + 3.0291i
		   (4,5)     -3.9020 +10.3654i
		   (5,5)      5.7829 -14.7683i
		   (6,5)     -1.8809 + 4.4029i
		   (5,6)     -1.8809 + 4.4029i
		   (6,6)      3.8359 - 8.4970i
		   (7,7)      4.0150 - 5.4279i
		   (8,7)     -2.4890 + 2.2520i
		   (7,8)     -2.4890 + 2.2520i
		   (8,8)      6.7249 -10.6697i
		   (9,8)     -1.1370 + 2.3150i
		   (4,9)     -1.4240 + 3.0291i
		   (8,9)     -1.1370 + 2.3150i
		   (9,9)      2.5610 - 5.3440i	
 */
		assertTrue("", NumericUtil.equals(ynn.getA(0, 0), new Complex(10.5130, -38.6542), 0.001));
		assertTrue("", NumericUtil.equals(ynn.getA(1, 0), new Complex(-6.8410, 21.5786), 0.001));
		
		ISparseEqnComplex ynm = ySet[1];
		//System.out.println(ynm);

		/*
		 * M (1~n)
				2
				3
				6
				8
				1

		Y_NM
		   (1,1)     -1.6860 + 5.1158i
		   (2,1)     -1.7011 + 5.1939i
		   (1,2)     -1.9860 + 5.0688i
		   (2,3)      0.0000 + 4.2574i
		   (6,3)     -1.9550 + 4.0941i
		   (7,3)     -1.5260 + 3.1760i
		   (8,3)     -3.0989 + 6.1028i
		   (3,4)      0.0000 + 5.6770i
		   (2,5)     -1.0259 + 4.2350i
		   
		 * Ynm M number (0~n-1)
				1		   
				2
				3
				6
				8
 */
		assertTrue("", NumericUtil.equals(ynm.getA(1-1, 2-1), new Complex(-1.6860,  5.1158), 0.001));
		assertTrue("", NumericUtil.equals(ynm.getA(2-1, 2-1), new Complex(-1.7011,  5.1939), 0.001));
	}
	
	@Test 
	public void inactiveBusTestCase() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		aclfNet.getBus("Bus14").setStatus(false);
		aclfNet.getBranch("Bus9->Bus14(1)").setStatus(false);
		aclfNet.getBranch("Bus13->Bus14(1)").setStatus(false);
		
		ISparseEqnComplex[] ySet = new YMatrixSolver(aclfNet)
				.formYMatrixSet(true, /* mPartOnly */ 
						        bus -> !(bus.isGenPV() || bus.isSwing()));
		ISparseEqnComplex ynn = ySet[0];
		//System.out.println(ynn);
		assertTrue("", ynn.getDimension() == 8);
		
		ISparseEqnComplex ynm = ySet[1];
		//System.out.println(ynm);
		assertTrue("", ynm.getDimension(IndexType.Row) == 8);
		assertTrue("", ynm.getDimension(IndexType.Col) == 5);
	}
	
}

