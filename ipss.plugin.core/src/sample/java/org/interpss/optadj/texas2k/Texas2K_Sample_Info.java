package org.interpss.optadj.texas2k;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfNetwork;

public class Texas2K_Sample_Info {
    public static AclfNetwork loadNetwork() throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("ipss.plugin.core/testData/psse/v36/Texas2k_series24_case1_2016summerPeak_v36.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36) 
				.load()
				.getImportedObj();	

		aclfNet.getBranchList().stream().forEach(branch -> {
				branch.setName(branch.getId());
			});

		return aclfNet;
	}
}
