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
		sb.append("zmatrix: "+this.zMtx.toString()+"\n");
		sb.append("ymatrix: "+ (this.shuntYMtx==null? "":this.shuntYMtx.toString())+"\n");

		return sb.toString();
	}




}
