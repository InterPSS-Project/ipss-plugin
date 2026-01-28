package org.interpss.plugin.result;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.dflib.DataFrame;
import org.dflib.Sorter;
import org.dflib.row.RowProxy;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.result.bean.AclfBranchInfo;
import org.interpss.plugin.result.bean.AclfBusInfo;
import org.interpss.plugin.result.bean.AclfGenInfo;
import org.interpss.plugin.result.bean.AclfLoadInfo;
import org.interpss.plugin.result.bean.AclfNetInfo;
import org.interpss.plugin.result.dframe.AclfNetDFrameAdapter;

import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;

public class AclfResultDFrameHelper {
	// The ACLF network object from which results are to be extracted
	private final AclfNetwork aclfNet;
	
	private final AclfNetDFrameAdapter dfAdapter;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet the AclfNetwork object containing load flow results
	 */
	public AclfResultDFrameHelper(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
		this.dfAdapter = new AclfNetDFrameAdapter();
	  	dfAdapter.adapt(aclfNet);
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
	 * create and return all Bus Results 
	 * 
	 * @return List of AclfBusInfo objects containing bus-level results
	 */
	public List<AclfBusInfo> getBusResults() {
		List<AclfBusInfo> busInfoList = new LinkedList<>();
		createBusResults(busInfoList, null, this.aclfNet.getNoActiveBus());
		return busInfoList;
	}
	
	/** 
	 * Populate the provided list with bus-level results with the sort rule and
	 * number of results points.
	 * 
	 * @param busInfoList the list to populate with AclfBusInfo objects
	 * @param sortExpr sort expression to sort buses before processing
	 * @param maxNumOfBusResults maximum number of bus results to include
	 */
	public void createBusResults(List<AclfBusInfo> busInfoList, 
			Sorter sortExpr, int maxNumOfBusResults) {
		DataFrame dfBus = this.dfAdapter.getDfBus();
		/*
		 * Note: DFLib sorting is not in-place. The sort() method returns a 
		 * new DataFrame instance with the rows reordered, preserving the original 
		 * DataFrame.
		 */
		if (sortExpr != null)
			dfBus = dfBus.sort(sortExpr);
		
		Counter cnt = new Counter();
		
		// Process sorted rows
		dfBus.forEach(row -> {
			if (cnt.increment() <= maxNumOfBusResults) {
				AclfBusInfo busInfo = getBusResult(row);
				busInfoList.add(busInfo);
			}
		});
	}
	
	/** 
	 * Get results for a specific bus row.
	 * 
	 * @param row the RowProxy object representing the bus
	 * @return AclfBusInfo object containing results for the specified bus
	 */
	public AclfBusInfo getBusResult(RowProxy row) {
		
		double baseMva = this.aclfNet.getBaseMva();
					
		AclfBusInfo busInfo = new AclfBusInfo();
					
		busInfo.setBusId((String)row.get("ID"));
		busInfo.setBusName((String)row.get("Name"));
					
		busInfo.setBusType((String)row.get("BusType"));

		//System.out.println("====>" + row.get("VoltMag"));
		busInfo.setBusVoltageMagnitude(row.getDouble("VoltMag"));
		busInfo.setBusVoltageAnlgle(Math.toDegrees(row.getDouble("VoltAng")));
		
		busInfo.setBusGeneration(new Complex(row.getDouble("GenP"),
											 row.getDouble("GenQ")).multiply(baseMva));
		busInfo.setBusLoad(new Complex(row.getDouble("LoadP"),
				 					   row.getDouble("LoadQ")).multiply(baseMva));
		
		return busInfo;
	}
	
	/** 
	 * create and return all Gen Results 
	 * 
	 * @return List of AclfGenInfo objects containing bus-level gen results
	 */
	public List<AclfGenInfo> getGenResults() {
		List<AclfGenInfo> genInfoList = new LinkedList<>();
		createGenResults(genInfoList, null, 0);
		return genInfoList;
	}
	
	/** 
	 * Populate the provided list with bus-level gen results, with the sort rules
	 * and number of points.
	 * 
	 * @param genInfoList the list to populate with AclfGenInfo objects
	 * @param sorter sort expression to sort gens before processing
	 * @param maxNumOfGenResults maximum number of gen results to include, 0 means all
	 */
	public void createGenResults(List<AclfGenInfo> genInfoList, 
					Sorter sortExpr, int maxNumOfGenResults) {
		DataFrame dfGen = this.dfAdapter.getDfGen();
		/*
		 * Note: DFLib sorting is not in-place. The sort() method returns a 
		 * new DataFrame instance with the rows reordered, preserving the original 
		 * DataFrame.
		 */
		if (sortExpr != null)
			dfGen = dfGen.sort(sortExpr);
		
		Counter cnt = new Counter();
		
		// Process sorted rows
		dfGen.forEach(row -> {
			if (cnt.increment() <= maxNumOfGenResults) {
				AclfGenInfo genInfo = getGenResult(row);
				genInfoList.add(genInfo);
			}
		});
	}
	
	/** 
	 * Get results for a specific gen.
	 * 
	 * @param row the RowProxy object representing the gen	
	 * @return AclfGenInfo object containing results for the specified gen
	 */
	public AclfGenInfo getGenResult(RowProxy row) {
		double baseMva = this.aclfNet.getBaseMva();
					
		AclfGenInfo genInfo = new AclfGenInfo();
					
		genInfo.setBusId((String)row.get("BusID"));
		genInfo.setGenId((String)row.get("GenID"));
		genInfo.setGenName((String)row.get("GenName"));
					
		genInfo.setGenType( (String)row.get("GenCode"));  // Swing, PV, PQ, NonGen
		
		genInfo.setGen(new Complex(row.getDouble("PGen"), row.getDouble("QGen")).multiply(baseMva));
		
		return genInfo;
	}

	/** 
	 * create and return all Load Results 
	 * 
	 * @return List of AclfLoadInfo objects containing bus-level load results
	 */
	public List<AclfLoadInfo> getLoadResults() {
		List<AclfLoadInfo> loadInfoList = new LinkedList<>();
		createLoadResults(loadInfoList, null, 0);
		return loadInfoList;
	}

	/** 
	 * Populate the provided list with bus-level load results, with the sort rules
	 * and number of points.
	 * 
	 * @param loadInfoList the list to populate with AclfLoadInfo objects
	 * @param sortExpr sort expression to sort loads before processing
	 * @param maxNumOfLoadResults maximum number of load results to include, 0 means all
	 */
	public void createLoadResults(List<AclfLoadInfo> loadInfoList, 
					Sorter sortExpr, int maxNumOfLoadResults) {
		DataFrame dfLoad = this.dfAdapter.getDfLoad();
		/*
		 * Note: DFLib sorting is not in-place. The sort() method returns a 
		 * new DataFrame instance with the rows reordered, preserving the original 
		 * DataFrame.
		 */
		if (sortExpr != null)
			dfLoad = dfLoad.sort(sortExpr);
		
		Counter cnt = new Counter();
		
		// Process sorted rows
		dfLoad.forEach(row -> {
			if (cnt.increment() <= maxNumOfLoadResults) {
				AclfLoadInfo loadInfo = getLoadResult(row);
				loadInfoList.add(loadInfo);
			}
		});
	}
	
	/** 
	 * Get results for a specific load.
	 * 
	 * @param row the RowProxy object representing the load 	
	 * @return AclfLoadInfo object containing results for the specified load
	 */
	public AclfLoadInfo getLoadResult(RowProxy row) {
		double baseMva = this.aclfNet.getBaseMva();
					
		AclfLoadInfo loadInfo = new AclfLoadInfo();
					
		loadInfo.setBusId((String)row.get("BusID"));
		loadInfo.setLoadId((String)row.get("LoadID"));
		loadInfo.setLoadName((String)row.get("LoadName"));
		
		loadInfo.setLoadType((String)row.get("LoadCode"));  // PQ, NonLoad
		
		loadInfo.setLoad(new Complex(row.getDouble("PLoadTotal"), row.getDouble("QLoadTotal")).multiply(baseMva));
		return loadInfo;
	}
	
	/** 
	 * Create and return all Branch Results 
	 * 
	 * @return List of AclfBranchInfo objects containing branch-level results
	 */
	public List<AclfBranchInfo> getBranchResults() {
		List<AclfBranchInfo> braInfoList = new LinkedList<>();
		createBranchResults(braInfoList, null, this.aclfNet.getNoActiveBranch());
		return braInfoList;
	}
	
	/** 
	 * Populate the provided list with branch-level results, with the sort rules
	 * and number of points.
	 * 
	 * @param branchInfoList the list to populate with AclfBranchInfo objects
	 * @param sortExpr sort expression to sort branches before processing
	 * @param maxNumOfBranchResults maximum number of branch results to include
	 */
	public void createBranchResults(List<AclfBranchInfo> branchInfoList, 
						Sorter sortExpr, int maxNumOfBranchResults) {
		DataFrame dfBranch = this.dfAdapter.getDfBranch();
		/*
		 * Note: DFLib sorting is not in-place. The sort() method returns a 
		 * new DataFrame instance with the rows reordered, preserving the original 
		 * DataFrame.
		 */
		if (sortExpr != null)
			dfBranch = dfBranch.sort(sortExpr);
		
		Counter cnt = new Counter();
		
		// Process sorted rows
		dfBranch.forEach(row -> {
			if (cnt.increment() <= maxNumOfBranchResults) {
				AclfBranchInfo braInfo = getBranchResult(row);
				branchInfoList.add(braInfo);
			}
		});
	}
	
	/** 
	 * Get results for a specific branch by its ID.
	 * 
	 * @param branchId the ID of the branch
	 * @return AclfBranchInfo object containing results for the specified branch
	 */
	public AclfBranchInfo getBranchResult(RowProxy row) {
		double baseMva = this.aclfNet.getBaseMva();
		
		AclfBranchInfo branchInfo = new AclfBranchInfo();
					
		branchInfo.setBranchId((String)row.get("ID"));
		branchInfo.setBranchName((String)row.get("Name"));
					
		AclfBranchCode code = row.get("BranchCode", AclfBranchCode.class);
		branchInfo.setBranchType(code == AclfBranchCode.LINE? "AcLine" : "Transformer");  // AcLine, Transformer
					
		branchInfo.setBranchPowerFlowSide2To(new Complex(row.getDouble("PFrom2To"), row.getDouble("QFrom2To")).multiply(baseMva));
		branchInfo.setBranchPowerFlowTo2From(new Complex(row.getDouble("PTo2From"), row.getDouble("QTo2From")).multiply(baseMva));
		
		return branchInfo;
	}
}
