 /*
  * @(#)SVCTest.java   
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

package org.interpss.plugin.lfGCtrl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.aclf.LfGlobalAdjControlConfig;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class SwitchedShuntGControlTest extends CorePluginTestSetup {	
	@Test
	public void testBaseCase() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testData/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		// Bus4 is a switched shunt bus
		AclfBus bus4 = net.getBus("Bus4");
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt swShunt = bus4.getFirstSwitchedShunt(true);
		//swShunt.setBLimit(new LimitType(3.0*0.23637, 0.0));
		//System.out.println("Switched Shunt: " + swShunt);
		
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", swShunt.getShuntCompensatorList().size() == 3);
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.loadflow();
		assertTrue(net.isLfConverged());

		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", NumericUtil.equals(swShunt.getVSpecified(), 1.0, 0.0001));
		assertTrue("", NumericUtil.equals(bus4.getVoltageMag(), 0.91985, 0.0001));
		
		String swingId = "Bus1";
		AclfSwingBusAdapter swing = net.getBus(swingId).toSwingBus();
		//System.out.println("AclfNet Model: "+swing.getGenResults(UnitType.PU) );				
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal() - 0.2255) < 0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary() - 0.1585) < 0.0001);
	}
	
	@Test
	public void testLockAll() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testData/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		// Bus4 is a switched shunt bus
		AclfBus bus4 = net.getBus("Bus4");
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt swShunt = bus4.getFirstSwitchedShunt(true);
		//swShunt.setBLimit(new LimitType(3.0*0.23637, 0.0));
		//System.out.println("Switched Shunt: " + swShunt);
		
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", swShunt.getShuntCompensatorList().size() == 3);
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().initialize(new LfGlobalAdjControlConfig(config -> {
	  		config.gCtrlSwitchedShunt = LfGlobalAdjControlConfig.SwitchedShunt_LockAll;
	  	}));
		algo.loadflow();
		assertTrue(net.isLfConverged());

		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", NumericUtil.equals(swShunt.getVSpecified(), 1.0, 0.0001));
		assertTrue("", NumericUtil.equals(bus4.getVoltageMag(), 0.91985, 0.0001));
		
		String swingId = "Bus1";
		AclfSwingBusAdapter swing = net.getBus(swingId).toSwingBus();
		//System.out.println("AclfNet Model: "+swing.getGenResults(UnitType.PU) );				
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal() - 0.2255) < 0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary() - 0.1585) < 0.0001);
	}
	
	@Test
	public void testContinuousOnly() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testData/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		// Bus4 is a switched shunt bus
		AclfBus bus4 = net.getBus("Bus4");
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt swShunt = bus4.getFirstSwitchedShunt(true);
		//swShunt.setBLimit(new LimitType(3.0*0.23637, 0.0));
		//System.out.println("Switched Shunt: " + swShunt);
		
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", swShunt.getShuntCompensatorList().size() == 3);

		//TODO: pss/e switched shunt control default is range control, not point control, if we need to
		// get the following test passed, we need to set the control type to be point control
		// The input range in the data file is [0.9, 1.1], so the VSpecified is set to 1.0 (middle of the range by default	)
		swShunt.setAdjControlType(AclfAdjustControlType.POINT_CONTROL);
		
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().initialize(new LfGlobalAdjControlConfig(config -> {
	  		config.gCtrlSwitchedShunt = LfGlobalAdjControlConfig.SwitchedShunt_ContinuousOnly;
	  	}));
		algo.loadflow();
		assertTrue(net.isLfConverged());

		//System.out.println("Switched Shunt: " + swShunt);

		//Setting it continuous_only control, the switched shunt should be not activated as it is operated in fixed mode
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));


		// change the switched shunt to continuous mode, the switched shunt should be activated
		swShunt.setControlMode(AclfAdjustControlMode.CONTINUOUS);

		algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().initialize(new LfGlobalAdjControlConfig(config -> {
	  		config.gCtrlSwitchedShunt = LfGlobalAdjControlConfig.SwitchedShunt_ContinuousOnly;
	  	}));
		algo.loadflow();
		assertTrue(net.isLfConverged());


		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.47198, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.CONTINUOUS);
		assertTrue("", NumericUtil.equals(swShunt.getVSpecified(), 1.0, 0.0001));
		assertTrue("", NumericUtil.equals(bus4.getVoltageMag(), 0.99945, 0.0001));
		
		String swingId = "Bus1";
		AclfSwingBusAdapter swing = net.getBus(swingId).toSwingBus();
		//System.out.println("AclfNet Model: "+swing.getGenResults(UnitType.PU) );				
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal() - 0.2253) < 0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary() - 0.0079) < 0.0001);
	}
	
	@Test
	public void testEnableAll() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testData/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		// Bus4 is a switched shunt bus
		AclfBus bus4 = net.getBus("Bus4");
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt swShunt = bus4.getFirstSwitchedShunt(true);
	
		// double baseKva = net.getBaseKva();
		// swShunt.getShuntCompensatorList().forEach(comp -> {
		// 	comp.setUnitQMvar(0.23637*100.0); // UnitQ is in Mvar.
		// 	comp.calB(baseKva);  // we need to call this method to set the compensator B value
		// });
		//System.out.println("Switched Shunt: " + swShunt);
		
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", swShunt.getShuntCompensatorList().size() == 3);

		//TODO: pss/e switched shunt control default is range control, not point control, if we need to
		// get the following test passed, we need to set the control type to be point control
		// The input range in the data file is [0.9, 1.1], so the VSpecified is set to 1.0 (middle of the range by default	)
		swShunt.setAdjControlType(AclfAdjustControlType.POINT_CONTROL);

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().initialize(new LfGlobalAdjControlConfig(config -> {
	  		config.gCtrlSwitchedShunt = LfGlobalAdjControlConfig.SwitchedShunt_EnableAll;
	  	}));
		algo.loadflow();
		assertTrue(net.isLfConverged());

		System.out.println("Switched Shunt: " + swShunt);
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertEquals(swShunt.getBActual(), 0.47274, 0.0001);
		assertEquals(swShunt.getQ(), 0.47222  , 0.0001);
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.DISCRETE);
		assertEquals(swShunt.getVSpecified(), 1.0, 0.0001);
		assertEquals(bus4.getVoltageMag(), 0.99945, 0.0001);
		
		String swingId = "Bus1";
		AclfSwingBusAdapter swing = net.getBus(swingId).toSwingBus();
		//System.out.println("AclfNet Model: "+swing.getGenResults(UnitType.PU) );				
		assertEquals(swing.getGenResults(UnitType.PU).getReal(), 0.2253, 0.0001);
		assertEquals(swing.getGenResults(UnitType.PU).getImaginary(),0.0078, 0.0001);
	}
}

