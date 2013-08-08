 /*
  * @(#)TestEq1MachineCase.java   
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
import com.interpss.dstab.mach.Eq1Machine;
import com.interpss.dstab.mach.MachineType;

public class Eq1MachineCaseTest extends TestSetupBase {
	
	@Test
	public void test_Case1() {
		// create a machine in a two-bus network. The loadflow already converged
		Eq1Machine mach = createMachine();
		
		// calculate mach state init values
		DStabBus bus = net.getDStabBus("Gen");
		mach.initStates(bus);
		//System.out.println("Ygen: " + mach.getYgen());
		//System.out.println("Igen: " + mach.getIgen());
		assertTrue(Math.abs(mach.getYgen().getReal()-0.01208) < 0.00001);
		assertTrue(Math.abs(mach.getYgen().getImaginary()+2.63678) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getReal()-0.81207) < 0.00001);
		assertTrue(Math.abs(mach.getIgen(mach.getDStabBus()).getImaginary()+3.23676) < 0.00001);			

		/*
		System.out.println("Angle(deg) " + mach.getAngle()*Constants.RtoD);
		System.out.println("Eq1 " + mach.getEq1());
		System.out.println("Efd " + mach.getEfd());
		System.out.println("Pe " + mach.getPe());
		System.out.println("Pm " + mach.getPm());
		*/
		// the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation. There
		// should be no change
		assertTrue(Math.abs(mach.getSpeed()-1.0) < 0.00001);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		assertTrue(Math.abs(mach.getSpeed()-1.0) < 0.00001);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		
		/*
		System.out.println("Angle(deg) " + mach.getAngle()*Constants.RtoD);
		System.out.println("Speed " + mach.getSpeed());
		System.out.println("Eq1 " + mach.getEq1());
		System.out.println("Efd " + mach.getEfd());
		System.out.println("Pe " + mach.getPe());
		System.out.println("Pm " + mach.getPm());
		*/
		// again, the following values to compare to are by long-hand calculation
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.60114) < 0.00001);
		assertTrue(Math.abs(mach.getSpeed()-1.0002) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.8800642271660648) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}
	
	public Eq1Machine createMachine() {
		// create a two-bus network. Loadflow calculated
		DStabilityNetwork net = createTestDStabBus();

		// create a machine and connect to the bus "Gen"
		Eq1Machine mach = (Eq1Machine)DStabObjectFactory.
							createMachine("MachId", "MachName", MachineType.EQ1_MODEL, net, "Gen");
		DStabBus bus = net.getDStabBus("Gen");		
		// set machine data
		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.setMultiFactors(bus);
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setRa(0.003);
		mach.setXd(1.1);
		mach.setXl(0.14);
	    mach.setXq(1.08);
		mach.setXd1(0.23);
		mach.setTd01(5.6);
		mach.setSliner(2.0);  // no saturation
		mach.setSe100(1.0);
		mach.setSe120(1.0);		
		return mach;
	}	
}
