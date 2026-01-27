package org.interpss.plugin.result;

import java.util.Comparator;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;

public class AclfResultAdapter {	
	public static final int MaxNumOfResults = 50;
	
	// Pre-defined comparators for common sorting needs
	public static Comparator<AclfBus> busVoltHigherComparator = 
						(b1, b2) -> b1.getVoltageMag() < b2.getVoltageMag()? 1 : -1; 
	public static Comparator<AclfBus> busVoltLowerComparator = 
						(b1, b2) -> b1.getVoltageMag() > b2.getVoltageMag()? 1 : -1; 
	public static Comparator<AclfBus> mismatchLargerComparator = 
						(b1, b2) -> b1.mismatch(AclfMethodType.NR).abs() < b2.mismatch(AclfMethodType.NR).abs() ? 1 : -1; 					

	public static Comparator<AclfGen> genLargerComparator = 
						(g1, g2) -> g1.getGen().abs() < g2.getGen().abs()? 1 : -1; 
	public static Comparator<AclfLoad> loadLargerComparator = 
						(l1, l2) -> l1.getLoad(1.0).abs() < l2.getLoad(1.0).abs()? 1 : -1; 							
						
	public static Comparator<AclfBranch> branchFlowLargerComparator = 
						(b1, b2) -> b1.powerFrom2To().abs() < b2.powerFrom2To().abs()? 1 : -1; 
	
	// default comparators for no sorting					
	private Comparator<AclfBus> busComparator = (b1, b2) -> 0;
	private Comparator<AclfGen> genComparator = (b1, b2) -> 0;
	private Comparator<AclfLoad> loadComparator = (b1, b2) -> 0;
	private Comparator<AclfBranch> branchComparator = (b1, b2) -> 0;
	
	// default number of results to be extracted
	private int numOfBusResults = MaxNumOfResults;
	private int numOfGenResults = MaxNumOfResults;
	private int numOfLoadResults = MaxNumOfResults;
	private int numOfBranchResults = MaxNumOfResults;
	
	/**
	 * Default constructor
	 */
	public AclfResultAdapter() {
		// Constructor
	}
	
	/**
	 * Set the bus comparator for sorting bus results
	 * 
	 * @param comparator
	 * @return
	 */
	public AclfResultAdapter busComparator(Comparator<AclfBus> comparator) {
		this.busComparator = comparator;
		return this;
	}
	
	/**
	 * Set the generator comparator for sorting generator results
	 * 
	 * @param comparator
	 * @return
	 */
	public AclfResultAdapter genComparator(Comparator<AclfGen> comparator) {
		this.genComparator = comparator;
		return this;
	}
	
	/**
	 * Set the load comparator for sorting load results
	 * 
	 * @param comparator
	 * @return
	 */
	public AclfResultAdapter loadComparator(Comparator<AclfLoad> comparator) {
		this.loadComparator = comparator;
		return this;
	}
	
	/**
	 * Set the branch comparator for sorting branch results
	 * 
	 * @param comparator
	 * @return
	 */
	public AclfResultAdapter branchComparator(Comparator<AclfBranch> comparator) {
		this.branchComparator = comparator;
		return this;
	}
	
	/**
	 * Set the number of bus results to be extracted
	 * 
	 * @param num
	 * @return
	 */
	public AclfResultAdapter numOfBusResults(int num) {
		this.numOfBusResults = num;
		return this;
	}
	
	/**
	 * Set the number of generator results to be extracted
	 * 
	 * @param num
	 * @return
	 */
	public AclfResultAdapter numOfGenResults(int num) {
		this.numOfGenResults = num;
		return this;
	}
	
	/**
	 * Set the number of load results to be extracted
	 * 
	 * @param num
	 * @return
	 */
	public AclfResultAdapter numOfLoadResults(int num) {
		this.numOfLoadResults = num;
		return this;
	}
	
	/**
	 * Set the number of branch results to be extracted
	 * 
	 * @param num
	 * @return
	 */
	public AclfResultAdapter numOfBranchResults(int num) {
		this.numOfBranchResults = num;
		return this;
	}
	
	/**
	 * Accept the AclfNetwork object and extract the load flow results into
	 * an AclfResultContainer object, based on the sort rules and number of 
	 * result points.
	 * 
	 * @param aclfNet the AclfNetwork object containing load flow results
	 * @return AclfResultContainer object containing the extracted results
	 */
	public AclfResultContainer accept(AclfNetwork aclfNet) {
		AclfResultContainer results = new AclfResultContainer();
		
		AclfResultHelper helper = new AclfResultHelper(aclfNet);
		
		helper.createNetResults(results.getNetResults());
		
		results.getBusResults().clear();
		helper.createBusResults(results.getBusResults(), 
				busComparator, this.numOfBusResults);
		
		results.getGenResults().clear();
		helper.createGenResults(results.getGenResults(), 
				genComparator, this.numOfGenResults);
		
		results.getLoadResults().clear();
		helper.createLoadResults(results.getLoadResults(), 
				loadComparator, this.numOfLoadResults);
		
		results.getBranchResults().clear();
		helper.createBranchResults(results.getBranchResults(), 
				branchComparator, this.numOfBranchResults);
		
		return results;
	}
}
