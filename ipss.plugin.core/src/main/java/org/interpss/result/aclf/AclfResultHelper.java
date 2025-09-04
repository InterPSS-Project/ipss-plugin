package org.interpss.result.aclf;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.result.aclf.bean.AclfBranchInfo;
import org.interpss.result.aclf.bean.AclfBusInfo;
import org.interpss.result.aclf.bean.AclfGenInfo;
import org.interpss.result.aclf.bean.AclfLoadInfo;
import org.interpss.result.aclf.bean.AclfNetInfo;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;

public class AclfResultHelper {
	// The ACLF network object from which results are to be extracted
	private final AclfNetwork aclfNet;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet the AclfNetwork object containing load flow results
	 */
	public AclfResultHelper(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
	}
	
	/**
	 * Retrieves network-level load flow results.
	 * 
	 * @return AclfNetInfo object containing summarized network results
	 */
	public AclfNetInfo getNetResults() {
		AclfNetInfo results = new AclfNetInfo();
		
		createNetResults(results);
		
		return results;
	}
	
	/**
	 * Populates the provided AclfNetInfo object with network-level results.
	 * 
	 * @param results the AclfNetInfo object to populate
	 */
	public void createNetResults(AclfNetInfo results) {
		if (this.aclfNet == null) {
			results.setCaseDescription("No ACLF network load flow calculation results");
		}
		else {
			results.setCaseDescription("ACLF network load flow calculation results");
			
			results.setNetworkId(this.aclfNet.getId());
			results.setNetworkName(this.aclfNet.getName());
	
			results.setNumberOfBuses(this.aclfNet.getNoActiveBus());
			results.setNumberOfBranches(this.aclfNet.getNoActiveBranch());
			
			results.setLoadflowConverged(this.aclfNet.isLfConverged());
			results.setMaxMismatch(this.aclfNet.maxMismatch(AclfMethodType.NR).maxMis);
			
			results.setTotalGeneration(this.aclfNet.totalGeneration(UnitType.mVA));
			results.setTotalLoad(this.aclfNet.totalLoad(UnitType.mVA));
		}
	}
	
	/** 
	 * create and return Bus Results 
	 * 
	 * @return List of AclfBusInfo objects containing bus-level results
	 */
	public List<AclfBusInfo> getBusResults() {
		List<AclfBusInfo> busInfoList = new LinkedList<>();
		createBusResults(busInfoList, (b1, b2) -> 0, this.aclfNet.getNoActiveBus());
		return busInfoList;
	}
	
	/** 
	 * Populate the provided list with bus-level results.
	 * 
	 * @param busInfoList the list to populate with AclfBusInfo objects
	 * @param busComparator comparator to sort buses before processing
	 * @param maxNumOfBusResults maximum number of bus results to include
	 */
	public void createBusResults(List<AclfBusInfo> busInfoList, 
					Comparator<AclfBus> busComparator, int maxNumOfBusResults) {
		Counter cnt = new Counter();
		this.aclfNet.getBusList().stream()
				.sorted(busComparator)
				.filter(bus -> bus.isActive() && cnt.increment() <= maxNumOfBusResults)
				.forEach(bus -> {
					AclfBusInfo busInfo = getBusResult(bus.getId());
					
					busInfoList.add(busInfo);
				});
	}
	
	/** 
	 * Get results for a specific bus by its ID.
	 * 
	 * @param busId the ID of the bus
	 * @return AclfBusInfo object containing results for the specified bus
	 */
	public AclfBusInfo getBusResult(String busId) {
		AclfBus bus = this.aclfNet.getBus(busId);
		double baseMva = this.aclfNet.getBaseMva();
					
		AclfBusInfo busInfo = new AclfBusInfo();
					
		busInfo.setBusId(bus.getId());
		busInfo.setBusName(bus.getName());
					
		busInfo.setBusType(    // PV, PQ, Swing, Load
				bus.isSwing()? "Swing" :
					bus.isGenPV()? "PV" :
						bus.isGen()? "PQ" : "Load"
				);  
		busInfo.setBusVoltageMagnitude(bus.getVoltageMag());
		busInfo.setBusVoltageAnlgle(Math.toDegrees(bus.getVoltageAng()));
		
		busInfo.setBusGeneration(bus.calNetGenResults().multiply(baseMva));
		busInfo.setBusLoad(bus.calNetLoadResults().multiply(baseMva));
		
		return busInfo;
	}
	
