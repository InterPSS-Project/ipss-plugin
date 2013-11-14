package org.interpss.dist.algo.path;

import java.util.List;

import com.interpss.core.algo.path.NetPathWalkDirectionEnum;
import com.interpss.core.common.visitor.INetVisitor;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Network;
import com.interpss.dist.DistBus;
import com.interpss.dist.DistBusCode;

/**
 * Network initialization for walkthrough for Loadflow calculation.
 * 
 * @author mzhou
 *
 */

public class DistPathNetInitinizer implements INetVisitor<Bus, Branch> {
	protected List<String> statusInfo = null;
	private NetPathWalkDirectionEnum direction;
	
	public DistPathNetInitinizer() {
	}

	public DistPathNetInitinizer(NetPathWalkDirectionEnum dir) {
		this.direction = dir;
	}
	
	public DistPathNetInitinizer(NetPathWalkDirectionEnum dir, List<String> info) {
		this.direction = dir;
		this.statusInfo = info;
	}

	public void setDirection(NetPathWalkDirectionEnum dir) {
		this.direction = dir;
	}

	public void visit(Network<Bus, Branch> net) {
		if (this.direction == NetPathWalkDirectionEnum.ALONG_PATH)
			visitForward(net);
		else
			visitBackward(net);
	}
	
	/*
	 * network initialization for forward walk 
	 */
	
	private void visitForward(Network<Bus, Branch> net) {
		for (Branch b : net.getBranchList())
			initBranchForward(b);

		for (Bus b : net.getBusList()) {
			initBusForward(b);
		}
	}	
	
	protected void initBranchForward(Branch branch) {
		branch.setVisited(false);
	}
	
	protected void initBusForward(Bus b) {
		DistBus bus = (DistBus)b;
		bus.setVisited(false);
		if ( bus.getBusCode() == DistBusCode.UTILITY ) {
			bus.setVisited(true);
			if (this.statusInfo != null)
				statusInfo.add("\nUtility bus visited " + bus.getId());
		}
	}

	/*
	 * network initialization for backward walk 
	 */

	private void visitBackward(Network<Bus, Branch> net) {
		for (Branch b : net.getBranchList())
			initBranchBackward(b);

		for (Bus b : net.getBusList()) {
			initBusBackward(b);
		}
	}	

	protected void initBranchBackward(Branch branch) {
		branch.setVisited(false);
	}
	
	protected void initBusBackward(Bus b) {
		DistBus bus = (DistBus)b;
		bus.setVisited(false);
	}
}
