package org.interpss.sample.aclf.customSolver.impl;

import com.interpss.core.sparse.impl.AbstractSparseEqnComplexMatrix3x3Impl;
import com.interpss.core.sparse.solver.ISparseEqnSolver;

/**
 * CSJ Sparse Equation of data type complex3x3 for solving the [A]X=B problem. 
 * To outside, the index number is from 0 to n-1
 */
public class CMathparseEqnComplexMatrix3x3 extends AbstractSparseEqnComplexMatrix3x3Impl<ISparseEqnSolver> {
	
	public CMathparseEqnComplexMatrix3x3(int n) {
		super(n);
		cplxMatrix = new CMathSparseEqnComplex(3*n);
	}
}