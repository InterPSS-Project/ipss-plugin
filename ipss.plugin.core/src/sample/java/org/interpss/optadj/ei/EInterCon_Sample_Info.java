package org.interpss.optadj.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.HashSet;
import java.util.Set;

import org.ejml.data.DMatrixSparseCSC;
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

		//aclfNet.getBranchList().stream().forEach(branch -> {
		//		branch.setName(branch.getId());
		//	});

		return aclfNet;
	}

	public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = loadNetwork();

		AclfNetSensSparseHelper helper = new AclfNetSensSparseHelper(aclfNet);

		aclfNet.createAclfGenNameLookupTable(true);

		Set<String> busSet = new HashSet<>();
		// add all active gen buses to the bus set
		aclfNet.getAclfGenNameLookupTable().values().stream().filter(gen -> gen.isActive()).forEach(gen -> {
			busSet.add(gen.getParentBus().getId());
		});
		System.out.println("Number of active gen buses: " + busSet.size());

		Set<String> branchSet = new HashSet<>();
		// add first 200 active branch buses to the branch set
		aclfNet.getBranchList().stream().filter(branch -> branch.isActive()).limit(200).forEach(branch -> {
			branchSet.add(branch.getId());
		});

		// count the number of non-zero elements in the sparse matrix
		DMatrixSparseCSC sen = helper.calSenSortNumber(busSet, branchSet);
		System.out.println("Number of non-zero elements in the sparse matrix: " + sen.nz_length);
	}
}
