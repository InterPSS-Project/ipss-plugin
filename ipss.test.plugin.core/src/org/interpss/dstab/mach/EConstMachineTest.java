 /*
  * @(#)TestEConstMachineCase.java   
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.mach;

import static org.junit.Assert.assertTrue;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineType;

public class EConstMachineTest extends TestSetupBase {
	@Test
	public void test_Case1() {
		// create a machine in a two-bus network. The loadflow already converged
		EConstMachine mach = createMachine();
		
		// calculate mach state init values
		DStabBus bus = net.getDStabBus("Gen");
		mach.initStates(bus);
		/*
		System.out.println("Angle(deg) " + mach.getAngle()*Constants.RtoD);
		System.out.println("E " + mach.getE());
		System.out.println("Pe " + mach.getPe());
		System.out.println("Pm " + mach.getPm());
		*/
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.49656) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.8) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.49656) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.8) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.49656) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.8) < 0.00001);

		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		/*
		System.out.println("Angle(deg) " + mach.getAngle()*Constants.RtoD);
		System.out.println("Speed " + mach.getSpeed());
		System.out.println("E " + mach.getE());
		System.out.println("Pe " + mach.getPe());
		System.out.println("Pm " + mach.getPm());
		*/
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.51456) < 0.00001);
		assertTrue(Math.abs(mach.getSpeed()-1.0002) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}
	
	public EConstMachine createMachine() {
		// create a two-bus network. Loadflow calculated
		DStabilityNetwork net = createTestDStabBus();

		EConstMachine mach = (EConstMachine)DStabObjectFactory.
							createMachine("MachId", "MachName", MachineType.ECONSTANT, net, "Gen");
		DStabBus bus = net.getDStabBus("Gen");
		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.setMultiFactors(bus);
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setXd1(0.3);
		return mach;
	}	
}
