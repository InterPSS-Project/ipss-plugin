package org.interpss.plugin.optadj.result;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.interpss.datatype.base.BaseJSONBean;


/** 

* @author  Donghao.F 

* @date 2023 Mar 11 09:50:58 

* 

*/
public class SsaResultContainer extends BaseJSONBean{
	double lodfThreshold = 0.2;
	
	List<SsaBranchOverLimitInfo> baseOverLimitInfo;
	
	List<SsaBranchOverLimitInfo> caOverLimitInfo;

	List<SsaBranchOverLimitInfo> largeLODFInfo;
	
	public SsaResultContainer(double lodfThreshold) {
		super();
		this.lodfThreshold = lodfThreshold;
		baseOverLimitInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
		caOverLimitInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
		largeLODFInfo = new CopyOnWriteArrayList<SsaBranchOverLimitInfo>();
	}
	
	public double getLodfThreshold() {
		return lodfThreshold;
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
