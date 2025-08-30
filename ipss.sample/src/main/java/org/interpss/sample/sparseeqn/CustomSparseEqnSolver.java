package org.interpss.sample.sparseeqn;

import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.sparse.ISparseEqnDouble;
import org.interpss.sample.sparseeqn.impl.CMathSparseEqnDoubleSolver;
import org.interpss.sample.sparseeqn.impl.CSJComplexSparseEqnSolver;

import com.interpss.core.sparse.solver.SparseEqnSolverFactory;

public class CustomSparseEqnSolver {

	public static void main(String[] args) {
		// configure the sparse eqn solvers
		SparseEqnSolverFactory.setDoubleSolverCreator((ISparseEqnDouble eqn) -> new CMathSparseEqnDoubleSolver(eqn));
		SparseEqnSolverFactory.setComplexSolverCreator((ISparseEqnComplex eqn) -> new CSJComplexSparseEqnSolver(eqn));
		
		// start your simulation job ...
	}
}