	/** 
	 * create and return Gen Results 
	 * 
	 * @return List of AclfGenInfo objects containing bus-level gen results
	 */
	public List<AclfGenInfo> getGenResults() {
		List<AclfGenInfo> genInfoList = new LinkedList<>();
		createGenResults(genInfoList, (b1, b2) -> 0, 0);
		return genInfoList;
	}
	
	/** 
	 * Populate the provided list with bus-level gen results.
	 * 
	 * @param genInfoList the list to populate with AclfGenInfo objects
	 * @param genComparator comparator to sort gens before processing
	 * @param maxNumOfGenResults maximum number of gen results to include, 0 means all
	 */
	public void createGenResults(List<AclfGenInfo> genInfoList, 
					Comparator<AclfGen> genComparator, int maxNumOfGenResults) {
		List<AclfGen> genList = new LinkedList<>();
		this.aclfNet.getBusList().forEach(bus -> {
			if (bus.isActive() && bus.getContributeGenList().size() > 0)
				bus.getContributeGenList().forEach(gen -> {
					if (gen.isActive())
						genList.add(gen);
				});
		});
		
		Counter cnt = new Counter();
		genList.stream()
				.sorted(genComparator)
				.filter(gen -> gen.isActive() && 
						(cnt.increment() <= maxNumOfGenResults || maxNumOfGenResults == 0))
				.forEach(gen -> {
					AclfGenInfo genInfo = getGenResult(gen);
					genInfoList.add(genInfo);
				});
	}
	
	/** 
	 * Get results for a specific gen.
	 * 
	 * @param gen the gen object	
	 * @return AclfGenInfo object containing results for the specified gen
	 */
	public AclfGenInfo getGenResult(AclfGen gen) {
		AclfBus bus = (AclfBus)gen.getParentBus();
		double baseMva = this.aclfNet.getBaseMva();
					
		AclfGenInfo genInfo = new AclfGenInfo();
					
		genInfo.setBusId(bus.getId());
		genInfo.setGenId(gen.getId());
		genInfo.setGenName(gen.getName());
		/*
		busInfo.setBusName(bus.getName());
					
		busInfo.setBusType(    // PV, PQ, Swing, Load
				bus.isSwing()? "Swing" :
					bus.isGenPV()? "PV" :
						bus.isGen()? "PQ" : "Load"
				);  
		busInfo.setBusVoltageMagnitude(bus.getVoltageMag());
		busInfo.setBusVoltageAnlgle(Math.toDegrees(bus.getVoltageAng()));
		
		busInfo.setBusGeneration(bus.calNetGenResults().multiply(baseMva));
		busInfo.setBusLoad(bus.calNetLoadResults().multiply(baseMva));
		*/
		return genInfo;
	}

	/** 
	 * create and return Load Results 
	 * 
	 * @return List of AclfLoadInfo objects containing bus-level load results
	 */
	public List<AclfLoadInfo> getLoadResults() {
		List<AclfLoadInfo> loadInfoList = new LinkedList<>();
		createLoadResults(loadInfoList, (b1, b2) -> 0, 0);
		return loadInfoList;
	}

