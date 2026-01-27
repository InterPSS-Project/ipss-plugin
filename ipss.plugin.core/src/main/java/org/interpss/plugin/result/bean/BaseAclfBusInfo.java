package org.interpss.plugin.result.bean;

import org.interpss.datatype.base.BaseJSONBean;

public abstract class BaseAclfBusInfo extends BaseJSONBean {
	private int areaNo;  
	private String areaName;  
	private int zoneNo;  
	private String zoneName;   
	
	public BaseAclfBusInfo() {
	}
	
	public BaseAclfBusInfo(int areaNo, String areaName, int zoneNo, String zoneName) {
		super();
		this.areaNo = areaNo;
		this.areaName = areaName;
		this.zoneNo = zoneNo;
		this.zoneName = zoneName;
	}

	public int getAreaNo() {
		return areaNo;
	}
	
	public void setAreaNo(int areaNo) {
		this.areaNo = areaNo;
	}
	
	public String getAreaName() {
		return areaName;
	}
	
	public void setAreaName(String areaName) {
		this.areaName = areaName;
	}
	
	public int getZoneNo() {
		return zoneNo;
	}
	
	public void setZoneNo(int zoneNo) {
		this.zoneNo = zoneNo;
	}
	
	public String getZoneName() {
		return zoneName;
	}
	
	public void setZoneName(String zoneName) {
		this.zoneName = zoneName;
	}
}
