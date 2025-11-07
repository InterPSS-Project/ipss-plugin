package org.interpss.sample.customSolver.impl;

import com.interpss.core.sparse.impl.AbstractSparseEqnMatrix2x2Impl;
import com.interpss.core.sparse.solver.SparseEqnSolverFactory;

/**
 * CommonMath Sparse Equation of data type 2x2 matrix for solving the [A]X=B problem. 
 * To outside, the index number is from 0 to n-1
 */

public class CMathSparseEqnMatrix2x2 extends AbstractSparseEqnMatrix2x2Impl {
	/** Constructor
	 * 
	 * @param n the dimension of the equation
	 */
	public CMathSparseEqnMatrix2x2(int n) {
		super(n);
		this.solver = new SparseEqnSolverFactory().createSparseEqnDoubleSolver(this);
		setDimension(n);
	}
}