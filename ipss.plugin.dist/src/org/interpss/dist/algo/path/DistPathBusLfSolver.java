package org.interpss.dist.algo.path;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.util.Number2String;

import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.dist.DistBranch;
import com.interpss.dist.DistBus;

/**
 * For bus visiting in the backward walkthrough process. Based on the bus voltage, 
 * branch active power is calculated during the walkthrough process. 
 * 
 * @author mzhou
 *
 */
public class DistPathBusLfSolver extends DistPathBusWalker {
	
	public DistPathBusLfSolver() {
	}
	
	public DistPathBusLfSolver(List<String> info) {
		super(info);
	}
	
	/**
	 * During the bus visiting, visit the connected branch, store branch power as branch.weight,
	 * at the into bus side.
	 * 
	 * Condition to call this method : for a visited bus, there is only one un-visited branch
	 * 
	 *    (to be visited) |--w--------- -> ----------| (visited)
	 * 
	 * @param branch the connected branch to be visited
	 * @param bus the bus object which is already visited  
	 */
	@Override protected void visitBranch(Branch branch, Bus bus) {
		/*
		 *                        | -> branch weight
		 *           out flow <-  |
		 *                        | <- bus P 
		 */
		Complex flowOutgoing = getP(bus, bus.getNetwork().getBaseKva())
				                .subtract(getVisitedBranchTotalWeight(bus));
		
		DistBus distBus = (DistBus)bus;
		if (!DistPathLfHelper.atFlowIntoSide(branch, bus)) {
			// calculate branch loss based on the flowOutgoing
			DistBranch distBranch = (DistBranch)branch;
			double cur = flowOutgoing.divide(distBus.getAcscBus().getVoltage()).abs();
			Complex loss = distBranch.getAcscBranch().getZ().multiply(cur*cur);
			flowOutgoing = flowOutgoing.multiply(-1.0).add(loss);
		}
		
/*
		Complex mis = distBus.getAcscBus().mismatch(AclfMethod.NR);
		if (mis.abs() < 0.001) {
			// bus mismatch - into network direction as the positive direction
			flowOutgoing = flowOutgoing.add(mis);
		}
*/			
		branch.setWeight(flowOutgoing);
		
		if (this.statusInfo != null)
			statusInfo.add("\nBranch visited " + branch.getId() + "  branch flow direction: " +
		                   (branch.getIntFlag()==1? "along" : "oppsite") +
		                   "  weight: " + Number2String.toStr(branch.getWeight()));
		branch.setVisited(true);
	}	
	
	/**
	 * calculate branch power at the bus side, out-going as positive direction.
	 * 
	 *          |--w--------- -> ----------|
	 * 
	 * @param branch
	 * @param bus
	 * @return
	 */
	@Override protected Complex getBranchPowerFlow(Branch branch, Bus bus) {
		// get branch flow stored in the branch.weight
		Complex flow = super.getBranchPowerFlow(branch, bus);
		// if bus is not at the flow into side, take the loss from the weight
		if (!DistPathLfHelper.atFlowIntoSide(branch, bus)) {
			DistBranch distBranch = (DistBranch)branch;
			double cur = DistPathLfHelper.current(distBranch).abs();
			Complex loss = distBranch.getAcscBranch().getZ().multiply(cur*cur);
			flow = flow.subtract(loss);
		}
		return flow;
	}
}
