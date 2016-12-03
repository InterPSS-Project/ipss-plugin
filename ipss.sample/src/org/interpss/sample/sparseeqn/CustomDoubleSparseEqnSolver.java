package org.interpss.sample.sparseeqn;

import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnDouble;

import com.interpss.core.sparse.ISparseEqnSolver;

public class CustomDoubleSparseEqnSolver implements ISparseEqnSolver {
	private boolean LUed = false;
	
	// sparse eqn to be solved by this solver
	private ISparseEqnDouble sparseEqn;
	
	public CustomDoubleSparseEqnSolver(ISparseEqnDouble sparseEqn) {
		this.sparseEqn = sparseEqn;
	}
	
	@Override public void setMatrixDirty() {
		this.LUed = false;
	}

	@Override public boolean luMatrix(double arg0) throws IpssNumericException {
		System.out.println("Call CustomDoubleSparseEqnSolver.luMatrix() ...");
		this.LUed = true;
		return true;
	}

	@Override public void solveEqn() throws IpssNumericException {
		if (!this.LUed)
			this.luMatrix(1.0e-10);
		System.out.println("Call CustomDoubleSparseEqnSolver.solveEqn() ...");
	}
	
	/* (non-Javadoc)
	 * @see com.interpss.core.sparse.ISparseEqnSolver#solveEqn(double[])
	 */
	@Override
	public double[] solveEqn(double[] arg0) throws IpssNumericException {
		return null;
	}	
}

