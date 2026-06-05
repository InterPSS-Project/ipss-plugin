package org.interpss.plugin.optadj.result;

import org.interpss.datatype.base.BaseJSONBean;

/** 

* @author  Donghao.F
* @date 2023 Mar 11 09:50:58 
* 
*/

public class SsaBranchOverLimitInfo extends BaseJSONBean{
	private String overLimitBranchId;
	
	private double limitMW;
	
	private double baseFlowMW;

	private String outageBranchId;

	private double shftedFlowMW;

	private double loadingPercent;
	
	/**
	 * Constructor for base case over limit info
	 * 
	 * @param overLimitBranchId
	 * @param limitMw
	 * @param baseFlowMw
	 */
	public SsaBranchOverLimitInfo(String overLimitBranchId, double limitMw, double baseFlowMw) {
		super();
		this.overLimitBranchId = overLimitBranchId;
		this.limitMW = limitMw;
		this.baseFlowMW = baseFlowMw;
		this.loadingPercent = 100.0 * Math.abs(baseFlowMW) / limitMW;
	}
	/**
	 * Constructor for contingency over limit info
	 * 
	 * @param outageBranchId
	 * @param overLimitBranchId
	 * @param limitMw
	 * @param baseFlowMw
	 * @param shftedFlowMw
	 */
	public SsaBranchOverLimitInfo(String outageBranchId, String overLimitBranchId, double limitMw, double baseFlowMw, double shftedFlowMw) {
		this(overLimitBranchId, limitMw, baseFlowMw);
		this.outageBranchId = outageBranchId;
		this.shftedFlowMW = shftedFlowMw;
		this.loadingPercent = 100.0 * Math.abs(baseFlowMw + shftedFlowMW) / limitMW;
	}

	public String getOutageBranchId() {
		return outageBranchId;
	}

	public String getOverLimitBranchId() {
		return overLimitBranchId;
	}

	public double getBaseFlowMW() {
		return baseFlowMW;
	}	

	public double getLimitMW() {
		return limitMW;
	}

	public double getShftedFlowMW() {
		return shftedFlowMW;
	}
}
