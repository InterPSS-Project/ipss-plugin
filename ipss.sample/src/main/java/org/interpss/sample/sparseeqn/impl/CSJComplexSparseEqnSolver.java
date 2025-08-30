package org.interpss.sample.sparseeqn.impl;

import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.core.sparse.solver.csj.ICSJSparseEqnSolver;

import edu.emory.mathcs.csparsej.tdcomplex.DZcs_common.DZcsa;

public class CSJComplexSparseEqnSolver implements ICSJSparseEqnSolver {
	protected boolean LUed = false;
	
	private ISparseEqnComplex sparseEqn;
	
	public CSJComplexSparseEqnSolver(ISparseEqnComplex sparseEqn) {
		this.sparseEqn = sparseEqn;
	}	
	
	@Override public void setMatrixDirty() {
		this.LUed = false;
	}

	@Override public boolean factorization(double arg0) throws IpssNumericException {
		System.out.println("Call CustomComplexSparseEqnSolver.luMatrix() ...");
		this.LUed = true;
		return true;
	}

	@Override public void solveEqn() throws IpssNumericException {
		if (!this.LUed)
			this.factorization(1.0e-10);
		System.out.println("Call CustomComplexSparseEqnSolver.solveEqn() ...");
	}

	@Override
	public boolean factorization() throws IpssNumericException {
		System.out.println("Call CustomComplexSparseEqnSolver.luMatrix() ...");
		this.LUed = true;
		return true;
	}

	@Override
	public double[] solveEqn(double[] arg0) throws IpssNumericException {
		if (!this.LUed)
			this.factorization(1.0e-10);
		System.out.println("Call CustomComplexSparseEqnSolver.solveEqn() ...");
		return null;
	}

	@Override
	public DZcsa solveEqn(DZcsa arg0) throws IpssNumericException {
		if (!this.LUed)
			this.factorization(1.0e-10);
		System.out.println("Call CustomComplexSparseEqnSolver.solveEqn() ...");
		return null;
	}
}
