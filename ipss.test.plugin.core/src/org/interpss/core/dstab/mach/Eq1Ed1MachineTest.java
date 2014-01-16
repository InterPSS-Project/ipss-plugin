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

package org.interpss.core.dstab.mach;

import static org.junit.Assert.assertTrue;

import org.interpss.numeric.datatype.ComplexFunc;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.util.sample.SampleDStabCase;

public class Eq1Ed1MachineTest extends TestSetupBase {
	@Test
	public void test_Case1()  throws InterpssException {
		// create a machine in a two-bus network. The loadflow already converged
		DStabilityNetwork net = SampleDStabCase.createDStabTestNet();
		System.out.println(net.net2String());
		Eq1Ed1Machine mach = SampleDStabCase.createEq1Ed1Machine(net);
		
		// calculate mach state init values
		mach.initStates();
		//System.out.println("Ygen: " + ComplexFunc.toStr(mach.getYgen()));
		System.out.println("Igen: " +  ComplexFunc.toStr(mach.getIgen()));
		assertTrue(Math.abs(mach.getYgen().getReal()-0.0567) < 0.00001);
		assertTrue(Math.abs(mach.getYgen().getImaginary()+4.34709) < 0.00001);
		assertTrue(Math.abs(mach.getIgen().getReal()-0.8567) < 0.0001);
		assertTrue(Math.abs(mach.getIgen().getImaginary()+4.94709) < 0.00001);	
		
		// the following values to compare to are by long-hand calculation
		System.out.println("Angle, Ed1, Eq1, Efd, Pe: " + mach.getAngle()*180/Math.PI + ", " + 
				mach.getEd1() + ", " + mach.getEq1() + ", " + mach.getEfd()+ ", " + mach.getPe());
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.88008) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation. There
		// should be no change
		//System.out.println("Angle, Ed1, Eq1, Efd, Pe: " + mach.getAngle()*Constants.RtoD + ", " + mach.getEd1() + ", " + mach.getEq1() + ", " + mach.getEfd()+ ", " + mach.getPe());
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.88008) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);
		//System.out.println("Angle, Ed1, Eq1, Efd, Pe: " + mach.getAngle()*Constants.RtoD + ", " + mach.getEd1() + ", " + mach.getEq1() + ", " + mach.getEfd()+ ", " + mach.getPe());
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.58341) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.88008) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER);

		// again, the following values to compare to are by long-hand calculation
		//System.out.println("Angle, Ed1, Eq1, Efd, Pe: " + mach.getAngle()*Constants.RtoD + ", " + mach.getEd1() + ", " + mach.getEq1() + ", " + mach.getEfd()+ ", " + mach.getPe());
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-27.60114) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getEd1()+0.36656) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.88008) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}
}
