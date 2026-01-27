package org.interpss.plugin.result.bean;

import org.apache.commons.math3.complex.Complex;

public class AclfBusInfo extends BaseAclfBusInfo {
	private String busId;  
	private String busName;  
	
	private String busType;  // PV, PQ, Swing, Load
	private double busVoltageMagnitude;
	private double busVoltageAnlgle;
	
	private Complex busGeneration;
	private Complex busLoad;
	
	public AclfBusInfo() {
	}
	
	public AclfBusInfo(int areaNo, String areaName, int zoneNo, String zoneName,
			String busId, String busName, String busType, double busVoltageMagnitude, double busVoltageAnlgle,
			Complex busGeneration, Complex busLoad) {
		super(areaNo, areaName, zoneNo, zoneName);
		this.busId = busId;
		this.busName = busName;
		this.busType = busType;
		this.busVoltageMagnitude = busVoltageMagnitude;
		this.busVoltageAnlgle = busVoltageAnlgle;
		this.busGeneration = busGeneration;
		this.busLoad = busLoad;
	}

	public String getBusId() {
		return busId;
	}

	public void setBusId(String busId) {
		this.busId = busId;
	}

	public String getBusName() {
		return busName;
	}

	public void setBusName(String busName) {
		this.busName = busName;
	}

	public String getBusType() {
		return busType;
	}

	public void setBusType(String busType) {
		this.busType = busType;
	}

	public double getBusVoltageMagnitude() {
		return busVoltageMagnitude;
	}

	public void setBusVoltageMagnitude(double busVoltageMagnitude) {
		this.busVoltageMagnitude = busVoltageMagnitude;
	}

	public double getBusVoltageAnlgle() {
		return busVoltageAnlgle;
	}

	public void setBusVoltageAnlgle(double busVoltageAnlgle) {
		this.busVoltageAnlgle = busVoltageAnlgle;
	}

	public Complex getBusGeneration() {
		return busGeneration;
	}

	public void setBusGeneration(Complex busGeneration) {
		this.busGeneration = busGeneration;
	}

	public Complex getBusLoad() {
		return busLoad;
	}

	public void setBusLoad(Complex busLoad) {
		this.busLoad = busLoad;
	}
}
