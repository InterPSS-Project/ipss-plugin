package org.interpss.plugin.optadj.algo.result;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.interpss.datatype.base.BaseJSONBean;


/** 

* @author  Donghao.F 

* @date 2023 Mar 11 09:50:58 

* 

*/
public class AclfNetSsaResultContainer extends BaseJSONBean{
	
	List<SsaBranchOverLimitInfo> baseOverLimitInfo;
	
	List<SsaBranchOverLimitInfo> caOverLimitInfo;

	public AclfNetSsaResultContainer() {
		super();
		baseOverLimitInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
		caOverLimitInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
	}

	public List<SsaBranchOverLimitInfo> getBaseOverLimitInfo() {
		return baseOverLimitInfo;
	}

	public void setBaseOverLimitInfo(List<SsaBranchOverLimitInfo> baseOverLimitInfo) {
		this.baseOverLimitInfo = baseOverLimitInfo;
	}

	public List<SsaBranchOverLimitInfo> getCaOverLimitInfo() {
		return caOverLimitInfo;
	}

	public void setCaOverLimitInfo(List<SsaBranchOverLimitInfo> caOverLimitInfo) {
		this.caOverLimitInfo = caOverLimitInfo;
	}
	
	
}
