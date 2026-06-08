package org.interpss.core.sparse.solver.klusolvex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.base.ISparseEquation.IndexType;

import com.interpss.core.sparse.ComplexSEqnElem;
import com.interpss.core.sparse.ComplexSEqnRow;
import org.interpss.core.sparse.impl.klusolvex.KlusolveXSparseEqnComplexImpl;
import com.interpss.core.sparse.solver.BaseSparseEqnSolver;
import com.sun.jna.Memory;

/**
 * KLUSolveX solver for square complex sparse equations.
 */
public class KlusolveXSparseEqnComplexSolver extends BaseSparseEqnSolver {
	private static final int COMPLEX_BYTES = Double.BYTES * 2;

	private final KlusolveXSparseEqnComplexImpl eqn;
	private final KlusolveXLibrary library;
	private KlusolveXSparseSet sparseSet;
	private boolean matrixDirty = true;
	private double[] rhsInterleaved;
	private double[] resultInterleaved;
	private Complex[] resultValues;
	private Memory rhsMemory;
	private Memory resultMemory;

	public KlusolveXSparseEqnComplexSolver(KlusolveXSparseEqnComplexImpl eqn) {
		this.eqn = eqn;
		this.library = KlusolveXLibrary.load();
	}

	@Override
	public void setMatrixDirty() {
		super.setMatrixDirty();
		this.matrixDirty = true;
	}

	@Override
	public Object getSymbolTable() {
		return this.sparseSet;
	}

	@Override
	public void setSymbolTable(Object symbolTable) {
		this.sparseSet = (KlusolveXSparseSet) symbolTable;
	}

	@Override
	public boolean factorization(boolean buildSymbolTable, double tolerance) throws IpssNumericException {
		ensureSparseSet();
		if(this.matrixDirty || buildSymbolTable) {
			buildNativeMatrix();
		}
		this.sparseSet.factor();
		this.factored = true;
		this.matrixDirty = false;
		return true;
	}

	@Override
	public boolean factorization(boolean buildSymbolTable) throws IpssNumericException {
		return factorization(buildSymbolTable, 1.0e-6);
	}

	@Override
	public void solveEqn(boolean buildSymbolTable) throws IpssNumericException {
		if(!this.factored) {
			factorization(buildSymbolTable, 1.0e-10);
		}
		ensureSolveBuffers();
		long start = KlusolveXPerformanceCounters.start();
		fillCurrentB(this.rhsInterleaved);
		long rhsCollectNanos = elapsed(start);
		start = KlusolveXPerformanceCounters.start();
		this.sparseSet.writeComplexMemory(this.rhsMemory, this.rhsInterleaved);
		long rhsNativeWriteNanos = elapsed(start);
		KlusolveXSparseSet.NativeSolveResult result = this.sparseSet.solveNative(this.rhsMemory, this.resultMemory);
		start = KlusolveXPerformanceCounters.start();
		result.x.read(0L, this.resultInterleaved, 0, this.resultInterleaved.length);
		long resultUnpackNanos = elapsed(start);
		long resultCreateNanos = 0L;
		long resultStoreNanos = 0L;
		if(KlusolveXPerformanceCounters.enabled()) {
			start = KlusolveXPerformanceCounters.start();
			for(int i = 0; i < this.eqn.getDimension(); i++) {
				this.resultValues[i] = new Complex(this.resultInterleaved[2 * i], this.resultInterleaved[2 * i + 1]);
			}
			resultCreateNanos = elapsed(start);
			start = KlusolveXPerformanceCounters.start();
			for(int i = 0; i < this.eqn.getDimension(); i++) {
				this.eqn.setBi(this.resultValues[i], i);
			}
			resultStoreNanos = elapsed(start);
		} else {
			for(int i = 0; i < this.eqn.getDimension(); i++) {
				this.eqn.setBi(new Complex(this.resultInterleaved[2 * i], this.resultInterleaved[2 * i + 1]), i);
			}
		}
		profile().addRhsPackBreakdown(rhsCollectNanos, rhsNativeWriteNanos);
		profile().addSolve(rhsCollectNanos + rhsNativeWriteNanos, result.nativeSolveNanos, resultUnpackNanos);
		profile().addEquationCopy(resultCreateNanos + resultStoreNanos);
		profile().addEquationCopyBreakdown(resultCreateNanos, resultStoreNanos);
	}

	public Complex[] solveComplexEqn(Complex[] b) throws IpssNumericException {
		if(!this.factored) {
			factorization(false, 1.0e-10);
		}
		double[] solved = this.sparseSet.solve(toInterleaved(b));
		long start = KlusolveXPerformanceCounters.start();
		Complex[] x = new Complex[this.eqn.getDimension()];
		for(int i = 0; i < x.length; i++) {
			x[i] = new Complex(solved[2 * i], solved[2 * i + 1]);
		}
		profile().addComplexResult(elapsed(start));
		return x;
	}

