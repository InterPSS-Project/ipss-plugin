package org.interpss.dep.QA.compare.aclf;

import org.interpss.dep.QA.compare.DataComparatorAdapter;
import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.core.aclf.AclfBranch;

/**
 * AclfBranch data comparator
 * 
 * @author mzhou
 *
 */
public class AclfBranchDataComparator extends DataComparatorAdapter<AclfBranch, AclfBranch> {
	private final double tolerance;

	public AclfBranchDataComparator() {
		this(0.0);
	}

	public AclfBranchDataComparator(double tolerance) {
		this.tolerance = tolerance;
	}

	@Override public boolean compare(AclfBranch baseBranch, AclfBranch branch) {
		this.msg = "";
		boolean ok = true;
		
		if (baseBranch.isActive() != branch.isActive()) {
			this.msg += "\nbranch.status not equal: " + branch.getId() + ", " + baseBranch.isActive() + "(base), " + branch.isActive(); ok = false; }
		
		if (!equals(baseBranch.getZ(), branch.getZ())) {
			this.msg += "\nbranch.z not equal: " + branch.getId() + ", " + Number2String.toStr(baseBranch.getZ()) + "(base), " + Number2String.toStr(branch.getZ()); ok = false; }		

		if (!equals(baseBranch.getFromShuntY(), branch.getFromShuntY())) {
			this.msg += "\nbranch.fromShuntY not equal: " + branch.getId() + ", " + Number2String.toStr(baseBranch.getFromShuntY()) + "(base), " + Number2String.toStr(branch.getFromShuntY()); ok = false;		}		

		if (!equals(baseBranch.getToShuntY(), branch.getToShuntY())) {
			this.msg += "\nbranch.toShuntY not equal: " + branch.getId() + ", " + Number2String.toStr(baseBranch.getToShuntY()) + "(base), " + Number2String.toStr(branch.getToShuntY()); ok = false; }		

		if (!equals(baseBranch.getFromTurnRatio(), branch.getFromTurnRatio())) {
			this.msg += "\nbranch.fromTurnRatio not equal: " + branch.getId() + ", " + baseBranch.getFromTurnRatio() + "(base), " + branch.getFromTurnRatio(); ok = false; }	
		
		if (!equals(baseBranch.getToTurnRatio(), branch.getToTurnRatio())) {
			this.msg += "\nbranch.toTurnRatio not equal: " + branch.getId() + ", " + baseBranch.getToTurnRatio() + "(base), " + branch.getToTurnRatio(); ok = false; 	}	

		if (!equals(baseBranch.getFromPSXfrAngle(), branch.getFromPSXfrAngle())) {
			this.msg += "\nbranch.fromPSXfrAngle not equal: " + branch.getId() + ", " + baseBranch.getFromPSXfrAngle() + "(base), " + branch.getFromPSXfrAngle(); ok = false; 	}	
		
		if (!equals(baseBranch.getToPSXfrAngle(), branch.getToPSXfrAngle())) {
			this.msg += "\nbranch.toPSXfrAngle not equal: " + branch.getId() + ", " + baseBranch.getToPSXfrAngle() + "(base), " + branch.getToPSXfrAngle(); ok = false; }
		
		this.msg += "\n";
		return ok;
	}

	private boolean equals(double baseValue, double value) {
		return this.tolerance > 0.0
				? NumericUtil.equals(baseValue, value, this.tolerance)
				: NumericUtil.equals(baseValue, value);
	}

	private boolean equals(org.apache.commons.math3.complex.Complex baseValue,
			org.apache.commons.math3.complex.Complex value) {
		return this.tolerance > 0.0
				? NumericUtil.equals(baseValue, value, this.tolerance)
				: NumericUtil.equals(baseValue, value);
	}
}
