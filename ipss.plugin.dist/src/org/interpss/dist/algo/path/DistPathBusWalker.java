package org.interpss.dist.algo.path;

import java.util.List;

import org.apache.commons.math3.complex.Complex;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.core.common.visitor.IBusBVisitor;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.dist.DistBus;
import com.interpss.dist.DistBusCode;

/**
 * Bus based network walk-through for determining power flow direction. 
 * 
 *    Branch.infFlag is set during the process
 * 
 *       if power flow direction same as branch connection direction (from->to), intFlag = 1
 *                               Not same                                        intFlag = 0
 *                               
 *    Branch weight is set to branch power flow (pu), only real power part, only for 
 *    determining branch flow direction purpose, without considering branch loss.
 *    
 *    Branch weight is always positive. branch.intFlag is used to determine the active power flow 
 *    direction.
 *    
 *    It is assumed that branch loss is not going to change branch active power flow direction                             
 * 
 * @author mzhou
 *
 */
public class DistPathBusWalker implements IBusBVisitor {
	protected List<String> statusInfo = null;
	
	public DistPathBusWalker() {
	}

	public DistPathBusWalker(List<String> info) {
		this.statusInfo = info;
	}
	
	/**
	 * 	visit the bus and connected branches, return false if the walk of the bus is not done
	 */
	public boolean visit(Bus bus) { 
		boolean done = true;
		if (! bus.isVisited()) {
			DistBus distBus = (DistBus)bus; 
			// if the bus is to be visited, set done = false, meaning bus walking not done yet.			
			if (distBus.getBusCode() != DistBusCode.UTILITY && 
					bus.nBranchConnected(false /* visited */) == 1) {
				// bus.nBranchConnected(false) calculates no of unvisited branches connected to the bus
				visitBus(bus);
				done = false;
			}
			else if (distBus.getBusCode() == DistBusCode.UTILITY && bus.nBranchConnected(false) == 0) {
				visitUtilityBus(bus);
				done = false;
			}
		}
		return done;
	}
	
	/**
	 * visit the bus and connected branch if necessary for determining branch power flow direction.
	 * pre-condition : the bus has only one un-visited branch
	 * 
	 * @param bus
	 */
	protected void visitBus(Bus bus) {
		// visit the bus
		bus.setVisited(true);
		if (this.statusInfo != null)
			statusInfo.add("\nBus visited " + bus.getId());

		// find the unvisited branch
		Branch branch = null;
		int cnt = 0;
		for (Branch bra : bus.getBranchList()) {
			if (!bra.isVisited()) { 
				branch = bra;
				cnt++;
			}
		}

		if (branch != null && cnt == 1) {
			visitBranch(branch, bus);
		}
		else
			throw new InterpssRuntimeException("Programming error");
	}

	/**
	 * Visit the branch to calculate branch active power flow. Store the active power to branch.weight.
	 * for determining branch flow direction, therefore, only real power is stored as branch weight 
	 * 
	 * @param branch
	 * @param bus
	 */
	protected void visitBranch(Branch branch, Bus bus) {
		/*
		 *                            | -> branch weight of all visited branches
		 *  out flow (un-visited) <-  |
		 *                            | <- bus P 
		 */
		double flowOutgoing = getP(bus, bus.getNetwork().getBaseKva()).getReal() - getVisitedBranchTotalWeight(bus).getReal();

		boolean samedir = flowOutgoing > 0 && (branch.isFromBus(bus)) ||
				          flowOutgoing < 0 && (branch.isToBus(bus));
		branch.setIntFlag(samedir? 1 : 0);
		
		branch.setWeight(new Complex(Math.abs(flowOutgoing), 0.0));

		if (this.statusInfo != null)
			statusInfo.add("\nBranch visited " + branch.getId() + "  branch flow direction: " +
		                   (branch.getIntFlag()==1? "along" : "oppsite") +
		                   "  weight: " + branch.getWeight().getReal());
		branch.setVisited(true);
	}
	
	/**
	 * visit the utility bus, all branches connected should be visited before the
	 * utility bus can be visited
	 * 
	 * @param bus
	 */
	protected void visitUtilityBus(Bus bus) {
		bus.setVisited(true);
		if (this.statusInfo != null)
			statusInfo.add("\nUtility Bus visited " + bus.getId());
	}
	
	/**
	 * get bus power, generation as positive direction
	 * 
	 * @param bus
	 * @param baseKva
	 * @return
	 */
	protected Complex getP(Bus b, double baseKva) {
		DistBus bus = (DistBus)b;
		Complex p = new Complex(0.0,0.0);
		if ( bus.getBusCode() == DistBusCode.GENERATOR ) {
			p = bus.toGenerator().getGen(baseKva);
		}
		else if ( bus.getBusCode() == DistBusCode.IND_MOTOR ) {
			p = bus.toIndMotor().getLoad(baseKva).multiply(-1.0);
		}
		else if ( bus.getBusCode() == DistBusCode.SYN_MOTOR ) {
			p = bus.toSynMotor().getLoad(baseKva).multiply(-1.0);
		}
		else if ( bus.getBusCode() == DistBusCode.MIXED_LOAD ) {
			p = bus.toMixedLoad().getLoad(baseKva).multiply(-1.0);
		}
		//System.out.println("getP() for bus " + bus.getId() + "  " + p.getReal());
		return p;
	}
	

	/**
	 * out-going direction regarding the bus as positive direction. The following is an
	 * example:
	 * 
	 *                  |  f -- w1 -- t
	 *    f -- w2 -- t  |
	 *    
	 *    w = w1 - w2
	 * 
	 * @param bus
	 * @return
	 */
	protected Complex getVisitedBranchTotalWeight(Bus bus) {
		Complex sum = new Complex(0.0,0.0);
		for (Branch bra : bus.getBranchList()) {
			if (bra.isVisited()) {
				sum = sum.add(getBranchPowerFlow(bra, bus));
			}
		}
		//System.out.println("getVisitedBranchTotalWeight() for bus " + bus.getId() + "  " + sum.getReal());
		return sum;
	}	
	
	/**
	 * out-going direction regarding the bus as positive direction.
	 * for determining branch flow direction, therefore branch loss is ignored
	 * 
	 * @param branch
	 * @param bus
	 * @return
	 */
	protected Complex getBranchPowerFlow(Branch branch, Bus bus) {
		// Branch weight is always positive. branch.intFlag is used to determine the flow 
		// direction.
		double sign = DistPathLfHelper.atFlowIntoSide(branch, bus) ? 1.0 : -1.0;
		return branch.getWeight().multiply(sign);
	}
}
