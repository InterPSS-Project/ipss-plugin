package org.interpss.dist.algo.path;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.algo.path.NetPathWalkDirectionEnum;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Methods for initialization for backward and forward network walkthrough
 * 
 * @author mzhou
 *
 */
public class DistPathNetLfSolver extends DistPathNetInitinizer {
	public DistPathNetLfSolver() {
	}

	public DistPathNetLfSolver(NetPathWalkDirectionEnum dir) {
		super(dir);
	}
	
	@Override protected void initBranchForward(Branch branch) {
		branch.setVisited(false);
	}
	
	@Override protected void initBranchBackward(Branch branch) {
		branch.setVisited(false);
		branch.setWeight(new Complex(0.0,0.0));
	}
	
	@Override protected void initBusBackward(Bus bus) {
		bus.setVisited(false);
	}	
}
