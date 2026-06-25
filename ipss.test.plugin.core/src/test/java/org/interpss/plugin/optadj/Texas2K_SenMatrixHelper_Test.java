package org.interpss.plugin.optadj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ejml.data.DMatrixSparseCSC;
import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.texas2K.Texas2K_TestCaseInfo;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.optadj.algo.util.AclfNetSensHelper;
import com.interpss.optadj.algo.util.AclfNetSensSparseHelper;

/**
 * Regression test for {@code Texas2K_SenMatrix_Sample}: dense and sparse bus-to-branch
 * sensitivity matrices must agree on the Texas-2K network.
 */
public class Texas2K_SenMatrixHelper_Test extends CorePluginTestSetup {
	private static final double SEN_TOLERANCE = 1e-6;

	@Test
	void denseAndSparseSensitivityMatricesAgree() throws Exception {
		AclfNetwork net = Texas2K_TestCaseInfo.createTestCaseNetwork();

		float[][] denseSen = new AclfNetSensHelper(net).calSen();
		DMatrixSparseCSC sparseSen = new AclfNetSensSparseHelper(net).calSen();

		assertEquals(net.getNoActiveBus(), denseSen.length, "Dense matrix row count");
		assertEquals(net.getNoActiveBranch(), denseSen[0].length, "Dense matrix column count");
		assertEquals(net.getNoBus(), sparseSen.numRows, "Sparse matrix row count");
		assertEquals(net.getNoBranch(), sparseSen.numCols, "Sparse matrix column count");

		int matchingNonZeroCount = 0;
		for (int i = 0; i < denseSen.length; i++) {
			for (int j = 0; j < denseSen[i].length; j++) {
				double sparseValue = sparseSen.get(i, j, 0.0);
				if (sparseValue != 0.0) {
					assertEquals(denseSen[i][j], sparseValue, SEN_TOLERANCE,
							"Dense and sparse sensitivity mismatch at [" + i + "][" + j + "]");
					matchingNonZeroCount++;
				}
			}
		}

		// Regression anchors (Texas2K_SenMatrix_Sample).
		assertEquals(2000, denseSen.length, "Active bus count");
		assertEquals(3220, denseSen[0].length, "Active branch count");
		assertEquals(0.0785106f, denseSen[0][0], SEN_TOLERANCE, "First dense sensitivity entry");
		assertEquals(0.0785106, sparseSen.get(0, 0, 0.0), SEN_TOLERANCE,
				"First sparse sensitivity entry");
		assertEquals(2517431, sparseSen.nz_length, "Sparse matrix non-zero count");
		assertEquals(2517431, matchingNonZeroCount,
				"All sparse non-zero entries should match dense within tolerance");
		assertTrue(matchingNonZeroCount > 0, "Sensitivity matrix should contain non-zero entries");
	}
}
