package org.interpss.plugin.result.bean;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datatype.base.BaseJSONBean;

public class AclfNetInfo extends BaseJSONBean {
	private String networkId;
	private String networkName;
	private String caseDescription;
	
	private int numberOfBuses;
	private int numberOfBranches;
	
	private boolean LoadflowConverged;
	private Complex maxMismatch;
	
	private Complex totalGeneration;
	private Complex totalLoad;
	
	public AclfNetInfo() {
	}
	
	public AclfNetInfo(String networkId, String networkName, String caseDescription, int numberOfBuses,
			int numberOfBranches, boolean loadflowConverged, Complex maxMismatch, Complex totalGeneration,
			Complex totalLoad) {
		super();
		this.networkId = networkId;
		this.networkName = networkName;
		this.caseDescription = caseDescription;
		this.numberOfBuses = numberOfBuses;
		this.numberOfBranches = numberOfBranches;
		LoadflowConverged = loadflowConverged;
		this.maxMismatch = maxMismatch;
		this.totalGeneration = totalGeneration;
		this.totalLoad = totalLoad;
	}

	public String getNetworkId() {
		return networkId;
	}

	public void setNetworkId(String networkId) {
		this.networkId = networkId;
	}

	public String getNetworkName() {
		return networkName;
	}

	public void setNetworkName(String networkName) {
		this.networkName = networkName;
	}

	public String getCaseDescription() {
		return caseDescription;
	}

	public void setCaseDescription(String caseDescription) {
		this.caseDescription = caseDescription;
	}

	public int getNumberOfBuses() {
		return numberOfBuses;
	}

	public void setNumberOfBuses(int numberOfBuses) {
		this.numberOfBuses = numberOfBuses;
	}

	public int getNumberOfBranches() {
		return numberOfBranches;
	}

	public void setNumberOfBranches(int numberOfBranches) {
		this.numberOfBranches = numberOfBranches;
	}

	public boolean isLoadflowConverged() {
		return LoadflowConverged;
	}

	public void setLoadflowConverged(boolean loadflowConverged) {
		LoadflowConverged = loadflowConverged;
	}

	public Complex getMaxMismatch() {
		return maxMismatch;
	}

	public void setMaxMismatch(Complex maxMismatch) {
		this.maxMismatch = maxMismatch;
	}

	public Complex getTotalGeneration() {
		return totalGeneration;
	}

	public void setTotalGeneration(Complex totalGeneration) {
		this.totalGeneration = totalGeneration;
	}

	public Complex getTotalLoad() {
		return totalLoad;
	}

	public void setTotalLoad(Complex totalLoad) {
		this.totalLoad = totalLoad;
	}
}