	/** 
	 * Populate the provided list with bus-level load results.
	 * 
	 * @param loadInfoList the list to populate with AclfLoadInfo objects
	 * @param loadComparator comparator to sort loads before processing
	 * @param maxNumOfLoadResults maximum number of load results to include, 0 means all
	 */
	public void createLoadResults(List<AclfLoadInfo> loadInfoList, 
					Comparator<AclfLoad> loadComparator, int maxNumOfLoadResults) {
		List<AclfLoad> loadList = new LinkedList<>();
		this.aclfNet.getBusList().forEach(bus -> {
			if (bus.isActive() && bus.getContributeLoadList().size() > 0)
				bus.getContributeLoadList().forEach(load -> {
					if (load.isActive())
						loadList.add(load);
				});
		});
		
		Counter cnt = new Counter();
		loadList.stream()
				.sorted(loadComparator)
				.filter(load -> load.isActive() && 
						(cnt.increment() <= maxNumOfLoadResults || maxNumOfLoadResults == 0))
				.forEach(load -> {
					AclfLoadInfo loadInfo = getLoadResult(load);
					loadInfoList.add(loadInfo);
				});
	}
	
	/** 
	 * Get results for a specific load.
	 * 
	 * @param load the gen object	
	 * @return AclfLoadInfo object containing results for the specified load
	 */
	public AclfLoadInfo getLoadResult(AclfLoad load) {
		AclfBus bus = (AclfBus)load.getParentBus();
		double baseMva = this.aclfNet.getBaseMva();
					
		AclfLoadInfo loadInfo = new AclfLoadInfo();
					
		loadInfo.setBusId(bus.getId());
		loadInfo.setLoadId(load.getId());
		loadInfo.setLoadName(load.getName());
		/*
		busInfo.setBusName(bus.getName());
					
		busInfo.setBusType(    // PV, PQ, Swing, Load
				bus.isSwing()? "Swing" :
					bus.isGenPV()? "PV" :
						bus.isGen()? "PQ" : "Load"
				);  
		busInfo.setBusVoltageMagnitude(bus.getVoltageMag());
		busInfo.setBusVoltageAnlgle(Math.toDegrees(bus.getVoltageAng()));
		
		busInfo.setBusGeneration(bus.calNetGenResults().multiply(baseMva));
		busInfo.setBusLoad(bus.calNetLoadResults().multiply(baseMva));
		*/
		return loadInfo;
	}
	
	/** 
	 * Create and return Branch Results 
	 * 
	 * @return List of AclfBranchInfo objects containing branch-level results
	 */
	public List<AclfBranchInfo> getBranchResults() {
		List<AclfBranchInfo> braInfoList = new LinkedList<>();
		createBranchResults(braInfoList, (b1, b2) -> 0, this.aclfNet.getNoActiveBranch());
		return braInfoList;
	}
	
	/** 
	 * Populate the provided list with branch-level results.
	 * 
	 * @param branchInfoList the list to populate with AclfBranchInfo objects
	 */
	public void createBranchResults(List<AclfBranchInfo> branchInfoList, 
						Comparator<AclfBranch> branchComparator, int maxNumOfBranchResults) {
		Counter cnt = new Counter();
		this.aclfNet.getBranchList().stream()
				.sorted(branchComparator) 
				.filter(branch -> branch.isActive() && cnt.increment() <= maxNumOfBranchResults)
				.forEach(branch -> {
					AclfBranchInfo branchInfo = getBranchResult(branch.getId());
					branchInfoList.add(branchInfo);
				});
	}
	
	/** 
	 * Get results for a specific branch by its ID.
	 * 
	 * @param branchId the ID of the branch
	 * @return AclfBranchInfo object containing results for the specified branch
	 */
	public AclfBranchInfo getBranchResult(String branchId) {
		AclfBranch branch = this.aclfNet.getBranch(branchId);
		
		AclfBranchInfo branchInfo = new AclfBranchInfo();
					
		branchInfo.setBranchId(branch.getId());
		branchInfo.setBranchName(branch.getName());
					
		branchInfo.setBranchType(branch.isLine()? "AcLine" : "Transformer");  // AcLine, Transformer
					
		branchInfo.setBranchPowerFlowFromSide2ToSide(branch.powerFrom2To(UnitType.mVA));
		branchInfo.setBranchPowerFlowToSide2FromSide(branch.powerTo2From(UnitType.mVA));
		
		return branchInfo;
	}
}
