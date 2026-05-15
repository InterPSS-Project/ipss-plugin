package org.interpss.plugin.optadj.algo.result;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.interpss.datatype.base.BaseJSONBean;

import com.interpss.algo.parallel.BranchCAResultRec;


/** 
* A container class for storing the results of the AclfNetwork SSA algorithm.

* @author  Donghao.F 

* @date 2023 Mar 11 09:50:58 

* 

*/
public class AclfNetSsaResultContainer extends BaseJSONBean{
	// a list of branches that are over the limit in the base case
	private List<BranchDclfResultRec> baseOverLimitInfo;
	// a list of branches that are over the limit in the contingency situation	
	private List<BranchCAResultRec> caOverLimitInfo;

	public AclfNetSsaResultContainer() {
		super();
		baseOverLimitInfo = new CopyOnWriteArrayList<BranchDclfResultRec>();
		caOverLimitInfo = new CopyOnWriteArrayList<BranchCAResultRec>();
	}

	public List<BranchDclfResultRec> getBaseOverLimitInfo() {
		return baseOverLimitInfo;
	}

	public Map<String, BranchDclfResultRec> toBaseOverLimitInfoMap() {
		return baseOverLimitInfo.stream()
			.collect(Collectors.toMap(rec -> rec.dclfBranch.getId(), Function.identity()));
	}

	public void setBaseOverLimitInfo(List<BranchDclfResultRec> baseOverLimitInfo) {
		this.baseOverLimitInfo = baseOverLimitInfo;
	}

	public List<BranchCAResultRec> getCaOverLimitInfo() {
		return caOverLimitInfo;
	}

	public Map<String, BranchCAResultRec> toCaOverLimitInfoMap() {
		return caOverLimitInfo.stream()
			.collect(Collectors.toMap(rec -> rec.aclfBranch.getId(), Function.identity()));
	}

	public void setCaOverLimitInfo(List<BranchCAResultRec> caOverLimitInfo) {
		this.caOverLimitInfo = caOverLimitInfo;
	}
}
