package org.interpss.sample.customSolver.impl;

import org.interpss.sample.customSolver.solver.CMathSparseEqnDoubleSolver;

import com.interpss.core.sparse.impl.AbstractSparseEqnDoubleImpl;

/**
 * CommonMatch Sparse Equation of data type double for solving the [A]X=B problem. 
 * To outside, the index number is from 0 to n-1.
 */
public class CMathSparseEqnDouble extends AbstractSparseEqnDoubleImpl {
	/** Constructor
	 * 
	 * @param n the dimension of the equation
	 */
	public CMathSparseEqnDouble(int n) {
		super(n);
		this.sparseEqnSolver = new CMathSparseEqnDoubleSolver(this);
		this.sparseEqnSolver.setMatrixDirty();
	}	
}