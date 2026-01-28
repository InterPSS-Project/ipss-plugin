package org.interpss.plugin.result;

import static org.dflib.Exp.*;

import org.dflib.Sorter;

import com.interpss.core.aclf.AclfNetwork;

public class AclfResultDFrameAdapter {	
	public static final int MaxNumOfResults = 50;
	
	// Pre-defined comparators for common sorting needs
	public static Sorter busVoltHigherSortExpr = $col("VoltMag").desc(); 
	public static Sorter busVoltLowerSortExpr = $col("VoltMag").asc(); 
	
	public static Sorter pMismatchLargerSortExpr = $col("MismatchP").desc(); 
	public static Sorter qMismatchLargerSortExpr = $col("MismatchQ").desc();
	public static Sorter mismatchLargerSorterExpr = 
							$double("MismatchP").mul($double("MismatchP"))
							.add($double("MismatchQ").mul($double("MismatchQ"))).desc();
	
	public static Sorter genLargerSortExpr = 
							$double("PGen").mul($double("PGen"))
							.add($double("QGen").mul($double("QGen"))).desc(); 
	public static Sorter loadLargerSortExpr = 
							$double("PLoadTotal").mul($double("PLoadTotal"))
							.add($double("QLoadTotal").mul($double("QLoadTotal"))).desc(); 							
						
	public static Sorter branchFlowLargerSortExpr = 
							$double("PFrom2To").mul($double("PFrom2To"))
							.add($double("QFrom2To").mul($double("QFrom2To"))).desc();
 
	// default comparators for no sorting					
	private Sorter busSortExpr = null;
	private Sorter genSortExpr = null;
	private Sorter loadSortExpr = null;
	private Sorter branchSortExpr = null;
	
	// default number of results to be extracted
	private int numOfBusResults = MaxNumOfResults;
	private int numOfGenResults = MaxNumOfResults;
	private int numOfLoadResults = MaxNumOfResults;
	private int numOfBranchResults = MaxNumOfResults;
	
	/**
	 * Default constructor
	 */
	public AclfResultDFrameAdapter() {
		// Constructor
	}
	
	/**
	 * Set the bus comparator for sorting bus results
	 * 
	 * @param comparator
	 * @return
	 */
	public AclfResultDFrameAdapter busSortExpr(Sorter comparator) {
		this.busSortExpr = comparator;
		return this;
	}
	
	/**
	 * Set the generator comparator for sorting generator results
	 * 
	 * @param comparator
	 * @return
	 */
	public AclfResultDFrameAdapter genSortExpr(Sorter comparator) {
		this.genSortExpr = comparator;
		return this;
	}
	
	/**
	 * Set the load comparator for sorting load results
	 * 
	 * @param comparator
	 * @return
	 */
	public AclfResultDFrameAdapter loadSortExpr(Sorter comparator) {
		this.loadSortExpr = comparator;
		return this;
	}
	
	/**
	 * Set the branch comparator for sorting branch results
	 * 
	 * @param comparator
	 * @return
	 */
	public AclfResultDFrameAdapter branchSortExpr(Sorter comparator) {
		this.branchSortExpr = comparator;
		return this;
	}
	
	/**
	 * Set the number of bus results to be extracted
	 * 
	 * @param num
	 * @return
	 */
	public AclfResultDFrameAdapter numOfBusResults(int num) {
		this.numOfBusResults = num;
		return this;
	}
	
	/**
	 * Set the number of generator results to be extracted
	 * 
	 * @param num
	 * @return
	 */
	public AclfResultDFrameAdapter numOfGenResults(int num) {
		this.numOfGenResults = num;
		return this;
	}
	
	/**
	 * Set the number of load results to be extracted
	 * 
	 * @param num
	 * @return
	 */
	public AclfResultDFrameAdapter numOfLoadResults(int num) {
		this.numOfLoadResults = num;
		return this;
	}
	
	/**
	 * Set the number of branch results to be extracted
	 * 
	 * @param num
	 * @return
	 */
	public AclfResultDFrameAdapter numOfBranchResults(int num) {
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
		
		AclfResultDFrameHelper helper = new AclfResultDFrameHelper(aclfNet);
		
		helper.createNetResults(results.getNetResults());
		
		results.getBusResults().clear();
		helper.createBusResults(results.getBusResults(), 
				busSortExpr, this.numOfBusResults);
		
		results.getGenResults().clear();
		helper.createGenResults(results.getGenResults(), 
				genSortExpr, this.numOfGenResults);
		
		results.getLoadResults().clear();
		helper.createLoadResults(results.getLoadResults(), 
				loadSortExpr, this.numOfLoadResults);
		
		results.getBranchResults().clear();
		helper.createBranchResults(results.getBranchResults(), 
				branchSortExpr, this.numOfBranchResults);
		
		return results;
	}
}
