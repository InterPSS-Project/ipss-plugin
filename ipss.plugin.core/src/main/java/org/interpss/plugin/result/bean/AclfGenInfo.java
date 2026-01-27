package org.interpss.plugin.result.bean;

import org.apache.commons.math3.complex.Complex;

public class AclfGenInfo extends BaseAclfBusInfo {
	private String busId;  
	private String genId;
	private String genName;
	
	private String genType;  // PV, PQ, Swing
	private Complex gen;
	
	public AclfGenInfo() {
	}
	
	public AclfGenInfo(int areaNo, String areaName, int zoneNo, String zoneName,
			String busId, String genId, String genName, String genType, Complex gen) {
		super(areaNo, areaName, zoneNo, zoneName);
		this.busId = busId;
		this.genId = genId;
		this.genName = genName;
		this.genType = genType;
		this.gen = gen;
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
	
	public String getGenType() {
		return genType;
	}
	
	public void setGenType(String genType) {
		this.genType = genType;
	}
	
	public Complex getGen() {
		return gen;
	}
	
	public void setGen(Complex gen) {
		this.gen = gen;
	}
}
