package org.interpss.result.aclf;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.result.aclf.bean.AclfBranchInfo;
import org.interpss.result.aclf.bean.AclfBusInfo;
import org.interpss.result.aclf.bean.AclfHvdcInfo;
import org.interpss.result.aclf.bean.AclfNetInfo;
import org.interpss.result.aclf.bean.AclfSvcInfo;

public class AclfResultContainer extends AclfNetInfo {
	private List<AclfBusInfo> busResults;
	private List<AclfSvcInfo> svcResults;
	
	private List<AclfBranchInfo> branchResults;
	private List<AclfHvdcInfo> hvdcResults;
	
	public AclfResultContainer() {
		super();
	}
	
	public AclfResultContainer(String networkId, String networkName, String caseDescription, int numberOfBuses,
			int numberOfBranches, boolean loadflowConverged, Complex maxMismatch, Complex totalGeneration,
			Complex totalLoad) {
		super(networkId, networkName, caseDescription, numberOfBuses, numberOfBranches, loadflowConverged, 
				maxMismatch, totalGeneration, totalLoad);
		this.busResults = new LinkedList<>();
		this.svcResults = new LinkedList<>();
		this.branchResults = new LinkedList<>();
		this.hvdcResults = new LinkedList<>();
	}
	
	public List<AclfBusInfo> getBusResults() {
		return busResults;
	}
	
	public List<AclfSvcInfo> getSvcResults() {
		return svcResults;
	}

	public List<AclfBranchInfo> getBranchResults() {
		return branchResults;
	}

	public List<AclfHvdcInfo> getHvdcResults() {
		return hvdcResults;
	}
}
