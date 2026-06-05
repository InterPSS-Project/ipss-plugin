package org.interpss.optadj.ieee39;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter.FileFormat;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

public class IEEE39_Sample_Data {

	public static AclfNetwork createTestCaseNetwork() throws InterpssException {
			    // Load network
				AclfNetwork net = CorePluginFactory.getFileAdapter(FileFormat.IEEECDF).load("ipss.plugin.core/testData/ieee/ieee39.ieee")
	            .getAclfNet();

	    // Clear zero-generation contributions
	    net.getBusList().forEach(bus -> {
	        if (bus.getGenP() == 0)
	            bus.getContributeGenList().clear();
	    });
		
	    net.createAclfGenNameLookupTable(true).values().forEach(gen -> gen.setPGenLimit(new LimitType(7, 0)));

		// set the branch rating.
		net.getBranchList().stream() 
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				aclfBranch.setName(aclfBranch.getId());
				// Mva1 is used for basecase loading limit
				aclfBranch.setRatingMva1(600.0);
			});

		return net;
	}
}
