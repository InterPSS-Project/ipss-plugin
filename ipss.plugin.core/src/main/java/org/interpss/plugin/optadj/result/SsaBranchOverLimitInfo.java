package org.interpss.plugin.optadj.result;

import org.interpss.datatype.base.BaseJSONBean;

/** 

* @author  Donghao.F
* @date 2023 Mar 11 09:50:58 
* 
*/

public class SsaBranchOverLimitInfo extends BaseJSONBean{
	
	String outageBranchId;
	
	String overLimitBranchId;
	
	double lodf;
	
	double limitKA;
	
	double baseFlowKA;
	
	double calFlowKA;
	
	double overFlowMW;

	double baseFlowMW;

	double calFlowMW;
	
	String fromGeoArea;
	
	String toGeoArea;
	
	
	public SsaBranchOverLimitInfo(String overLimitBranchId, double lodf, double limit, double baseFlow, double overFlow) {
		super();
		this.overLimitBranchId = overLimitBranchId;
		this.lodf = lodf;
		this.limitKA = limit;
		this.baseFlowKA = baseFlow;
		this.overFlowMW = overFlow;
	}

	public SsaBranchOverLimitInfo(String outageBranchId, String overLimitBranchId, double lodf, double limitKA,
			double baseFlowKA, double calFlowKA, double calFlowMW, double overFlowMW) {
		this(overLimitBranchId, lodf, limitKA, baseFlowKA, overFlowMW);
		this.outageBranchId = outageBranchId;
		this.calFlowKA = calFlowKA;
		this.calFlowMW = calFlowMW;
	}
	
	public SsaBranchOverLimitInfo(String overLimitBranchId, double lpdf, double limitKA, double baseFlowKA, double overFlowMW,
			String fromGeoArea, String toGeoArea) {
		this(overLimitBranchId, lpdf, limitKA, baseFlowKA, overFlowMW);
		this.fromGeoArea = fromGeoArea;
		this.toGeoArea = toGeoArea;
	}

	public String getOutageBranchId() {
		return outageBranchId;
	}

	public String getOverLimitBranchId() {
		return overLimitBranchId;
	}

	public double getLodf() {
		return lodf;
	}

	public double getLimitKA() {
		return limitKA;
	}

	public double getBaseFlowKA() {
		return baseFlowKA;
	}

	public double getCalFlowKA() {
		return calFlowKA;
	}

	public double getOverFlowMW() {
		return overFlowMW;
	}

	public double getBaseFlowMW() {
		return baseFlowMW;
	}

	public String getFromGeoArea() {
		return fromGeoArea;
	}

	public String getToGeoArea() {
		return toGeoArea;
	}

	public void setFromGeoArea(String fromGeoArea) {
		this.fromGeoArea = fromGeoArea;
	}

	public void setToGeoArea(String toGeoArea) {
		this.toGeoArea = toGeoArea;
	}

	public double getCalFlowMW() {
		return calFlowMW;
	}


	
	
}
