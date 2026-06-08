package org.interpss.optadj.texas2k;

import org.ejml.data.DMatrixSparseCSC;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;
import org.interpss.plugin.optadj.algo.util.AclfNetSensSparseHelper;
import com.interpss.core.aclf.AclfNetwork;

public class Texas2K_SenMatrix_Sample {
	public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = Texas2K_Sample_Info.loadNetwork();

		AclfNetSensHelper helper = new AclfNetSensHelper(aclfNet);

		float[][] sen = helper.calSen();
		System.out.println("Number of non-zero elements in the dense matrix: " + sen.length * sen[0].length);
		System.out.println(sen[0][0]);

		AclfNetSensSparseHelper helperSparse = new AclfNetSensSparseHelper(aclfNet);

		DMatrixSparseCSC senSparse = helperSparse.calSen();
		System.out.println("Number of non-zero elements in the sparse matrix: " + senSparse.nz_length);
		System.out.println(senSparse.get(0, 0, 0.0));

		int count = 0;
		for (int i = 0; i < sen.length; i++) {
			for (int j = 0; j < sen[i].length; j++) {
				double value = senSparse.get(i, j, 0.0);
				if (value != 0.0 && Math.abs(sen[i][j] - value) > 1e-6) {
					System.out.println("Error: " + sen[i][j] + " != " + value);
				}
				else
					if (value != 0.0) 
						count++;
			}
		}
		System.out.println("Number of non-zero elements in the sparse matrix: " + count);
	}
}
