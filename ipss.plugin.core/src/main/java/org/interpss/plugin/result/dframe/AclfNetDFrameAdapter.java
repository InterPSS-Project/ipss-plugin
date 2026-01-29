package org.interpss.plugin.result.dframe;

import org.dflib.DataFrame;

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
	 * Adapt the AclfNetwork load data to DataFrame
	 * * @param aclfNet the AclfNetwork object
	 * @return the adapted DataFrame
	 */
    public void adapt(AclfNetwork aclfNet) {
    	this.dfBus = new AclfBusDFrameAdapter().adapt(aclfNet);
    	this.dfGen = new AclfGenDFrameAdapter().adapt(aclfNet);
    	this.dfLoad = new AclfLoadDFrameAdapter().adapt(aclfNet);
    	
    	this.dfBranch = new AclfBranchDFrameAdapter().adapt(aclfNet);
    }
}
