package org.interpss.result.aclf.bean;

import org.interpss.datatype.base.BaseJSONBean;

public class AclfGenInfo extends BaseJSONBean {
	private String busId;  
	private String genId;
	private String genName;
	
	public AclfGenInfo() {
	}
	
	public AclfGenInfo(String busId, String genId, String genName) {
		super();
		this.busId = busId;
		this.genId = genId;
		this.genName = genName;
	}

	public String getBusId() {
		return busId;
	}

	public void setBusId(String busId) {
		this.busId = busId;
	}
	
	public String getGenId() {
		return genId;
	}
	
	public void setGenId(String genId) {
		this.genId = genId;
	}
	
	public String getGenName() {
		return genName;
	}
	
	public void setGenName(String genName) {
		this.genName = genName;
	}
}
