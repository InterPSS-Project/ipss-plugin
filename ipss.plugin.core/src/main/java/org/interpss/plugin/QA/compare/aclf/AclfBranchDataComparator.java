package org.interpss.plugin.QA.compare.aclf;

import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.QA.compare.DataComparatorAdapter;

import com.interpss.core.aclf.AclfBranch;

/**
 * AclfBranch data comparator
 * 
 * @author mzhou
 *
 */
public class AclfBranchDataComparator extends DataComparatorAdapter<AclfBranch, AclfBranch> {
	@Override public boolean compare(AclfBranch baseBranch, AclfBranch branch) {
		this.msg = "";
		boolean ok = true;
		
		if (baseBranch.isActive() != branch.isActive()) {
			this.msg += "\nbranch.status not equal: " + branch.getId() + ", " + baseBranch.isActive() + "(base), " + branch.isActive(); ok = false; }
		
		if (!NumericUtil.equals(baseBranch.getZ(), branch.getZ())) {
			this.msg += "\nbranch.z not equal: " + branch.getId() + ", " + Number2String.toStr(baseBranch.getZ()) + "(base), " + Number2String.toStr(branch.getZ()); ok = false; }		

		if (!NumericUtil.equals(baseBranch.getFromShuntY(), branch.getFromShuntY())) {
			this.msg += "\nbranch.fromShuntY not equal: " + branch.getId() + ", " + Number2String.toStr(baseBranch.getFromShuntY()) + "(base), " + Number2String.toStr(branch.getFromShuntY()); ok = false;		}		

		if (!NumericUtil.equals(baseBranch.getToShuntY(), branch.getToShuntY())) {
			this.msg += "\nbranch.toShuntY not equal: " + branch.getId() + ", " + Number2String.toStr(baseBranch.getToShuntY()) + "(base), " + Number2String.toStr(branch.getToShuntY()); ok = false; }		

		if (!NumericUtil.equals(baseBranch.getFromTurnRatio(), branch.getFromTurnRatio())) {
			this.msg += "\nbranch.fromTurnRatio not equal: " + branch.getId() + ", " + baseBranch.getFromTurnRatio() + "(base), " + branch.getFromTurnRatio(); ok = false; }	
		
		if (!NumericUtil.equals(baseBranch.getToTurnRatio(), branch.getToTurnRatio())) {
			this.msg += "\nbranch.toTurnRatio not equal: " + branch.getId() + ", " + baseBranch.getToTurnRatio() + "(base), " + branch.getToTurnRatio(); ok = false; 	}	

		if (!NumericUtil.equals(baseBranch.getFromPSXfrAngle(), branch.getFromPSXfrAngle())) {
			this.msg += "\nbranch.fromPSXfrAngle not equal: " + branch.getId() + ", " + baseBranch.getFromPSXfrAngle() + "(base), " + branch.getFromPSXfrAngle(); ok = false; 	}	
		
		if (!NumericUtil.equals(baseBranch.getToPSXfrAngle(), branch.getToPSXfrAngle())) {
			this.msg += "\nbranch.toPSXfrAngle not equal: " + branch.getId() + ", " + baseBranch.getToPSXfrAngle() + "(base), " + branch.getToPSXfrAngle(); ok = false; }
		
		this.msg += "\n";
		return ok;
	}			
}
