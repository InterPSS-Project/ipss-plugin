package org.interpss.sample.aclf.customSolver.impl;

import com.interpss.core.sparse.impl.AbstractSparseEqnDoubleImpl;
import com.interpss.core.sparse.solver.SparseEqnSolverFactory;

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
		this.solver = new SparseEqnSolverFactory().createSparseEqnDoubleSolver(this);
		this.solver.setMatrixDirty();
	}	
}