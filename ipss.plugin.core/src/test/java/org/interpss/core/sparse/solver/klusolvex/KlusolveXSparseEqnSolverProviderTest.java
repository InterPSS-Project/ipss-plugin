package org.interpss.core.sparse.solver.klusolvex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.IpssCorePlugin.SparseSolverType;
import org.interpss.core.sparse.impl.klusolvex.KlusolveXSparseEqnComplexImpl;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.sparse.SparseEqnObjectFactory;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;

import org.interpss.core.sparse.solver.KlusolveXAvailability;

public class KlusolveXSparseEqnSolverProviderTest {
	@AfterEach
	public void resetSolverProvider() {
		SparseEqnSolverProvider.useCSJ();
		System.clearProperty(SparseEqnSolverProvider.SOLVER_PROPERTY);
		System.clearProperty(KlusolveXMatrixMarketExporter.EXPORT_DIR_PROPERTY);
		System.clearProperty(KlusolveXMatrixMarketExporter.EXPORT_PREFIX_PROPERTY);
	}

	@Test
	public void matrixMarketExporterWritesJkluReplayFiles(@TempDir Path tempDir) throws Exception {
		System.setProperty(KlusolveXMatrixMarketExporter.EXPORT_DIR_PROPERTY, tempDir.toString());
		System.setProperty(KlusolveXMatrixMarketExporter.EXPORT_PREFIX_PROPERTY, "jklu-bench");

		KlusolveXMatrixMarketExporter.exportSnapshot(2,
				List.of(
						new KlusolveXMatrixEntry(0, 0, 4.0, 0.5),
						new KlusolveXMatrixEntry(0, 1, 0.5, -1.0),
						new KlusolveXMatrixEntry(1, 1, 5.0, -0.25)),
				new double[] {1.0, 2.0, -0.5, 1.0},
				"test");

		Path matrix = tempDir.resolve("jklu-bench-test-000001-matrix.mtx");
		Path rhs = tempDir.resolve("jklu-bench-test-000001-rhs.mtx");
		assertTrue(Files.exists(matrix));
		assertTrue(Files.exists(rhs));
		assertTrue(Files.readString(matrix).contains("%%MatrixMarket matrix coordinate complex general"));
		assertTrue(Files.readString(rhs).contains("%%MatrixMarket matrix array complex general"));
	}

	@Test
	public void autoKlusolveXFallsBackWhenNativeLibraryIsMissing() {
		Assumptions.assumeFalse(KlusolveXAvailability.isNativeLibraryLoadable(),
				"KLUSolveX native library is available");

		IpssCorePlugin.Selection selection = IpssCorePlugin.useKlusolveXIfAvailable();

		assertEquals(SparseSolverType.KLUSOLVEX_AUTO, selection.requested());
		assertEquals(SparseSolverType.CSJ, selection.active());
		assertTrue(selection.message().contains("falling back to CSJ"));
	}

	@Test
	public void strictKlusolveXSelectionReportsMissingNativeLibrary() {
		Assumptions.assumeFalse(KlusolveXAvailability.isNativeLibraryLoadable(),
				"KLUSolveX native library is available");
		try {
			IpssCorePlugin.useKlusolveX();
			fail("Expected KLUSolveX selection to fail without a native library");
		}
		catch(IllegalStateException e) {
			assertTrue(e.getMessage().contains("KLUSolveX native library is not loadable"));
		}
	}

	@Test
	public void nativeKlusolveXCanBeSelectedWhenAvailable() {
		Assumptions.assumeTrue(KlusolveXAvailability.isNativeLibraryLoadable(),
				"Set ipss.klusolvex.library.path or ipss.klusolvex.library.name to run native selection");

		IpssCorePlugin.Selection selection = IpssCorePlugin.useKlusolveX();

		assertEquals(SparseSolverType.KLUSOLVEX, selection.active());
		assertInstanceOf(KlusolveXSparseEqnComplexImpl.class,
				new SparseEqnObjectFactory().createSparseEqnComplex(2));
	}

	@Test
	public void nativeKlusolveXMatchesCsjForComplexEquationWhenAvailable() throws IpssNumericException {
		Assumptions.assumeTrue(KlusolveXAvailability.isNativeLibraryLoadable(),
				"Set ipss.klusolvex.library.path or ipss.klusolvex.library.name to run native parity");

		Complex[] csj = solveComplexSampleWithCsj();
		Complex[] klusolveX = solveComplexSampleWithKlusolveX();

		for(int i = 0; i < csj.length; i++) {
			assertEquals(csj[i].getReal(), klusolveX[i].getReal(), 1.0e-8);
			assertEquals(csj[i].getImaginary(), klusolveX[i].getImaginary(), 1.0e-8);
		}
	}

	@Test
	public void nativeKlusolveXMatchesCsjForComplex3x3EquationWhenAvailable() throws IpssNumericException {
		Assumptions.assumeTrue(KlusolveXAvailability.isNativeLibraryLoadable(),
				"Set ipss.klusolvex.library.path or ipss.klusolvex.library.name to run native 3x3 parity");

		Complex3x1[] csj = solveComplex3x3SampleWithCsj();
		Complex3x1[] klusolveX = solveComplex3x3SampleWithKlusolveX();

		for(int i = 0; i < csj.length; i++) {
			assertComplex3x1Equals(csj[i], klusolveX[i], 1.0e-8);
		}
	}

