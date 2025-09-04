package org.interpss.result.aclf.bean;

import org.interpss.datatype.base.BaseJSONBean;

public class AclfLoadInfo extends BaseJSONBean {
	private String busId;  
	private String loadId;
	private String loadName;
	
	public AclfLoadInfo() {
	}
	
	public AclfLoadInfo(String busId, String loadId, String loadName) {
		super();
		this.busId = busId;
		this.loadId = loadId;
		this.loadName = loadName;
	}

	public String getBusId() {
		return busId;
	}

	public void setBusId(String busId) {
		this.busId = busId;
	}
	
	public String getLoadId() {
		return loadId;
	}
	
	public void setLoadId(String loadId) {
		this.loadId = loadId;
	}
	
	public String getLoadName() {
		return loadName;
	}
	
	public void setLoadName(String loadName) {
		this.loadName = loadName;
	}
}
