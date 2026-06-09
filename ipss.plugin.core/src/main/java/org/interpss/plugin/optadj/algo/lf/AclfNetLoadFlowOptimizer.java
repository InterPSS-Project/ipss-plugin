package org.interpss.plugin.optadj.algo.lf;

import org.ejml.data.DMatrixSparseCSC;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;
import org.interpss.plugin.optadj.algo.util.AclfNetSensSparseHelper;

import com.interpss.core.aclf.AclfNetwork;

/**
 * 
 * 
 */
public class AclfNetLoadFlowOptimizer extends BaseAclfNetLoadFlowOptimizer{
	private boolean sparseMatrix = false;

	private float[][] senMatrix = null;

	private DMatrixSparseCSC senSparseMatrix = null;

	public AclfNetLoadFlowOptimizer() {
		this.sparseMatrix = false;
	}

	public AclfNetLoadFlowOptimizer(boolean sparseMatrix) {
		this.sparseMatrix = sparseMatrix;
	}

	@Override
	protected void createSenMatrix(AclfNetwork net) {
		if (sparseMatrix) {
			AclfNetSensSparseHelper helper = new AclfNetSensSparseHelper(net);
			senSparseMatrix = helper.calSen();
		} else {
			AclfNetSensHelper helper = new AclfNetSensHelper(net);
			senMatrix = helper.calSen();
		}
	}
	
	@Override
	protected float getSen(int busNo, int branchNo) {
		return sparseMatrix ? (float) senSparseMatrix.get(busNo, branchNo, 0.0f) : senMatrix[busNo][branchNo];
	}
}