	private Complex[] solveComplexSampleWithCsj() throws IpssNumericException {
		SparseEqnSolverProvider.useCSJ();
		ISparseEqnComplex eqn = createComplexSampleEqn();
		eqn.solveEqn(1.0e-20);
		return complexXVector(eqn);
	}

	private Complex[] solveComplexSampleWithKlusolveX() throws IpssNumericException {
		IpssCorePlugin.useKlusolveX();
		ISparseEqnComplex eqn = createComplexSampleEqn();
		eqn.solveEqn(1.0e-20);
		return complexXVector(eqn);
	}

	private Complex3x1[] solveComplex3x3SampleWithCsj() throws IpssNumericException {
		SparseEqnSolverProvider.useCSJ();
		ISparseEqnComplexMatrix3x3 eqn = createComplex3x3SampleEqn();
		eqn.solveEqn(1.0e-20);
		return complex3x1XVector(eqn);
	}

	private Complex3x1[] solveComplex3x3SampleWithKlusolveX() throws IpssNumericException {
		IpssCorePlugin.useKlusolveX();
		ISparseEqnComplexMatrix3x3 eqn = createComplex3x3SampleEqn();
		eqn.solveEqn(1.0e-20);
		return complex3x1XVector(eqn);
	}

	private ISparseEqnComplex createComplexSampleEqn() {
		ISparseEqnComplex eqn = new SparseEqnObjectFactory().createSparseEqnComplex(3);

		eqn.addToA(new Complex(4.0, 0.5), 0, 0);
		eqn.addToA(new Complex(5.0, -0.25), 1, 1);
		eqn.addToA(new Complex(6.0, 0.75), 2, 2);
		eqn.addToA(new Complex(0.5, -1.0), 0, 1);
		eqn.addToA(new Complex(-0.25, 0.5), 1, 2);
		eqn.addToA(new Complex(0.75, 0.25), 2, 0);

		eqn.setBi(new Complex(1.0, 2.0), 0);
		eqn.setBi(new Complex(-0.5, 1.0), 1);
		eqn.setBi(new Complex(3.0, -1.5), 2);
		return eqn;
	}

	private ISparseEqnComplexMatrix3x3 createComplex3x3SampleEqn() {
		ISparseEqnComplexMatrix3x3 eqn = new SparseEqnObjectFactory().createSparseEqnComplex3x3(2);

		Complex3x3 self0 = new Complex3x3();
		self0.aa = new Complex(6.0, 1.0);
		self0.bb = new Complex(7.0, 0.5);
		self0.cc = new Complex(8.0, -0.25);
		self0.ab = new Complex(0.2, -0.1);
		self0.bc = new Complex(-0.3, 0.2);

		Complex3x3 self1 = new Complex3x3();
		self1.aa = new Complex(5.5, -0.5);
		self1.bb = new Complex(6.5, 0.25);
		self1.cc = new Complex(7.5, 0.75);
		self1.ba = new Complex(0.1, 0.3);
		self1.cb = new Complex(-0.2, -0.2);

		Complex3x3 mutual01 = new Complex3x3();
		mutual01.aa = new Complex(-0.5, 0.25);
		mutual01.bb = new Complex(-0.4, -0.1);
		mutual01.cc = new Complex(-0.3, 0.15);

		Complex3x3 mutual10 = new Complex3x3();
		mutual10.aa = new Complex(-0.45, -0.2);
		mutual10.bb = new Complex(-0.35, 0.05);
		mutual10.cc = new Complex(-0.25, -0.1);

		eqn.addToA(self0, 0, 0);
		eqn.addToA(self1, 1, 1);
		eqn.addToA(mutual01, 0, 1);
		eqn.addToA(mutual10, 1, 0);

		eqn.setBi(new Complex3x1(new Complex(1.0, 0.5), new Complex(0.5, -0.25),
				new Complex(-1.0, 0.75)), 0);
		eqn.setBi(new Complex3x1(new Complex(0.75, -0.5), new Complex(-0.25, 1.0),
				new Complex(1.5, -0.75)), 1);
		return eqn;
	}

	private Complex[] complexXVector(ISparseEqnComplex eqn) {
		Complex[] x = new Complex[eqn.getDimension()];
		for(int i = 0; i < x.length; i++) {
			x[i] = eqn.getX(i);
		}
		return x;
	}

	private Complex3x1[] complex3x1XVector(ISparseEqnComplexMatrix3x3 eqn) {
		Complex3x1[] x = new Complex3x1[eqn.getDimension()];
		for(int i = 0; i < x.length; i++) {
			x[i] = eqn.getX(i);
		}
		return x;
	}

	private void assertComplex3x1Equals(Complex3x1 expected, Complex3x1 actual, double tolerance) {
		assertEquals(expected.a_0.getReal(), actual.a_0.getReal(), tolerance);
		assertEquals(expected.a_0.getImaginary(), actual.a_0.getImaginary(), tolerance);
		assertEquals(expected.b_1.getReal(), actual.b_1.getReal(), tolerance);
		assertEquals(expected.b_1.getImaginary(), actual.b_1.getImaginary(), tolerance);
		assertEquals(expected.c_2.getReal(), actual.c_2.getReal(), tolerance);
		assertEquals(expected.c_2.getImaginary(), actual.c_2.getImaginary(), tolerance);
	}
}
