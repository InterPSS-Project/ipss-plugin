package org.interpss.plugin.result.bean;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datatype.base.BaseJSONBean;


public class AclfBranchInfo extends BaseJSONBean {
	private String branchId;  
	private String branchName;  
	
	private String branchType;  // AcLine, Transformer
	
	private Complex branchPowerFlowFrom2To;
	private Complex branchPowerFlowTo2From;
	
	public AclfBranchInfo() {
	}

	public AclfBranchInfo(String branchId, String branchName, String branchType, 
			Complex branchPowerFlowFrom2To,
			Complex branchPowerFlowTo2From) {
		super();
		this.branchId = branchId;
		this.branchName = branchName;
		this.branchType = branchType;
		this.branchPowerFlowFrom2To = branchPowerFlowFrom2To;
		this.branchPowerFlowTo2From = branchPowerFlowTo2From;
	}

	public String getBranchId() {
		return branchId;
	}

	public void setBranchId(String branchId) {
		this.branchId = branchId;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public String getBranchType() {
		return branchType;
	}

	public void setBranchType(String branchType) {
		this.branchType = branchType;
	}

	public Complex getBranchPowerFlowFrom2To() {
		return branchPowerFlowFrom2To;
	}

	public void setBranchPowerFlowSide2To(Complex branchPowerFlowFromSide2ToSide) {
		this.branchPowerFlowFrom2To = branchPowerFlowFromSide2ToSide;
	}

	public Complex getBranchPowerFlowTo2From() {
		return branchPowerFlowTo2From;
	}

	public void setBranchPowerFlowTo2From(Complex branchPowerFlowToSide2FromSide) {
		this.branchPowerFlowTo2From = branchPowerFlowToSide2FromSide;
	}
}
