package org.interpss.core.sparse.solver.klusolvex;

import java.util.List;

import org.interpss.numeric.exp.IpssNumericException;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * Owns a KLUSolveX sparse-set handle.
 */
final class KlusolveXSparseSet implements AutoCloseable {
	private static final int COMPLEX_BYTES = Double.BYTES * 2;

	private final KlusolveXLibrary library;
	private final int dimension;
	private Pointer handle;
	private List<KlusolveXMatrixEntry> matrixPattern;

	KlusolveXSparseSet(KlusolveXLibrary library, int dimension) throws IpssNumericException {
		this.library = library;
		this.dimension = dimension;
		long start = KlusolveXPerformanceCounters.start();
		this.handle = library.NewSparseSet(dimension);
		profile().addHandle(elapsed(start));
		if(this.handle == null || Pointer.nativeValue(this.handle) == 0L) {
			throw new IpssNumericException("KLUSolveX NewSparseSet failed, dimension = " + dimension);
		}
		start = KlusolveXPerformanceCounters.start();
		library.SetOptions(this.handle, KlusolveXLibrary.REUSE_SYMBOLIC_FACTORIZATION);
		profile().addOption(elapsed(start));
	}

	int dimension() {
		return this.dimension;
	}

	List<KlusolveXMatrixEntry> matrixPattern() {
		return this.matrixPattern;
	}

	void setMatrixPattern(List<KlusolveXMatrixEntry> matrixPattern) {
		this.matrixPattern = List.copyOf(matrixPattern);
	}

	void zero() throws IpssNumericException {
		checkResult(library.ZeroSparseSet(handle), "ZeroSparseSet");
	}

	void increment(int row, int col, double re, double im) throws IpssNumericException {
		long start = KlusolveXPerformanceCounters.start();
		int result = library.IncrementMatrixElement(handle, row + 1, col + 1, re, im);
		profile().addIncrement(elapsed(start));
		checkResult(result, "IncrementMatrixElement");
	}

	boolean tryIncrement(int row, int col, double re, double im) {
		long start = KlusolveXPerformanceCounters.start();
		int result = library.IncrementMatrixElement(handle, row + 1, col + 1, re, im);
		profile().addIncrement(elapsed(start));
		return result == 1;
	}

	boolean tryZeroise(int row, int col) {
		long start = KlusolveXPerformanceCounters.start();
		int result = library.ZeroiseMatrixElement(handle, row + 1, col + 1);
		profile().addZeroise(elapsed(start));
		return result == 1;
	}

	void add(int row, int col, double re, double im) throws IpssNumericException {
		Memory value = new Memory(COMPLEX_BYTES);
		value.setDouble(0L, re);
		value.setDouble(Double.BYTES, im);
		long start = KlusolveXPerformanceCounters.start();
		int result = library.SetMatrixElement(handle, row + 1, col + 1, value);
		profile().addSet(elapsed(start));
		checkResult(result, "SetMatrixElement");
	}

	void addRowPrimitive(int row, List<KlusolveXMatrixEntry> rowEntries) throws IpssNumericException {
		int order = 1;
		for(KlusolveXMatrixEntry entry : rowEntries) {
			if(entry.col() != row) {
				order++;
			}
		}
		Memory nodes = new Memory((long) order * Integer.BYTES);
		nodes.clear();
		nodes.setInt(0L, row + 1);
		int nextNode = 1;
		for(KlusolveXMatrixEntry entry : rowEntries) {
			if(entry.col() != row) {
				nodes.setInt((long) nextNode * Integer.BYTES, entry.col() + 1);
				nextNode++;
			}
		}
		Memory values = new Memory((long) order * order * COMPLEX_BYTES);
		values.clear();
		for(KlusolveXMatrixEntry entry : rowEntries) {
			int localCol = localColumn(row, rowEntries, entry.col());
			long offset = (long) localCol * order * COMPLEX_BYTES;
			values.setDouble(offset, entry.re());
			values.setDouble(offset + Double.BYTES, entry.im());
		}
		long start = KlusolveXPerformanceCounters.start();
		int result = library.AddPrimitiveMatrix(handle, order, nodes, values);
		profile().addPrimitive(elapsed(start));
		checkResult(result, "AddPrimitiveMatrix");
	}

	private int localColumn(int row, List<KlusolveXMatrixEntry> rowEntries, int col) {
		if(col == row) {
			return 0;
		}
		int localCol = 1;
		for(KlusolveXMatrixEntry entry : rowEntries) {
			if(entry.col() == row) {
				continue;
			}
			if(entry.col() == col) {
				return localCol;
			}
			localCol++;
		}
		return -1;
	}

	void factor() throws IpssNumericException {
		long start = KlusolveXPerformanceCounters.start();
		int result = library.FactorSparseMatrix(handle);
		profile().addFactor(elapsed(start));
		if(result == 2) {
			throw new IpssNumericException("KLUSolveX matrix is singular at column " + singularCol());
		}
		checkResult(result, "FactorSparseMatrix");
	}

	double[] solve(double[] bInterleaved) throws IpssNumericException {
		long start = KlusolveXPerformanceCounters.start();
		Memory b = complexMemory(bInterleaved);
		long rhsPackNanos = elapsed(start);
		NativeSolveResult result = solveNative(b);
		start = KlusolveXPerformanceCounters.start();
		double[] xInterleaved = new double[dimension * 2];
		result.x.read(0L, xInterleaved, 0, xInterleaved.length);
		profile().addSolve(rhsPackNanos, result.nativeSolveNanos, elapsed(start));
		return xInterleaved;
	}

	NativeSolveResult solveNative(Memory b) throws IpssNumericException {
		return solveNative(b, new Memory((long) dimension * COMPLEX_BYTES));
	}

	NativeSolveResult solveNative(Memory b, Memory x) throws IpssNumericException {
		long start = KlusolveXPerformanceCounters.start();
		int result = library.SolveSparseSet(handle, x, b);
		long nativeSolveNanos = elapsed(start);
		if(result == 2) {
			throw new IpssNumericException("KLUSolveX solve found singular matrix at column " + singularCol());
		}
		checkResult(result, "SolveSparseSet");
		return new NativeSolveResult(x, nativeSolveNanos);
	}

	private int singularCol() {
		IntByReference result = new IntByReference();
		if(library.GetSingularCol(handle, result) == 1) {
			return result.getValue();
		}
		return -1;
	}

	Memory complexMemory(double[] values) {
		Memory memory = new Memory((long) dimension * COMPLEX_BYTES);
		memory.write(0L, values, 0, dimension * 2);
		return memory;
	}

	void writeComplexMemory(Memory memory, double[] values) {
		memory.write(0L, values, 0, dimension * 2);
	}

	private void checkResult(int result, String function) throws IpssNumericException {
		if(result != 1) {
			throw new IpssNumericException("KLUSolveX " + function + " failed, result = " + result);
		}
	}

	private KlusolveXPerformanceCounters profile() {
		return KlusolveXPerformanceCounters.get();
	}

	static final class NativeSolveResult {
		final Memory x;
		final long nativeSolveNanos;

		NativeSolveResult(Memory x, long nativeSolveNanos) {
			this.x = x;
			this.nativeSolveNanos = nativeSolveNanos;
		}
	}

	private long elapsed(long start) {
		return KlusolveXPerformanceCounters.enabled() ? System.nanoTime() - start : 0L;
	}

	@Override
	public void close() {
		if(this.handle != null && Pointer.nativeValue(this.handle) != 0L) {
			library.DeleteSparseSet(handle);
			this.handle = null;
		}
	}
}
