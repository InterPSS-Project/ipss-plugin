package org.interpss.result.aclf.bean;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datatype.base.BaseJSONBean;


public class AclfBranchInfo extends BaseJSONBean {
	private String branchId;  
	private String branchName;  
	
	private String branchType;  // AcLine, Transformer
	
	private Complex branchPowerFlowFromSide2ToSide;
	private Complex branchPowerFlowToSide2FromSide;
	
	public AclfBranchInfo() {
	}

	public AclfBranchInfo(String branchId, String branchName, String branchType, Complex branchPowerFlowFromSide2ToSide,
			Complex branchPowerFlowToSide2FromSide) {
		super();
		this.branchId = branchId;
		this.branchName = branchName;
		this.branchType = branchType;
		this.branchPowerFlowFromSide2ToSide = branchPowerFlowFromSide2ToSide;
		this.branchPowerFlowToSide2FromSide = branchPowerFlowToSide2FromSide;
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

	public Complex getBranchPowerFlowFromSide2ToSide() {
		return branchPowerFlowFromSide2ToSide;
	}

	public void setBranchPowerFlowFromSide2ToSide(Complex branchPowerFlowFromSide2ToSide) {
		this.branchPowerFlowFromSide2ToSide = branchPowerFlowFromSide2ToSide;
	}

	public Complex getBranchPowerFlowToSide2FromSide() {
		return branchPowerFlowToSide2FromSide;
	}

	public void setBranchPowerFlowToSide2FromSide(Complex branchPowerFlowToSide2FromSide) {
		this.branchPowerFlowToSide2FromSide = branchPowerFlowToSide2FromSide;
	}
}
