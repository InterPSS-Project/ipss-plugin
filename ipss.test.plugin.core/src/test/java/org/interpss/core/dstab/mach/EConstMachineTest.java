package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.util.sample.SampleDStabCase;

public class EConstMachineTest extends TestSetupBase {
	@Test
	public void test_Case1()  throws InterpssException {
		// create a machine in a two-bus network. The loadflow already converged
		BaseDStabNetwork net = SampleDStabCase.createDStabTestNet();
		EConstMachine mach = SampleDStabCase.createEConstMachine(net);
		
		// calculate mach state init values
		BaseDStabBus bus = net.getDStabBus("Gen");

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
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 1);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.49656) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.8) < 0.00001);
		
		// Move forward more steps, we should have the same value, since there is no disturbance
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 1);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 1);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, 1);
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.49656) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-0.8) < 0.00001);

		// create an event by changing Pm from 2.0 to 1.0
		mach.setPm(1.0);  
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER,0);
		mach.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER,1);
	
		System.out.println("Angle(deg) " + mach.getAngle()*180/Math.PI);
		System.out.println("Speed " + mach.getSpeed());
		System.out.println("E " + mach.getE());
		System.out.println("Pe " + mach.getPe());
		System.out.println("Pm " + mach.getPm());
		
		assertTrue(Math.abs(Math.toDegrees(mach.getAngle())-11.51456) < 0.00001);
		assertTrue(Math.abs(mach.getSpeed()-1.0001999) < 0.00001);
		assertTrue(Math.abs(mach.getE()-1.20416) < 0.00001);
		assertTrue(Math.abs(mach.getPe()-0.8) < 0.00001);
		assertTrue(Math.abs(mach.getPm()-1.0) < 0.00001);
	}
}