	public void clearPrimitiveRhs() {
		ensureSolveBuffers();
		Arrays.fill(this.rhsInterleaved, 0.0);
	}

	public double[] primitiveRhsInterleaved() {
		ensureSolveBuffers();
		return this.rhsInterleaved;
	}

	public void setPrimitiveRhs(int row, Complex value) {
		if(value == null) {
			setPrimitiveRhs(row, 0.0, 0.0);
		} else {
			setPrimitiveRhs(row, value.getReal(), value.getImaginary());
		}
	}

	public void setPrimitiveRhs(int row, double real, double imaginary) {
		ensureSolveBuffers();
		this.rhsInterleaved[2 * row] = real;
		this.rhsInterleaved[2 * row + 1] = imaginary;
	}

	public void setPrimitiveRhs3x1(int row,
			double aReal, double aImaginary,
			double bReal, double bImaginary,
			double cReal, double cImaginary) {
		ensureSolveBuffers();
		int offset = 6 * row;
		this.rhsInterleaved[offset] = aReal;
		this.rhsInterleaved[offset + 1] = aImaginary;
		this.rhsInterleaved[offset + 2] = bReal;
		this.rhsInterleaved[offset + 3] = bImaginary;
		this.rhsInterleaved[offset + 4] = cReal;
		this.rhsInterleaved[offset + 5] = cImaginary;
	}

	public void solvePrimitiveRhs(boolean buildSymbolTable) throws IpssNumericException {
		if(!this.factored) {
			factorization(buildSymbolTable, 1.0e-10);
		}
		ensureSolveBuffers();
		long start = KlusolveXPerformanceCounters.start();
		this.sparseSet.writeComplexMemory(this.rhsMemory, this.rhsInterleaved);
		long rhsNativeWriteNanos = elapsed(start);
		KlusolveXSparseSet.NativeSolveResult result = this.sparseSet.solveNative(this.rhsMemory, this.resultMemory);
		start = KlusolveXPerformanceCounters.start();
		result.x.read(0L, this.resultInterleaved, 0, this.resultInterleaved.length);
		long resultUnpackNanos = elapsed(start);
		profile().addRhsPackBreakdown(0L, rhsNativeWriteNanos);
		profile().addSolve(rhsNativeWriteNanos, result.nativeSolveNanos, resultUnpackNanos);
		profile().addEquationCopy(0L);
		profile().addEquationCopyBreakdown(0L, 0L);
	}

	public double[] primitiveSolvedInterleaved() {
		ensureSolveBuffers();
		return this.resultInterleaved;
	}

	public Complex getPrimitiveSolved(int row) {
		ensureSolveBuffers();
		return new Complex(this.resultInterleaved[2 * row], this.resultInterleaved[2 * row + 1]);
	}

	public void setPrimitiveSolved(int row, Complex3x1 target, int phaseIndex) {
		ensureSolveBuffers();
		Complex value = new Complex(this.resultInterleaved[2 * row], this.resultInterleaved[2 * row + 1]);
		if(phaseIndex == 0) {
			target.a_0 = value;
		}
		else if(phaseIndex == 1) {
			target.b_1 = value;
		}
		else {
			target.c_2 = value;
		}
	}

	private void ensureSparseSet() throws IpssNumericException {
		int rows = this.eqn.getDimension(IndexType.Row);
		int cols = this.eqn.getDimension(IndexType.Col);
		if(rows != cols) {
			throw new IpssNumericException("KLUSolveX supports square matrices only: rows = "
					+ rows + ", cols = " + cols);
		}
		if(this.sparseSet == null || this.sparseSet.dimension() != rows) {
			if(this.sparseSet != null) {
				this.sparseSet.close();
			}
			this.sparseSet = new KlusolveXSparseSet(library, rows);
			clearSolveBuffers();
			this.matrixDirty = true;
		}
	}

	private void buildNativeMatrix() throws IpssNumericException {
		long start = KlusolveXPerformanceCounters.start();
		List<KlusolveXMatrixEntry> entries = matrixEntries();
		long traversalNanos = elapsed(start);
		if(canReuseNativePattern(entries) && tryUpdateNativeValues(entries)) {
			this.sparseSet.setMatrixPattern(entries);
			profile().addReuseBuild(entries.size(), traversalNanos);
			return;
		}
		buildNativeMatrix(entries);
		this.sparseSet.setMatrixPattern(entries);
		profile().addFullBuild(entries.size(), traversalNanos);
	}

