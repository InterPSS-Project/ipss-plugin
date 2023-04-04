package org.interpss.sample.sparseeqn;

import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnDouble;

import com.interpss.core.sparse.solver.ISparseCSJEqnSolver;

import edu.emory.mathcs.csparsej.tdcomplex.DZcs_common.DZcsa;

public class CustomDoubleSparseEqnSolver implements ISparseCSJEqnSolver {
	private boolean LUed = false;
	
	// sparse eqn to be solved by this solver
	private ISparseEqnDouble sparseEqn;
	
	public CustomDoubleSparseEqnSolver(ISparseEqnDouble sparseEqn) {
		this.sparseEqn = sparseEqn;
	}
	
	@Override public void setMatrixDirty() {
		this.LUed = false;
	}

	@Override public boolean factorization(double arg0) throws IpssNumericException {
		System.out.println("Call CustomDoubleSparseEqnSolver.luMatrix() ...");
		this.LUed = true;
		return true;
	}

	@Override public void solveEqn() throws IpssNumericException {
		if (!this.LUed)
			this.factorization(1.0e-10);
		System.out.println("Call CustomDoubleSparseEqnSolver.solveEqn() ...");
	}

	@Override
	public boolean factorization() throws IpssNumericException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double[] solveEqn(double[] arg0) throws IpssNumericException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DZcsa solveEqn(DZcsa arg0) throws IpssNumericException {
		// TODO Auto-generated method stub
		return null;
	}
}

