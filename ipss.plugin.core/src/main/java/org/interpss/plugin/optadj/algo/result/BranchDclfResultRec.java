package org.interpss.plugin.optadj.algo.result;

import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;


/** 
* Branch DCLF result record
*/
public class BranchDclfResultRec {
	public DclfAlgoBranch dclfBranch;
	public double mwFlow;
	public double loadingPercent;

	public BranchDclfResultRec(DclfAlgoBranch dclfBranch) {
		double baseMVA = dclfBranch.getBranch().getNetwork().getBaseMva();
		this.dclfBranch = dclfBranch;
		this.mwFlow = dclfBranch.getDclfFlow() * baseMVA;
		this.loadingPercent = Math.abs(mwFlow / dclfBranch.getBranch().getRatingMva1())*100;
	}
}
