package org.interpss.plugin.result.bean;

import com.interpss.common.datatype.BaseJSONBean;


public class AclfHvdcInfo extends BaseJSONBean {
	private String branchId;  
	
	public AclfHvdcInfo() {
	}

	public AclfHvdcInfo(String branchId) {
		super();
		this.branchId = branchId;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}
}
