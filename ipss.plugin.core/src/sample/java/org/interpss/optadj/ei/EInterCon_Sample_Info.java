package org.interpss.optadj.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.optadj.algo.util.AclfNetSensSparseHelper;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfNetwork;

public class EInterCon_Sample_Info {
    public static AclfNetwork loadNetwork() throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("ipss.plugin.core/testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	

		aclfNet.getBranchList().stream().forEach(branch -> {
				branch.setName(branch.getId());
			});

		return aclfNet;
	}

	public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = loadNetwork();

		AclfNetSensSparseHelper helper = new AclfNetSensSparseHelper(aclfNet);

		helper.calSen();
	}
}
