package org.interpss.sample.sparseeqn;

import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.sparse.ISparseEqnDouble;

import com.interpss.core.sparse.SparseEqnSolverFactory;

public class CustomSparseEqnSolver {

	public static void main(String[] args) {
		// configure the sparse eqn solvers
		SparseEqnSolverFactory.setDoubleSolverCreator((ISparseEqnDouble eqn) -> { return new CustomDoubleSparseEqnSolver(eqn); });
		SparseEqnSolverFactory.setComplexSolverCreator((ISparseEqnComplex eqn) -> { return new CustomComplexSparseEqnSolver(eqn); });
		
		// start your simulation job ...
	}
}


