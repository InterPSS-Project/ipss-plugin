package org.interpss.plugin.optadj.algo.result;

import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;


/** 
* Branch Optimization Adjustment result record
*/
public class BranchOptAdjustResultRec extends BranchDclfResultRec {
	public double adjustedMwFlow;
	public double adjustedLoadingPercent;

	public BranchOptAdjustResultRec(DclfAlgoBranch dclfBranch) {
		super(dclfBranch);
	}
}
