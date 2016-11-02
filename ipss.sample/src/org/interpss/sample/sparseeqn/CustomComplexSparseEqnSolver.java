package org.interpss.sample.sparseeqn;

import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.core.sparse.ISparseEqnSolver;

public class CustomComplexSparseEqnSolver implements ISparseEqnSolver {
	protected boolean LUed = false;
	
	private ISparseEqnComplex sparseEqn;
	
	public CustomComplexSparseEqnSolver(ISparseEqnComplex sparseEqn) {
		this.sparseEqn = sparseEqn;
	}	
	
	@Override public void setMatrixDirty() {
		this.LUed = false;
	}

	@Override public boolean luMatrix(double arg0) throws IpssNumericException {
		System.out.println("Call CustomComplexSparseEqnSolver.luMatrix() ...");
		this.LUed = true;
		return true;
	}

	@Override public void solveEqn() throws IpssNumericException {
		if (!this.LUed)
			this.luMatrix(1.0e-10);
		System.out.println("Call CustomComplexSparseEqnSolver.solveEqn() ...");
	}
	
	/* (non-Javadoc)
	 * @see com.interpss.core.sparse.ISparseEqnSolver#solveEqn(double[])
	 */
	@Override
	public double[] solveEqn(double[] arg0) throws IpssNumericException {
		return null;
	}	
}
