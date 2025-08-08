package org.interpss.result.aclf;

import java.util.LinkedList;
import java.util.List;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.result.aclf.bean.AclfBranchInfo;
import org.interpss.result.aclf.bean.AclfBusInfo;
import org.interpss.result.aclf.bean.AclfNetInfo;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;

public class LoadflowResultsHelper {
	private final AclfNetwork aclfNet;
	
	public LoadflowResultsHelper(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
	}
	
	public AclfNetInfo getNetResults() {
		AclfNetInfo results = new AclfNetInfo();

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
		
		return results;
	}
	
	public List<AclfBusInfo> getBusResults() {
		List<AclfBusInfo> busInfoList = new LinkedList<>();
		
		this.aclfNet.getBusList().stream()
				.filter(bus -> bus.isActive())
				.forEach(bus -> {
					AclfBusInfo busInfo = getBusResult(bus.getId());
					
					busInfoList.add(busInfo);
				});
		
		return busInfoList;
	}
	
	public AclfBusInfo getBusResult(String busId) {
		AclfBus bus = this.aclfNet.getBus(busId);
					
		AclfBusInfo busInfo = new AclfBusInfo();
					
		busInfo.setBusId(bus.getId());
		busInfo.setBusName(bus.getName());
					
		busInfo.setBusType(    // PV, PQ, Swing, Load
				bus.isSwing()? "Swing" :
					bus.isGenPV()? "PV" :
						bus.isGen()? "PQ" : "Load"
				);  
		busInfo.setBusVoltageMagnitude(bus.getVoltageMag());
		busInfo.setBusVoltageAnlgle(bus.getVoltageAng());
		
		busInfo.setBusGeneration(bus.calNetGenResults());
		busInfo.setBusLoad(bus.calNetLoadResults());
		
		return busInfo;
	}

	
	public List<AclfBranchInfo> getBranchResults() {
		List<AclfBranchInfo> braInfoList = new LinkedList<>();
		
		this.aclfNet.getBranchList().stream()
				.filter(branch -> branch.isActive())
				.forEach(branch -> {
					AclfBranchInfo branchInfo = getBranchResult(branch.getId());
					braInfoList.add(branchInfo);
				});
		
		return braInfoList;
	}
	
	public AclfBranchInfo getBranchResult(String branchId) {
		AclfBranch branch = this.aclfNet.getBranch(branchId);
		
		AclfBranchInfo branchInfo = new AclfBranchInfo();
					
		branchInfo.setBranchId(branch.getId());
		branchInfo.setBranchName(branch.getName());
					
		branchInfo.setBranchType(branch.isLine()? "AcLine" : "Transformer");  // AcLine, Transformer
					
		branchInfo.setBranchPowerFlowFromSide2ToSide(branch.powerFrom2To());
		branchInfo.setBranchPowerFlowToSide2FromSide(branch.powerTo2From());
		
		return branchInfo;
	}
}
