package org.interpss.plugin.result.bean;

public class AclfSvcInfo extends BaseAclfBusInfo {
	private String busId;  
	
	public AclfSvcInfo() {
	}
	
	public AclfSvcInfo(int areaNo, String areaName, int zoneNo, String zoneName,
			String busId) {
		super(areaNo, areaName, zoneNo, zoneName);
		this.busId = busId;
	}

	public String getBusId() {
		return busId;
	}

	public void setBusId(String busId) {
		this.busId = busId;
	}
}