	private List<KlusolveXMatrixEntry> matrixEntries() {
		List<KlusolveXMatrixEntry> entries = new ArrayList<>();
		for(int i = 0; i < this.eqn.getDimension(); i++) {
			ComplexSEqnRow row = this.eqn.getElem(i);
			if(this.eqn.isSquareMatrix()) {
				addIfNonZero(entries, i, i, row.aii);
			}
			for(ComplexSEqnElem elem : row.aijList) {
				addIfNonZero(entries, i, elem.j, elem.aij);
			}
		}
		return entries;
	}

	private boolean canReuseNativePattern(List<KlusolveXMatrixEntry> entries) {
		List<KlusolveXMatrixEntry> previous = this.sparseSet.matrixPattern();
		if(previous == null || previous.size() != entries.size()) {
			return false;
		}
		for(int i = 0; i < entries.size(); i++) {
			if(!entries.get(i).samePosition(previous.get(i))) {
				return false;
			}
		}
		return true;
	}

	private boolean tryUpdateNativeValues(List<KlusolveXMatrixEntry> entries) {
		List<KlusolveXMatrixEntry> previous = this.sparseSet.matrixPattern();
		for(KlusolveXMatrixEntry entry : previous) {
			if(!this.sparseSet.tryZeroise(entry.row(), entry.col())) {
				profile().addReuseFallback();
				return false;
			}
		}
		for(KlusolveXMatrixEntry entry : entries) {
			if(!this.sparseSet.tryIncrement(entry.row(), entry.col(), entry.re(), entry.im())) {
				profile().addReuseFallback();
				return false;
			}
		}
		return true;
	}

	private void buildNativeMatrix(List<KlusolveXMatrixEntry> entries) throws IpssNumericException {
		this.sparseSet.zero();
		int index = 0;
		while(index < entries.size()) {
			int row = entries.get(index).row();
			int next = index + 1;
			while(next < entries.size() && entries.get(next).row() == row) {
				next++;
			}
			this.sparseSet.addRowPrimitive(row, entries.subList(index, next));
			index = next;
		}
	}

	private void addIfNonZero(List<KlusolveXMatrixEntry> entries, int row, int col, Complex value) {
		if(value != null && value.abs() != 0.0) {
			entries.add(new KlusolveXMatrixEntry(row, col, value.getReal(), value.getImaginary()));
		}
	}

	private Complex[] currentB() {
		Complex[] b = new Complex[this.eqn.getDimension()];
		for(int i = 0; i < b.length; i++) {
			b[i] = this.eqn.getX(i);
		}
		return b;
	}

	private double[] currentBInterleaved() {
		double[] b = new double[this.eqn.getDimension() * 2];
		fillCurrentB(b);
		return b;
	}

	private void fillCurrentB(double[] b) {
		for(int i = 0; i < this.eqn.getDimension(); i++) {
			Complex value = this.eqn.getElem(i).bi;
			if(value == null) {
				value = Complex.ZERO;
			}
			b[2 * i] = value.getReal();
			b[2 * i + 1] = value.getImaginary();
		}
	}

	private double[] toInterleaved(Complex[] values) {
		double[] interleaved = new double[this.eqn.getDimension() * 2];
		for(int i = 0; i < this.eqn.getDimension(); i++) {
			Complex value = i < values.length && values[i] != null ? values[i] : Complex.ZERO;
			interleaved[2 * i] = value.getReal();
			interleaved[2 * i + 1] = value.getImaginary();
		}
		return interleaved;
	}

	private KlusolveXPerformanceCounters profile() {
		return KlusolveXPerformanceCounters.get();
	}

	private void ensureSolveBuffers() {
		int values = this.eqn.getDimension() * 2;
		if(this.rhsInterleaved == null || this.rhsInterleaved.length != values) {
			this.rhsInterleaved = new double[values];
			this.resultInterleaved = new double[values];
			if(KlusolveXPerformanceCounters.enabled()) {
				this.resultValues = new Complex[this.eqn.getDimension()];
			}
			this.rhsMemory = new Memory((long) this.eqn.getDimension() * COMPLEX_BYTES);
			this.resultMemory = new Memory((long) this.eqn.getDimension() * COMPLEX_BYTES);
		}
	}

	private void clearSolveBuffers() {
		this.rhsInterleaved = null;
		this.resultInterleaved = null;
		this.resultValues = null;
		this.rhsMemory = null;
		this.resultMemory = null;
	}

	private long elapsed(long start) {
		return KlusolveXPerformanceCounters.enabled() ? System.nanoTime() - start : 0L;
	}
}
