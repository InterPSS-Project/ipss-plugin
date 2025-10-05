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

package org.interpss.core.dclf;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.dclf.DclfAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class IEEE14_Dclf_Test extends CorePluginTestSetup {
	@Test 
	public void dclfLossTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		/*
		   Bud Id       VoltAng(deg)     Gen     Load    ShuntG
		=========================================================
		    Bus1           0.000       225.43     0.00     0.00 
		    Bus2          -0.092        40.00    21.70     0.00 
		    Bus3          -0.233         0.00    94.20     0.00 		
		 */

		DclfAlgoBus dclfBus1 = dclfAlgo.getDclfAlgoBus("Bus1");
		AclfBus bus1 = dclfBus1.getBus();
		int n1 = bus1.getSortNumber();
		double pgen = dclfAlgo.getBusPower(dclfBus1) * aclfNet.getBaseMva(); 
		assertTrue("Aclf 232.393", NumericUtil.equals(pgen, 225.43, 0.01));

		DclfAlgoBus dclfBus2 = dclfAlgo.getDclfAlgoBus("Bus2");
		AclfBus bus2 = dclfBus2.getBus();
		int n2 = bus2.getSortNumber();
		double angle = dclfAlgo.getBusAngle(n2);
		assertTrue("", NumericUtil.equals(angle, -0.092, 0.001));		
	}

	@Test 
	public void dclfTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		/*
			   Bud Id       VoltAng(deg)     Gen     Load    ShuntG
			=========================================================
			    Bus1           0.000       219.00     0.00     0.00 
			    Bus2          -0.088        40.00    21.70     0.00 
			    Bus3          -0.226         0.00    94.20     0.00 
		 */
		DclfAlgoBus dclfBus1 = dclfAlgo.getDclfAlgoBus("Bus1");
		AclfBus bus1 = dclfBus1.getBus();
		int n1 = bus1.getSortNumber();
		double pgen = dclfAlgo.getBusPower(dclfBus1) * aclfNet.getBaseMva(); 
		assertTrue("Aclf 232.393", NumericUtil.equals(pgen, 219.00, 0.01));

		DclfAlgoBus dclfBus2 = dclfAlgo.getDclfAlgoBus("Bus2");
		AclfBus bus2 = dclfBus2.getBus();
		int n2 = bus2.getSortNumber();
		double angle = dclfAlgo.getBusAngle(n2);
		assertTrue("", NumericUtil.equals(angle, -0.088, 0.001));			
	}
	
	//@Test 
	public void aclfTestCase() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		assertTrue(net.isLfConverged());		
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32393)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16549)<0.0001);
	}
}

