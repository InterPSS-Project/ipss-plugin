package org.interpss.plugin.optadj.algo.result;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.interpss.datatype.base.BaseJSONBean;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;


/** 
* A container class for storing the results of the AclfNetwork SSA algorithm.

* @author  Donghao.F 

* @date 2023 Mar 11 09:50:58 

* 

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
