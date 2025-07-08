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

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.aclf.PSSELfGControlConfig;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class SwitchedShuntGControlTest extends CorePluginTestSetup {	
	@Test
	public void testBaseCase() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testdata/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		// Bus4 is a switched shunt bus
		AclfBus bus4 = net.getBus("Bus4");
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt swShunt = bus4.getSwitchedShunt();
		swShunt.setBLimit(new LimitType(3.0*0.23637, 0.0));
		//System.out.println("Switched Shunt: " + swShunt);
		
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", swShunt.getShuntCompensatorList().size() == 3);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
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
						"testdata/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		// Bus4 is a switched shunt bus
		AclfBus bus4 = net.getBus("Bus4");
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt swShunt = bus4.getSwitchedShunt();
		swShunt.setBLimit(new LimitType(3.0*0.23637, 0.0));
		//System.out.println("Switched Shunt: " + swShunt);
		
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", swShunt.getShuntCompensatorList().size() == 3);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().initialize(new PSSELfGControlConfig(config -> {
	  		config.gCtrlSwitchedShunt = PSSELfGControlConfig.SwitchedShunt_LockAll;
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
	public void testContinuous() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testdata/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		// Bus4 is a switched shunt bus
		AclfBus bus4 = net.getBus("Bus4");
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt swShunt = bus4.getSwitchedShunt();
		swShunt.setBLimit(new LimitType(3.0*0.23637, 0.0));
		//System.out.println("Switched Shunt: " + swShunt);
		
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", swShunt.getShuntCompensatorList().size() == 3);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().initialize(new PSSELfGControlConfig(config -> {
	  		config.gCtrlSwitchedShunt = PSSELfGControlConfig.SwitchedShunt_Continuous;
	  	}));
		algo.loadflow();
		assertTrue(net.isLfConverged());

		//System.out.println("Switched Shunt: " + swShunt);
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.4725, 0.0001));
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
	public void testDiscrate() throws Exception {
		// load the test data
		AclfNetwork net = IpssAdapter
				.importAclfNet(
						"testdata/adpter/psse/PSSE_5Bus_Test_switchShunt.raw")
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30).load()
				.getImportedObj();
		
		// Bus4 is a switched shunt bus
		AclfBus bus4 = net.getBus("Bus4");
		assertTrue(bus4.isSwitchedShunt());
		SwitchedShunt swShunt = bus4.getSwitchedShunt();
		// TODO: need to set and init the switched shunt data according to the ShuntCompensatorList
		// in the PSSE adapter
		swShunt.setBLimit(new LimitType(3.0*0.23637, 0.0));
		double baseKva = net.getBaseKva();
		swShunt.getShuntCompensatorList().forEach(comp -> {
			comp.setUnitQMvar(0.23637*100.0); // UnitQ is in Mvar.
			comp.calB(baseKva);  // we need to call this method to set the compensator B value
		});
		//System.out.println("Switched Shunt: " + swShunt);
		
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.2, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.FIXED);
		assertTrue("", swShunt.getShuntCompensatorList().size() == 3);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().initialize(new PSSELfGControlConfig(config -> {
	  		config.gCtrlSwitchedShunt = PSSELfGControlConfig.SwitchedShunt_Discrete;
	  	}));
		algo.loadflow();
		assertTrue(net.isLfConverged());

		//System.out.println("Switched Shunt: " + swShunt);
		assertTrue("", NumericUtil.equals(swShunt.getBInit(), 0.23637, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getBActual(), 0.4727, 0.0001));
		assertTrue("", NumericUtil.equals(swShunt.getQ(), 0.4723, 0.0001));
		assertTrue("", swShunt.getControlMode() == AclfAdjustControlMode.DISCRETE);
		assertTrue("", NumericUtil.equals(swShunt.getVSpecified(), 1.0, 0.0001));
		assertTrue("", NumericUtil.equals(bus4.getVoltageMag(), 0.99953, 0.0001));
		
		String swingId = "Bus1";
		AclfSwingBusAdapter swing = net.getBus(swingId).toSwingBus();
		//System.out.println("AclfNet Model: "+swing.getGenResults(UnitType.PU) );				
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal() - 0.2253) < 0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary() - 0.0077) < 0.0001);
	}
}

