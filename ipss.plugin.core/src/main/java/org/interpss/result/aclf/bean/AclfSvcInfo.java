package org.interpss.result.aclf.bean;

import org.interpss.datatype.base.BaseJSONBean;

public class AclfSvcInfo extends BaseJSONBean {
	private String busId;  
	
	public AclfSvcInfo() {
	}
	
	public AclfSvcInfo(String busId) {
		super();
		this.busId = busId;
	}

	public String getBusId() {
		return busId;
	}

	public void setBusId(String busId) {
		this.busId = busId;
	}
}
