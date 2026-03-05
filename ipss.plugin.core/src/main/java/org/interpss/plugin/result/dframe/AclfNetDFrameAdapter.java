package org.interpss.plugin.result.dframe;

import java.util.function.Predicate;

import org.dflib.DataFrame;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter to convert AclfNet data to a set of DataFrame objects
 */
public class AclfNetDFrameAdapter {
	// default filters for bus and branch data for simulation report generation, 
	// can be overridden by user input filters in the adapt() method
	public static Predicate<AclfBus> BusFilter = bus -> bus.getBaseVoltage() >= 100 * 1000  // voltage level filter: only include buses with base voltage >= 100kV
						&& (bus.getVoltageMag() > 1.05 || bus.getVoltageMag() < 0.95);
	
	public static Predicate<AclfBranch> BranchFilter = branch -> {
		double ratingMVA = branch.getRatingMva1();
		if (ratingMVA <= 0) return false; // skip branches with non-positive rating
		
		double powerFlowMW = branch.powerFrom2To(UnitType.mVA).abs();
		double loadingPercent = Math.abs(powerFlowMW) / ratingMVA * 100.0;
		if (loadingPercent > 70.0) 
			return true;
		else
			return false;
	};
	
	private DataFrame dfBus;
	private DataFrame dfGen;
	private DataFrame dfLoad;
	
	private DataFrame dfBranch;
	
    /**
     * Constructor to initialize the DataFrame appender
     */
    public AclfNetDFrameAdapter() {
    }
    
    /**
     * Get the DataFrame for bus data
     * 
     * @return the DataFrame for bus data
     */
    public DataFrame getDfBus() {
    	return dfBus;
    }
    
    /**
     * Get the DataFrame for generator data
     * 
     * @return the DataFrame for generator data
     */
    public DataFrame getDfGen() {
		return dfGen;
	}
    
    /**
	 * Get the DataFrame for load data
	 * 
	 * @return the DataFrame for load data
	 */
    public DataFrame getDfLoad() {
    	return dfLoad;
    }
    
    /**
	 * Get the DataFrame for branch data
	 * 
	 * @return the DataFrame for branch data
	 * */
    public DataFrame getDfBranch() {
    	return dfBranch;
    }
    
    /**
	 * Adapt the AclfNetwork bus/gen/load and branch data to DataFrame
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @return the adapted DataFrame
	 */
    public void adapt(AclfNetwork aclfNet) {
    	adapt(aclfNet, false);
    }
    
    /**
	 * Adapt the AclfNetwork bus/gen/load and branch data to DataFrame
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filtered whether to apply the default filters for bus and branch data
	 * @return the adapted DataFrame
	 */
    public void adapt(AclfNetwork aclfNet, boolean filtered) {
		if (filtered) {
			this.dfBus = new AclfBusDFrameAdapter().adapt(aclfNet, BusFilter, true);
			this.dfGen = new AclfGenDFrameAdapter().adapt(aclfNet, gen -> true);
			this.dfLoad = new AclfLoadDFrameAdapter().adapt(aclfNet, load -> true);
			
			this.dfBranch = new AclfBranchDFrameAdapter().adapt(aclfNet, BranchFilter, true);
		} else {
	    	this.dfBus = new AclfBusDFrameAdapter().adapt(aclfNet);
	    	this.dfGen = new AclfGenDFrameAdapter().adapt(aclfNet);
	    	this.dfLoad = new AclfLoadDFrameAdapter().adapt(aclfNet);
	    	
	    	this.dfBranch = new AclfBranchDFrameAdapter().adapt(aclfNet);
		}
    }
    
    /**
	 * Adapt the AclfNetwork bus/gen/load and branch data to DataFrame with filters
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param busFilter the filter for bus data
	 * @param genFilter the filter for generator data
	 * @param loadFilter the filter for load data
	 * @param branchFilter the filter for branch data
	 * @return the adapted DataFrame
	 * */
    public void adapt(AclfNetwork aclfNet, 
    		Predicate<AclfBus> busFilter, 
    		Predicate<AclfGen> genFilter, 
    		Predicate<AclfLoad> loadFilter, 
    		Predicate<AclfBranch> branchFilter) {
    	this.dfBus = new AclfBusDFrameAdapter().adapt(aclfNet, busFilter, true);
    	this.dfGen = new AclfGenDFrameAdapter().adapt(aclfNet, genFilter);
    	this.dfLoad = new AclfLoadDFrameAdapter().adapt(aclfNet, loadFilter);
    	
    	this.dfBranch = new AclfBranchDFrameAdapter().adapt(aclfNet, branchFilter, true);
    }
}
