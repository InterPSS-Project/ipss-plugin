package org.interpss.sample.aclf.customSolver.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.core.sparse.impl.AbstractSparseEqnComplexImpl;
import com.interpss.core.sparse.solver.ISparseEqnSolver;
import com.interpss.core.sparse.solver.SparseEqnSolverFactory;

/**
 * Common Math Sparse Equation of data type complex for solving the [A]X=B problem. 
 * To outside, the index number is from 0 to n-1
 */
public class CMathSparseEqnComplex extends AbstractSparseEqnComplexImpl<ISparseEqnSolver> {
	/** Constructor
	 * 
	 * @param n the dimension of the equation
	 */
	public CMathSparseEqnComplex(int n) {
		super(n);
		this.solver = new SparseEqnSolverFactory().createSparseEqnComplexSolver(this);
	}

	@Override
	public Complex[] solveLUedEqn(Complex[] b) throws IpssNumericException {
		throw new IpssNumericException("Function not implemented");
	}
}
