package org.interpss.plugin.result;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datatype.base.BaseJSONBean;
import org.interpss.plugin.result.bean.AclfBranchInfo;
import org.interpss.plugin.result.bean.AclfBusInfo;
import org.interpss.plugin.result.bean.AclfGenInfo;
import org.interpss.plugin.result.bean.AclfHvdcInfo;
import org.interpss.plugin.result.bean.AclfLoadInfo;
import org.interpss.plugin.result.bean.AclfNetInfo;
import org.interpss.plugin.result.bean.AclfSvcInfo;

/**
 * Container class for holding the loadflow results
 * 
 * @author mzhou
 *
 */
public class AclfResultContainer extends BaseJSONBean {
	// Network related results
	private AclfNetInfo netResults;
	
	// Bus related results
	private List<AclfBusInfo> busResults;
	// Generator related results
	private List<AclfGenInfo> genResults;
	// Load related results
	private List<AclfLoadInfo> loadResults;
	// SVC related results
	private List<AclfSvcInfo> svcResults;
	
	// Branch related results
	private List<AclfBranchInfo> branchResults;
	// HVDC related results
	private List<AclfHvdcInfo> hvdcResults;
	
	/**
	 * Default constructor
	 */
	public AclfResultContainer() {
		super();
		this.netResults = new AclfNetInfo();
		this.busResults = new LinkedList<>();
		this.genResults = new LinkedList<>();
		this.loadResults = new LinkedList<>();
		this.svcResults = new LinkedList<>();
		this.branchResults = new LinkedList<>();
		this.hvdcResults = new LinkedList<>();
	}
	
	/**
	 * Constructor
	 * 
	 * @param networkId
	 * @param networkName
	 * @param caseDescription
	 * @param numberOfBuses
	 * @param numberOfBranches
	 * @param loadflowConverged
	 * @param maxMismatch
	 * @param totalGeneration
	 * @param totalLoad
	 */
	public AclfResultContainer(String networkId, String networkName, String caseDescription, 
			int numberOfBuses, int numberOfBranches, 
			boolean loadflowConverged, Complex maxMismatch, 
			Complex totalGeneration, Complex totalLoad) {
		super();
		this.netResults = new AclfNetInfo(networkId, networkName, caseDescription, 
				numberOfBuses, numberOfBranches, 
				loadflowConverged, maxMismatch, 
				totalGeneration, totalLoad);
		this.busResults = new LinkedList<>();
		this.genResults = new LinkedList<>();
		this.loadResults = new LinkedList<>();
		this.svcResults = new LinkedList<>();
		this.branchResults = new LinkedList<>();
		this.hvdcResults = new LinkedList<>();
	}
	
	public AclfNetInfo getNetResults() {
		return netResults;
	}
	
	public List<AclfBusInfo> getBusResults() {
		return busResults;
	}
	
	public List<AclfGenInfo> getGenResults() {
		return genResults;
	}
	
	public List<AclfLoadInfo> getLoadResults() {
		return loadResults;
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
