package org.interpss.plugin.optadj.result;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.interpss.datatype.base.BaseJSONBean;


/** 

* @author  Donghao.F 

* @date 2023 Mar 11 09:50:58 

* 

*/
public class SsaResultOneStateInfo extends BaseJSONBean{
	double lodfThreshold = 0.2;
	
	int timePoint = -1;
	
	List<SsaBranchOverLimitInfo> baseOverLimitInfo;
	
	List<SsaBranchOverLimitInfo> caOverLimitInfo;

	List<SsaBranchOverLimitInfo> largeLODFInfo;
	
	public SsaResultOneStateInfo(double lodfThreshold) {
		super();
		this.lodfThreshold = lodfThreshold;
		baseOverLimitInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
		caOverLimitInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
		largeLODFInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
	}
	
	public SsaResultOneStateInfo(int timePoint, double lodfThreshold) {
		this(lodfThreshold);
		this.timePoint = timePoint;
	}
	
	public SsaResultOneStateInfo(int timePoint) {
		this(timePoint, 1.1);
	}
	
	public double getLodfThreshold() {
		return lodfThreshold;
	}
	
	public int getTimePoint() {
		return timePoint;
	}

	public void setTimePoint(int timePoint) {
		this.timePoint = timePoint;
	}

	public List<SsaBranchOverLimitInfo> getBaseOverLimitInfo() {
		return baseOverLimitInfo;
	}

	public void setBaseOverLimitInfo(List<SsaBranchOverLimitInfo> baseOverLimitInfo) {
		this.baseOverLimitInfo = baseOverLimitInfo;
	}

	public List<SsaBranchOverLimitInfo> getCaOverLimitInfo() {
		return caOverLimitInfo;
	}

	public void setCaOverLimitInfo(List<SsaBranchOverLimitInfo> caOverLimitInfo) {
		this.caOverLimitInfo = caOverLimitInfo;
	}

	public List<SsaBranchOverLimitInfo> getLargeLODFInfo() {
		return largeLODFInfo;
	}

	public void setLargeLODFInfo(List<SsaBranchOverLimitInfo> largeLODFInfo) {
		this.largeLODFInfo = largeLODFInfo;
	}
}
