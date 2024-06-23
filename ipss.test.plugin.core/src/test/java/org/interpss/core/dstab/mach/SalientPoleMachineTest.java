 /*
  * @(#)TestSalientPoleMachineCase.java   
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

import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.dstab.util.sample.SampleDStabCase;

public class SalientPoleMachineTest extends TestSetupBase {
	
	@Test
	public void test_Case1()  throws InterpssException {
		// create a machine in a two-bus network. The loadflow already converged
		BaseDStabNetwork<?,?> net = SampleDStabCase.createDStabTestNet();
		SalientPoleMachine mach = SampleDStabCase.createSalientPoleMachine(net);
		
		// calculate mach state init values
		BaseDStabBus<?,?> bus = net.getDStabBus("Gen");

		mach.initStates(bus);
		System.out.println("Ygen: " + mach.getYgen());
		System.out.println("Igen: " + mach.getIgen());
		assertTrue(Math.abs(mach.getYgen().getReal()-0.20820320632937747) < 0.00001);
		assertTrue(Math.abs(mach.getYgen().getImaginary()+8.328128253175098) < 0.00001);
//		assertTrue(Math.abs(mach.getIgen().getReal()-0.96658) < 0.00001);
//		assertTrue(Math.abs(mach.getIgen().getImaginary()+8.09625) < 0.00001);		

		// the following values to compare to are by long-hand calculation
		
		System.out.println("Angle, Eq1, Psikd, Psiq11, Efd, Pe: " + mach.getAngle() + ", " + 
				mach.getEq1() + ", " + mach.getPsikd() +  ", " + mach.getPsiq11() + ", " + 
				mach.getEfd()+ ", " + mach.getPe());
		
		assertTrue(Math.abs(mach.getAngle()-0.48142) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getPsikd()-1.0139441866327137) < 0.00001);
		assertTrue(Math.abs(mach.getPsiq11()+0.4139965935498328) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.880088) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward one step
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER,0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER,1);
		
		// again, the following values to compare to are by long-hand calculation. There
		// should be no change
		//System.out.println("Angle, Eq1, Ed11, Eq11, Efd, Pe: " + mach.getAngle()*Constants.RtoD + ", " + mach.getEq1() + ", " + mach.getEd11() +  ", " + mach.getEq11() + ", " + mach.getEfd()+ ", " + mach.getPe());
		assertTrue(Math.abs(mach.getAngle()-0.48142) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getPsikd()-1.0139441866327137) < 0.00001);
		assertTrue(Math.abs(mach.getPsiq11()+0.4139965935498328) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.880088) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 1);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 1);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 1);
		assertTrue(Math.abs(mach.getAngle()-0.48142) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getPsikd()-1.0139441866327137) < 0.00001);
		assertTrue(Math.abs(mach.getPsiq11()+0.4139965935498328) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.880088) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.803) < 0.00001);
		
		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 1);

		// again, the following values to compare to are by long-hand calculation
		
		System.out.println("Angle, Eq1, Psikd, Psiq11, Efd, Pe: " + mach.getAngle() + ", " + 
				mach.getEq1() + ", " + mach.getPsikd() +  ", " + mach.getPsiq11() + ", " + 
				mach.getEfd()+ ", " + mach.getPe());
				
		assertTrue(Math.abs(mach.getAngle()-0.4817927159622999) < 0.00001);
		assertTrue(Math.abs(mach.getEq1()-1.09514) < 0.00001);
		assertTrue(Math.abs(mach.getPsikd()-1.0139441866327137) < 0.00001);
		assertTrue(Math.abs(mach.getPsiq11()+0.4139965935498328) < 0.00001);
		assertTrue(Math.abs(mach.getEfd()-1.880088) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.803158) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}
}
