package org.interpss.optadj.texas2k;

import org.ejml.data.DMatrixSparseCSC;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.dclf.AclfNetSensHelper;
import com.interpss.core.funcImpl.dclf.AclfNetSensSparseHelper;

import org.interpss.fadapter.psse.PSSEDirectParser;
public class Texas2K_Sample_Info {
    public static AclfNetwork loadNetwork() throws InterpssException {
		// load the test data V33
		AclfNetwork aclfNet = new PSSEDirectParser().parse("ipss.plugin.core/testData/psse/v36/Texas2k_series24_case1_2016summerPeak_v36.RAW");	

		//aclfNet.getBranchList().stream().forEach(branch -> {
				//branch.setName(branch.getId());
		//	});

		return aclfNet;
	}

	public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = loadNetwork();

		AclfNetSensHelper helper = new AclfNetSensHelper(aclfNet);

		float[][] sen = helper.calSen();
		System.out.println("Number of non-zero elements in the dense matrix: " + sen.length * sen[0].length);

		AclfNetSensSparseHelper helperSparse = new AclfNetSensSparseHelper(aclfNet);

		DMatrixSparseCSC senSparse = helperSparse.calSen();
		System.out.println("Number of non-zero elements in the sparse matrix: " + senSparse.nz_length);
	}
}
