package org.interpss.plugin.result.bean;

import org.apache.commons.math3.complex.Complex;

public class AclfLoadInfo extends BaseAclfBusInfo {
	private String busId;  
	private String loadId;
	private String loadName;
	
	private String loadType;  // ConstP, ConstI, ConstZ, ZIP
	private Complex load;
	
	public AclfLoadInfo() {
	}
	
	public AclfLoadInfo(int areaNo, String areaName, int zoneNo, String zoneName,
			String busId, String loadId, String loadName, String loadType, Complex load) {
		super(areaNo, areaName, zoneNo, zoneName);
		this.busId = busId;
		this.loadId = loadId;
		this.loadName = loadName;
		this.loadType = loadType;
		this.load = load;
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
	
	public String getLoadType() {
		return loadType;
	}
	
	public void setLoadType(String loadType) {
		this.loadType = loadType;
	}
	
	public Complex getLoad() {
		return load;
	}
	
	public void setLoad(Complex load) {
		this.load = load;
	}
}
