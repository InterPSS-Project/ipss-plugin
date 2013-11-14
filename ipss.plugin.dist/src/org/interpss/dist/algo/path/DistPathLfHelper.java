package org.interpss.dist.algo.path;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.dist.DistBranch;
import com.interpss.dist.DistBus;

public class DistPathLfHelper {
	/**
	 * calculate branch current based on branch power flow stored in branch.weight
	 * 
	 * @param bra
	 * @return
	 */
	public static Complex current(DistBranch branch) {
		Complex flow = branch.getWeight();
		// flag = 1, branch connection direction = branch flow direction. 
		// branch weight, which is the power into the branch, is at the fromBus side
		DistBus bus = (DistBus)(branch.getIntFlag()==1?branch.getFromBus():branch.getToBus());
//		System.out.println("Branch: " + branch.getId() + 
//				" voltage: " + ComplexFunc.toMagAng(bus.getAcscBus().getVoltage()) + " @" + bus.getId());
		return flow.divide(bus.getAcscBus().getVoltage()).conjugate();
	}
	
	/**
	 * check if the bus is at flow into branch side
	 * 
	 * @param branch
	 * @param bus
	 * @return
	 */
	public static boolean atFlowIntoSide(Branch branch, Bus bus) {
		return branch.isFromBus(bus) && branch.getIntFlag() == 1 ||
			   branch.isToBus(bus) && branch.getIntFlag() == 0;
	}	
}
