package org.interpss.result.aclf;

import com.interpss.core.aclf.AclfNetwork;

public class AclfResultAdapter {
	public AclfResultAdapter() {
		// Constructor
	}
	
	public AclfResultContainer accept(AclfNetwork aclfNet) {
		AclfResultContainer results = new AclfResultContainer();
		
		AclfResultHelper helper = new AclfResultHelper(aclfNet);
		
		helper.createNetResults(results);
		
		results.getBusResults().clear();
		helper.createBusResults(results.getBusResults());
		
		results.getBranchResults().clear();
		helper.createBranchResults(results.getBranchResults());
		
		return results;
	}
}
