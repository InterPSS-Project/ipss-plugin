package org.interpss.plugin.optadj.config;
/** 

* @author  Donghao.F 

* @date 2025��3��5�� ����10:10:58 

* 

*/
public class OPtAdjControlLimit {

	String genName;
	
	int index;
	
	double origin;
	
	double pMax;
	
	double pMin;

	public OPtAdjControlLimit(String genName, double origin, double pMax, double pMin) {
		super();
		this.genName = genName;
		this.origin = origin;
		this.pMax = pMax;
		this.pMin = pMin;
	}

	public String getGenName() {
		return genName;
	}

	public void setGenName(String genName) {
		this.genName = genName;
	}

	public double getPMax() {
		return pMax;
	}

	public void setPMax(double pMax) {
		this.pMax = pMax;
	}

	public double getPMin() {
		return pMin;
	}

	public void setPMin(double pMin) {
		this.pMin = pMin;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public double getOrigin() {
		return origin;
	}
	
	
	
	
}
