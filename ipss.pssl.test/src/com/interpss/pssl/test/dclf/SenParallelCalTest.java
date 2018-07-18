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

import static org.junit.Assert.assertTrue;

import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.IDclfSolver;
import com.interpss.core.dclf.SenAnalysisType;
import com.interpss.core.dclf.common.ReferenceBusException;
import com.interpss.core.dclf.solver.DclfSolver;
import com.interpss.pssl.test.BaseTestSetup;

public class SenParallelCalTest extends BaseTestSetup {
	@Test
	public void test1() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		IDclfSolver solver = new DclfSolver(net);
		solver.prepareBMatrix(SenAnalysisType.PANGLE);
		
		//System.out.println("Step-1");
		net.getBusList().parallelStream()
				.filter(bus -> !bus.isRefBus())
				.forEach(bus -> {
					try {
						double[] dAry = solver.calSensitivityImpl(SenAnalysisType.PANGLE, bus.getId());
						//System.out.println("id: " + bus.getId() + ", " + dAry[1]);
						checkResults(bus.getId(), dAry);					
					} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
 
		//System.out.println("Step-2");
		net.getBusList().parallelStream()
				.filter(bus -> !bus.isRefBus())
				.forEach(bus -> {
					try {
						double[] dAry = solver.getSenPAngle(bus.getId());
						//System.out.println("id: " + bus.getId() + ", " + dAry[1]);
						checkResults(bus.getId(), dAry);
					} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
	}
	
	@Test
	public void test2() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/aclf/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		IDclfSolver solver = new DclfSolver(net);
		solver.prepareBMatrix(SenAnalysisType.PANGLE);
		
		//System.out.println("Step-2");
		net.getBusList().parallelStream()
				.filter(bus -> !bus.isRefBus())
				.forEach(bus -> {
					try {
						double[] dAry = solver.getSenPAngle(bus.getId());
						//System.out.println("id: " + bus.getId() + ", " + dAry[1]);
						checkResults(bus.getId(), dAry);
					} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
	}
	
	private void checkResults(String busId, double[] dAry) {
		switch (busId) {
		case "Bus2" : assertTrue(NumericUtil.equals(dAry[1], -0.04958601624914454)); break;
		case "Bus3" : assertTrue(NumericUtil.equals(dAry[1], -0.04417283396130418)); break;
		case "Bus4" : assertTrue(NumericUtil.equals(dAry[1], -0.03949628414724478)); break;
		case "Bus5" : assertTrue(NumericUtil.equals(dAry[1], -0.03612661375343601)); break;
		case "Bus6" : assertTrue(NumericUtil.equals(dAry[1], -0.037272487606249974)); break;
		case "Bus7" : assertTrue(NumericUtil.equals(dAry[1], -0.03889212570259713)); break;
		case "Bus8" : assertTrue(NumericUtil.equals(dAry[1], -0.03889212570259713)); break;
		case "Bus9" : assertTrue(NumericUtil.equals(dAry[1], -0.038574301149729456)); break;
		case "Bus10" : assertTrue(NumericUtil.equals(dAry[1], -0.03834294429351558)); break;
		case "Bus11" : assertTrue(NumericUtil.equals(dAry[1], -0.03781706605241497)); break;
		case "Bus12" : assertTrue(NumericUtil.equals(dAry[1], -0.03737535698243073)); break;
		case "Bus13" : assertTrue(NumericUtil.equals(dAry[1], -0.037455735118199524)); break;
		case "Bus14" : assertTrue(NumericUtil.equals(dAry[1], -0.03808523600806539)); break;	
	}		
	}
	
}

