package org.interpss.dist.algo.path;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.dist.DistBranch;
import com.interpss.dist.DistBus;

/**
 * For branch visiting in the forward walkthrough process. Based on the branch weight (active power flow), 
 * bus voltage is calculated during the walkthrough process.
 *  
 * @author mzhou
 *
 */
public class DistPathBranchLfSolver extends DistPathBranchWalker {
	public DistPathBranchLfSolver() {
	}
	
	public DistPathBranchLfSolver(List<String> info) {
		super(info);
	}	

	/**
	 * Calculate bus voltage based on branch active power flow during the forward walkthough process,
	 * if the opposite bus is visited 
	 *    
	 * @param bra branch object to be visited
	 * @param bus unvisited bus, its voltage to be calculated.
	 */
	@Override protected void visitBranchBus(Branch bra, Bus bus) {
		if (bus.isVisited())
			throw new InterpssRuntimeException("Programming error");
		
		bra.setVisited(true);
		if (this.statusInfo != null)
			statusInfo.add("\nBranch visited " + bra.getId());
		
		// calculate branch voltage drop
		DistBranch branch = (DistBranch)bra;
		DistBus optBus = (DistBus)(branch.getOppositeBus(bus));
		
		Complex cur = DistPathLfHelper.current(branch);
		Complex z = branch.getAcscBranch().getZ();
		Complex dV = cur.multiply(z);
		
		Complex optVoltage = optBus.getAcscBus().getVoltage();
		Complex newVoltage;
		if (DistPathLfHelper.atFlowIntoSide(branch, bus))
			newVoltage = optVoltage.add(dV);
		else 
			newVoltage = optVoltage.subtract(dV);
		
		DistBus distBus = (DistBus)bus;
		distBus.getAcscBus().setVoltage(newVoltage);
		
		bus.setVisited(true);
		if (this.statusInfo != null)
			statusInfo.add("\nTo Bus visited " + bus.getId() + " voltage :" + 
						Number2String.toStr(distBus.getAcscBus().getVoltageMag(UnitType.PU)) + " (" +
						Number2String.toStr(distBus.getAcscBus().getVoltageAng(UnitType.Deg)) + ") " +
						" branch cur: " + Number2String.toStr(cur));
	}
}
