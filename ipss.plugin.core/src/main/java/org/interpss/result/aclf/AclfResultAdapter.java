package org.interpss.result.aclf;

import java.util.Comparator;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

public class AclfResultAdapter {	
	public static final int MaxNumOfBusResults = 50;
	public static final int MaxNumOfBranchResults = 50;
	
	public static Comparator<AclfBus> busVoltHigherComparator = 
						(b1, b2) -> b1.getVoltageMag() < b2.getVoltageMag()? 1 : -1; 
	public static Comparator<AclfBus> busVoltLowerComparator = 
						(b1, b2) -> b1.getVoltageMag() > b2.getVoltageMag()? 1 : -1; 			

	private Comparator<AclfBus> busComparator = (b1, b2) -> 0;
	private Comparator<AclfBranch> branchComparator = (b1, b2) -> 0;
	private int numOfBusResults = MaxNumOfBusResults;
	private int numOfBranchResults = MaxNumOfBranchResults;
	
	public AclfResultAdapter() {
		// Constructor
	}
	
	public AclfResultAdapter busComparator(Comparator<AclfBus> comparator) {
		this.busComparator = comparator;
		return this;
	}
	
	public AclfResultAdapter branchComparator(Comparator<AclfBranch> comparator) {
		this.branchComparator = comparator;
		return this;
	}
	
	public AclfResultAdapter numOfBusResults(int num) {
		this.numOfBusResults = num;
		return this;
	}
	
	public AclfResultAdapter numOfBranchResults(int num) {
		this.numOfBranchResults = num;
		return this;
	}
	
	/**
	 * Accept the AclfNetwork object and extract the load flow results into
	 * an AclfResultContainer object.
	 * 
	 * @param aclfNet the AclfNetwork object containing load flow results
	 * @return AclfResultContainer object containing the extracted results
	 */
	public AclfResultContainer accept(AclfNetwork aclfNet) {
		AclfResultContainer results = new AclfResultContainer();
		
		AclfResultHelper helper = new AclfResultHelper(aclfNet);
		
		helper.createNetResults(results);
		
		results.getBusResults().clear();
		helper.createBusResults(results.getBusResults(), 
				busComparator, this.numOfBusResults);
		
		results.getBranchResults().clear();
		helper.createBranchResults(results.getBranchResults(), 
				branchComparator, this.numOfBranchResults);
		
		return results;
	}
}
