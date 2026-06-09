package org.interpss.core.sparse.impl.klusolvex;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.core.sparse.PrimitiveComplex3x3ArrayEquation;
import com.interpss.core.sparse.impl.AbstractSparseEqnComplexMatrix3x3Impl;
import org.interpss.core.sparse.solver.klusolvex.KlusolveXSparseEqnComplexSolver;

/**
 * KLUSolveX sparse equation of 3x3 complex matrix blocks.
 */
public class KlusolveXSparseEqnComplexMatrix3x3Impl
		extends AbstractSparseEqnComplexMatrix3x3Impl<KlusolveXSparseEqnComplexSolver>
		implements PrimitiveComplex3x3ArrayEquation {
	public KlusolveXSparseEqnComplexMatrix3x3Impl(int n) {
		super(n);
		this.cplxMatrix = new KlusolveXSparseEqnComplexImpl(3 * n);
	}

	@Override
	public void clearPrimitiveRhs() {
		scalarMatrix().clearPrimitiveRhs();
	}

	@Override
	public double[] primitiveRhsInterleaved() {
		return scalarMatrix().primitiveRhsInterleaved();
	}

	@Override
	public void setPrimitiveRhs3x1(int row, Complex3x1 value) {
		int offset = 3 * row;
		scalarMatrix().setPrimitiveRhs(offset, value.a_0);
		scalarMatrix().setPrimitiveRhs(offset + 1, value.b_1);
		scalarMatrix().setPrimitiveRhs(offset + 2, value.c_2);
	}

	@Override
	public void setPrimitiveRhs3x1(int row,
			double aReal, double aImaginary,
			double bReal, double bImaginary,
			double cReal, double cImaginary) {
		scalarMatrix().setPrimitiveRhs3x1(row,
				aReal, aImaginary, bReal, bImaginary, cReal, cImaginary);
	}

	@Override
	public void solvePrimitiveRhs(boolean buildSymbolTable) throws IpssNumericException {
		scalarMatrix().solvePrimitiveRhs(buildSymbolTable);
	}

	@Override
	public Complex3x1 getPrimitiveSolved3x1(int row) {
		int offset = 3 * row;
		return new Complex3x1(
				scalarMatrix().getPrimitiveSolved(offset),
				scalarMatrix().getPrimitiveSolved(offset + 1),
				scalarMatrix().getPrimitiveSolved(offset + 2));
	}

	@Override
	public Complex3x1 getPrimitiveSolved3x1(int row, Complex3x1 target) {
		Complex3x1 value = target == null ? new Complex3x1() : target;
		int offset = 3 * row;
		scalarMatrix().setPrimitiveSolved(offset, value, 0);
		scalarMatrix().setPrimitiveSolved(offset + 1, value, 1);
		scalarMatrix().setPrimitiveSolved(offset + 2, value, 2);
		return value;
	}

	@Override
	public double[] primitiveSolvedInterleaved() {
		return scalarMatrix().primitiveSolvedInterleaved();
	}

	private KlusolveXSparseEqnComplexImpl scalarMatrix() {
		return (KlusolveXSparseEqnComplexImpl) this.cplxMatrix;
	}
}
