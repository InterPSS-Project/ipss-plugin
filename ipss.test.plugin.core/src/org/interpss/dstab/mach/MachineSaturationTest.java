 /*
  * @(#)TestEq1Ed1MachineCase.java   
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

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.mach.MachineType;

public class MachineSaturationTest extends TestSetupBase {
	@Test
	public void test_Case1() {
		// create a two-bus network. Loadflow calculated
		DStabilityNetwork net = createTestDStabBus();

		// create a machine and connect to the bus "Gen"
		Eq1Ed1Machine mach = (Eq1Ed1Machine)DStabObjectFactory.
							createMachine("MachId", "MachName", MachineType.EQ1_ED1_MODEL, net, "Gen");
		DStabBus bus = net.getDStabBus("Gen");
		// set machine data
		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.setMultiFactors(bus);
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setXd(1.81);
		mach.setXl(0.15);
		mach.setXq(1.08);
		mach.setXd1(0.23);
		mach.setXq1(0.23);
		mach.setX0(0.1);
		mach.setX2(0.2);
		mach.setRa(0.003);
		mach.setTd01(5.6);
		mach.setTq01(1.5);
		mach.setSliner(0.8);  // no saturation
		mach.setSe100(12.5);
		mach.setSe120(50.0);		
		
		assertTrue(Math.abs(mach.getXdAdjusted(bus)-1.485327) < 0.0001);

		bus.setVoltage(new Complex(1.4, 0.0));
		assertTrue(Math.abs(mach.getXdAdjusted(bus)-0.746303) < 0.0001);
	}
}
