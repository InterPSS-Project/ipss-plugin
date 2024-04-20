package org.interpss.threePhase.basic.dstab.impl;

import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3W3PBranch;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.dstab.impl.DStab3WBranchImpl;

public class DStab3W3PBranchImpl extends DStab3WBranchImpl implements DStab3W3PBranch{
	
	
	@Override
	public boolean create2WBranches(AclfBranchCode branchCode, String[] properties) {
	     
		DStabNetwork3Phase net = (DStabNetwork3Phase) this.getFromBus().getNetwork();

		// create a bus - star bus, at the FromBus base voltage level
		String fromId = this.getFromBus().getId();
		String toId = this.getToBus().getId();
		String tertId = this.getTertiaryBus().getId();	
		String starBusId = properties[0];
		
		// create the bus and add it to the net object
		this.starBus = ThreePhaseObjectFactory.create3PDStabBus(starBusId, net);
		this.getStarBus().setStatus(this.isActive());
		this.getStarBus().setName("3W Xfr Star Bus");
		this.getStarBus().setDesc("Star bus for branch " + this.getId());
		this.getStarBus().setBaseVoltage(this.getFromBus().getBaseVoltage());
		
		//AclfBus starBus = (AclfBus)this.getStarBus();
		if (this.voltageStarBus != null)
			((AclfBus)starBus).setVoltage(this.voltageStarBus);
		
		// create the from branch: fromBudId -> starBusId
		DStab3PBranch branch = ThreePhaseObjectFactory.create3PBranch();
		net.addBranch(branch, fromId, starBusId, properties[1]);
		this.setFromBranch(branch);
		branch.setBranchCode(branchCode);
		branch.setStatus(this.isActive());

		// create the to branch: starBusId -> toBusId
		branch = ThreePhaseObjectFactory.create3PBranch();
		net.addBranch(branch, starBusId, toId, properties[2]);
		this.setToBranch(branch);
		branch.setStatus(this.isActive());
		branch.setBranchCode(branchCode);

		// create the tert branch: starBusId -> terrBusId
		branch = ThreePhaseObjectFactory.create3PBranch();
		net.addBranch(branch, starBusId, tertId, properties[3]);
		this.setTertiaryBranch(branch);
		branch.setStatus(this.isActive());
		branch.setBranchCode(branchCode);
		return true;
	}

}
