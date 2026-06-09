package org.interpss.threePhase.basic;

import org.interpss.numeric.datatype.Complex3x3;

public class LineConfiguration {

	enum InputType { Physical, ZYMatrix, LineCode}
	enum LengthUnit {Feet, Mile, Meter, kM}


	private String id = "";
	private int nphases = 1;
	private int baseFreq = 60;
	private InputType type =null;

	//TODO units
	private String lengthUnit = "";
	private int neutralConductor = -1;
	private boolean kronReductionEnabled = false;
	private int kronReductionCount = 0;

	private Complex3x3 zMtx = null;

	private Complex3x3 shuntYMtx = null;

	//physical parameters


	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getNphases() {
		return nphases;
	}

	public void setNphases(int nphases) {
		this.nphases = nphases;
	}

	public int getBaseFreq() {
		return baseFreq;
	}

	public void setBaseFreq(int baseFreq) {
		this.baseFreq = baseFreq;
	}

	public InputType getType() {
		return type;
	}

	public void setType(InputType type) {
		this.type = type;
	}

	public String getLengthUnit() {
		return lengthUnit;
	}

	public void setLengthUnit(String lengthUnit) {
		this.lengthUnit = lengthUnit == null ? "" : lengthUnit;
	}

	public int getNeutralConductor() {
		return neutralConductor;
	}

	public void setNeutralConductor(int neutralConductor) {
		this.neutralConductor = neutralConductor;
	}

	public boolean isKronReductionEnabled() {
		return kronReductionEnabled;
	}

	public void setKronReductionEnabled(boolean kronReductionEnabled) {
		this.kronReductionEnabled = kronReductionEnabled;
	}

	public int getKronReductionCount() {
		return kronReductionCount;
	}

	public void addKronReduction() {
		this.kronReductionEnabled = true;
		this.kronReductionCount++;
	}

	public Complex3x3 getZ3x3Matrix() {
		return zMtx;
	}

	public void setZ3x3Matrix(Complex3x3 zMtx) {
		this.zMtx = zMtx;
	}

	public Complex3x3 getShuntY3x3Matrix() {
		return shuntYMtx;
	}

	public void setShuntY3x3Matrix(Complex3x3 shuntYMtx) {
		this.shuntYMtx = shuntYMtx;
	}


	public boolean calculateZYMatrixWithPhyiscalConfiguration(){
		return true;
	}

	@Override
	public String toString(){
		StringBuffer sb = new StringBuffer();
		sb.append("Line Configuration:\n");
		sb.append("Id: "+id+"\n");
		sb.append("InputType: "+type+"\n");
		sb.append("nphases: "+nphases+"\n");
		sb.append("baseFreq: "+baseFreq+"\n");
		sb.append("lengthUnit: "+lengthUnit+"\n");
		sb.append("zmatrix: "+this.zMtx.toString()+"\n");
		sb.append("ymatrix: "+ (this.shuntYMtx==null? "":this.shuntYMtx.toString())+"\n");

		return sb.toString();
	}




}
