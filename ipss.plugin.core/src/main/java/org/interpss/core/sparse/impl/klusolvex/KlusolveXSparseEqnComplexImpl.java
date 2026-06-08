package org.interpss.core.sparse.impl.klusolvex;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.core.sparse.impl.AbstractSparseEqnComplexImpl;
import org.interpss.core.sparse.solver.klusolvex.KlusolveXSparseEqnComplexSolver;

/**
 * KLUSolveX sparse equation of complex scalars.
 */
public class KlusolveXSparseEqnComplexImpl
		extends AbstractSparseEqnComplexImpl<KlusolveXSparseEqnComplexSolver> {
	public KlusolveXSparseEqnComplexImpl(int n) {
		super(n);
		this.sparseEqnSolver = new KlusolveXSparseEqnComplexSolver(this);
		this.sparseEqnSolver.setMatrixDirty();
	}

	@Override
	public Complex[] solveLUedEqn(Complex[] b) throws IpssNumericException {
		return this.sparseEqnSolver.solveComplexEqn(b);
	}

	void clearPrimitiveRhs() {
		this.sparseEqnSolver.clearPrimitiveRhs();
	}

	double[] primitiveRhsInterleaved() {
		return this.sparseEqnSolver.primitiveRhsInterleaved();
	}

	void setPrimitiveRhs(int row, Complex value) {
		this.sparseEqnSolver.setPrimitiveRhs(row, value);
	}

	void setPrimitiveRhs(int row, double real, double imaginary) {
		this.sparseEqnSolver.setPrimitiveRhs(row, real, imaginary);
	}

	void setPrimitiveRhs3x1(int row,
			double aReal, double aImaginary,
			double bReal, double bImaginary,
			double cReal, double cImaginary) {
		this.sparseEqnSolver.setPrimitiveRhs3x1(row,
				aReal, aImaginary, bReal, bImaginary, cReal, cImaginary);
	}

	void solvePrimitiveRhs(boolean buildSymbolTable) throws IpssNumericException {
		this.sparseEqnSolver.solvePrimitiveRhs(buildSymbolTable);
	}

	double[] primitiveSolvedInterleaved() {
		return this.sparseEqnSolver.primitiveSolvedInterleaved();
	}

	Complex getPrimitiveSolved(int row) {
		return this.sparseEqnSolver.getPrimitiveSolved(row);
	}

	void setPrimitiveSolved(int row, Complex3x1 target, int phaseIndex) {
		this.sparseEqnSolver.setPrimitiveSolved(row, target, phaseIndex);
	}
}
