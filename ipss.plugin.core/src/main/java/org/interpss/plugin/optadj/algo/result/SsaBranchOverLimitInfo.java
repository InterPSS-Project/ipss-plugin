package org.interpss.plugin.optadj.algo.result;

import org.interpss.datatype.base.BaseJSONBean;

/** 

* @author  Donghao.F
* @date 2023 Mar 11 09:50:58 
* 
*/

public class SsaBranchOverLimitInfo extends BaseJSONBean{
	
	String outageBranchId;
	
	String overLimitBranchId;
	
	double limitKA;
	
	double baseFlowKA;
	
	double calFlowKA;
	
	double overFlowMW;

	double baseFlowMW;

	double limitFlowMw;
	
	String fromGeoArea;
	
	String toGeoArea;
	
	
	public SsaBranchOverLimitInfo(String overLimitBranchId, double limit, double baseFlow, double overFlow) {
		super();
		this.overLimitBranchId = overLimitBranchId;
		this.limitKA = limit;
		this.baseFlowKA = baseFlow;
		this.overFlowMW = overFlow;
	}

	public SsaBranchOverLimitInfo(String outageBranchId, String overLimitBranchId, double limit, double baseFlow, double calFlow,
			double overFlowMW) {
		super();
		this.outageBranchId = outageBranchId;
		this.overLimitBranchId = overLimitBranchId;
		this.limitKA = limit;
		this.baseFlowKA = baseFlow;
		this.calFlowKA = calFlow;
		this.overFlowMW = overFlowMW;
	}
	
	public SsaBranchOverLimitInfo(String overLimitBranchId, double limitKA, double baseFlowKA, double overFlowMW,
			String fromGeoArea, String toGeoArea) {
		super();
		this.overLimitBranchId = overLimitBranchId;
		this.limitKA = limitKA;
		this.baseFlowKA = baseFlowKA;
		this.overFlowMW = overFlowMW;
		this.fromGeoArea = fromGeoArea;
		this.toGeoArea = toGeoArea;
	}

	public String getOutageBranchId() {
		return outageBranchId;
	}

	public void setOutageBranchId(String outageBranchId) {
		this.outageBranchId = outageBranchId;
	}

	public String getOverLimitBranchId() {
		return overLimitBranchId;
	}

	public void setOverLimitBranchId(String overLimitBranchId) {
		this.overLimitBranchId = overLimitBranchId;
	}

	public double getLimitKA() {
		return limitKA;
	}

	public void setLimitKA(double limitKA) {
		this.limitKA = limitKA;
	}

	public double getBaseFlowKA() {
		return baseFlowKA;
	}

	public void setBaseFlowKA(double baseFlowKA) {
		this.baseFlowKA = baseFlowKA;
	}

	public double getCalFlowKA() {
		return calFlowKA;
	}

	public void setCalFlowKA(double calFlowKA) {
		this.calFlowKA = calFlowKA;
	}

	public double getOverFlowMW() {
		return overFlowMW;
	}

	public void setOverFlowMW(double overFlowMW) {
		this.overFlowMW = overFlowMW;
	}

	public String getFromGeoArea() {
		return fromGeoArea;
	}

	public void setFromGeoArea(String fromGeoArea) {
		this.fromGeoArea = fromGeoArea;
	}

	public String getToGeoArea() {
		return toGeoArea;
	}

	public void setToGeoArea(String toGeoArea) {
		this.toGeoArea = toGeoArea;
	}

	public double getBaseFlowMW() {
		return baseFlowMW;
	}

	public void setBaseFlowMW(double baseFlowMW) {
		this.baseFlowMW = baseFlowMW;
	}

	public double getLimitFlowMw() {
		return limitFlowMw;
	}

	public void setLimitFlowMw(double limitFlowMw) {
		this.limitFlowMw = limitFlowMw;
	}
	
	
	
}
