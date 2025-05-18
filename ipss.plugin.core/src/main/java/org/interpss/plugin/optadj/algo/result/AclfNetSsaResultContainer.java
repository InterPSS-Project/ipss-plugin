package org.interpss.plugin.optadj.algo.result;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.interpss.datatype.base.BaseJSONBean;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;


/** 
* A container class for storing the results of the AclfNetwork SSA algorithm.

* @author  Donghao.F 

* @date 2023 Mar 11 09:50:58 

* 

*/
public class AclfNetSsaResultContainer extends BaseJSONBean{
	// a list of branches that are over the limit in the base case
	List<DclfAlgoBranch> baseOverLimitInfo;
	// a list of branches that are over the limit in the contingency situation	
	List<BranchCAResultRec> caOverLimitInfo;

	public AclfNetSsaResultContainer() {
		super();
		baseOverLimitInfo = new CopyOnWriteArrayList<DclfAlgoBranch>();
		caOverLimitInfo = new CopyOnWriteArrayList<BranchCAResultRec>();
	}

	public List<DclfAlgoBranch> getBaseOverLimitInfo() {
		return baseOverLimitInfo;
	}

	public void setBaseOverLimitInfo(List<DclfAlgoBranch> baseOverLimitInfo) {
		this.baseOverLimitInfo = baseOverLimitInfo;
	}

	public List<BranchCAResultRec> getCaOverLimitInfo() {
		return caOverLimitInfo;
	}

	public void setCaOverLimitInfo(List<BranchCAResultRec> caOverLimitInfo) {
		this.caOverLimitInfo = caOverLimitInfo;
	}
}
