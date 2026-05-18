package org.interpss.plugin.optadj.algo.result;

import com.interpss.algo.parallel.BranchCAResultRec;


/** 
* Branch Optimization Adjustment result record
*/
public class BranchOptAdjustCAResultRec extends BranchCAResultRec {
	public double adjustedPostFlowMW;
	public double adjustedLoadingPercent;

	public BranchOptAdjustCAResultRec(BranchCAResultRec rec) {
		super(rec.contingency, rec.aclfBranch, rec.preFlowMW, rec.shiftedFlowMW);
	}
}
