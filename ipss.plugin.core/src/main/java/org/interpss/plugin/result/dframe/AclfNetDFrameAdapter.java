package org.interpss.plugin.result.dframe;

import java.util.function.Predicate;

import org.dflib.DataFrame;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter to convert AclfNet data to a set of DataFrame objects
 */
public class AclfNetDFrameAdapter {
	private DataFrame dfBus;
	private DataFrame dfGen;
	private DataFrame dfLoad;
	
	private DataFrame dfBranch;
	
    /**
     * Constructor to initialize the DataFrame appender
     */
    public AclfNetDFrameAdapter() {
    }
    
    public DataFrame getDfBus() {
    	return dfBus;
    }
    
    public DataFrame getDfGen() {
		return dfGen;
	}
    
    public DataFrame getDfLoad() {
    	return dfLoad;
    }
    
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
    	this.dfBus = new AclfBusDFrameAdapter().adapt(aclfNet);
    	this.dfGen = new AclfGenDFrameAdapter().adapt(aclfNet);
    	this.dfLoad = new AclfLoadDFrameAdapter().adapt(aclfNet);
    	
    	this.dfBranch = new AclfBranchDFrameAdapter().adapt(aclfNet);
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
