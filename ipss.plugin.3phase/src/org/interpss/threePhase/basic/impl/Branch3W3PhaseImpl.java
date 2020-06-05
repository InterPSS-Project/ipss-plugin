package org.interpss.threePhase.basic.impl;

import org.interpss.threePhase.basic.Branch3Phase;
import org.interpss.threePhase.basic.Branch3W3Phase;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.dstab.impl.DStab3WBranchImpl;

public class Branch3W3PhaseImpl extends DStab3WBranchImpl implements Branch3W3Phase{
	
	
	@Override
	public void create2WBranches(AclfBranchCode branchCode, String[] properties) throws InterpssException {
	     
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
		Branch3Phase branch = ThreePhaseObjectFactory.create3PBranch();
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
		
	}

}
