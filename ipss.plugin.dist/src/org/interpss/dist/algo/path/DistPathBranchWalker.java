package org.interpss.dist.algo.path;

import java.util.List;

import com.interpss.core.common.visitor.IBranchBVisitor;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Base class for network forward walkthrough.
 * 
 * @author mzhou
 *
 */
public class DistPathBranchWalker implements IBranchBVisitor {
	protected List<String> statusInfo = null;
	
	public DistPathBranchWalker() {
	}

	public DistPathBranchWalker(List<String> info) {
		this.statusInfo = info;
	}

	/**
	 * Visit the branch and connected buses, if one of the bus is visited and the other is unvisited.
	 * Return false if the walk of the branch is not done.
	 */
	public boolean visit(Branch bra) { 
		// if the branch is unvisited and one of the terminal bus (from/to) is visited and the other is unvisited
		//    visit the branch
		//    visit the other bus
		boolean done = true;
		if (!bra.isVisited()) {
			// when the branch is visited, set done = false
			if (bra.getFromBus().isVisited() && !bra.getToBus().isVisited()) {
				visitBranchBus(bra, bra.getToBus());
				done = false;
			}
			else if (!bra.getFromBus().isVisited() && bra.getToBus().isVisited()) {
				visitBranchBus(bra, bra.getFromBus());
				done = false;
			}
		}
		//return bra.isVisited();
		return done;
	}	
	
	/**
	 * Visit the branch and the unvisited bus, doing nothing special. This method
	 * will be override  
	 * 
	 * @param bra
	 * @param bus unvisit bus
	 */
	protected void visitBranchBus(Branch bra, Bus bus) {
		bra.setVisited(true);
		if (this.statusInfo != null)
			statusInfo.add("\nBranch visited " + bra.getId());
		bus.setVisited(true);
		if (this.statusInfo != null)
			statusInfo.add("\nTo Bus visited " + bus.getId());
	}
}
