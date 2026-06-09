package org.interpss.threePhase.sparse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.sparse.ComplexSEqnElem;
import com.interpss.core.sparse.ComplexSEqnRow;
import com.interpss.core.sparse.impl.AbstractSparseEqnComplexImpl;

/**
 * Opt-in exporter for generating JKLU Matrix Market replay cases from OpenDSS
 * three-phase feeder Y matrices.
 */
public class JkluThreePhaseMatrixExportTest {
	private static final String EXPORT_DIR = "jklu.3phase.matrix.export.dir";
	private static final String CASES = "jklu.3phase.matrix.export.cases";

	@Test
	public void exportOpenDssYMatrixCases() throws Exception {
		String dir = System.getProperty(EXPORT_DIR);
		assumeTrue(dir != null && dir.trim().length() > 0,
				"Set -D" + EXPORT_DIR + "=<dir> to generate JKLU Matrix Market cases");

		Path outputDir = Paths.get(dir);
		Files.createDirectories(outputDir);

		int exported = 0;
		for(String caseName : requestedCases()) {
			CaseSpec spec = CaseSpec.forName(caseName.trim());
			if(spec == null) {
				throw new IllegalArgumentException("Unknown JKLU 3phase export case: " + caseName);
			}
			exportCase(spec, outputDir);
			exported++;
		}
		assertTrue(exported > 0);
	}

	private static String[] requestedCases() {
		String value = System.getProperty(CASES, "Ckt24,Ckt7,IEEE8500");
		return value.split(",");
	}

	private static void exportCase(CaseSpec spec, Path outputDir) throws Exception {
		DStabNetwork3Phase distNet = loadCase(spec);
		DistributionPowerFlowAlgorithm powerFlow =
				ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
		powerFlow.setInitBusVoltageEnabled(true);
		powerFlow.orderDistributionBuses(false);
		powerFlow.initBusVoltages();

		ISparseEqnComplexMatrix3x3 yMatrix = distNet.formYMatrixABCForPowerflow();
		applySwingBusVoltageBoundary(distNet, yMatrix);

		SparseRows rows = collectRows(yMatrix.getSparseEqnComplex());
		regularizeZeroDiagonalRows(rows);
		Complex[] x = knownSolution(rows.n);
		Complex[] rhs = multiply(rows, x);

		Path matrix = outputDir.resolve(spec.name + "-ymatrix.mtx");
		Path vector = outputDir.resolve(spec.name + "-rhs.mtx");
		writeMatrixMarket(matrix, rows);
		writeVectorMarket(vector, rhs);

		System.out.println("JKLU_3PHASE_MATRIX case=" + spec.name
				+ ",matrix=" + matrix.toAbsolutePath()
				+ ",rhs=" + vector.toAbsolutePath()
				+ ",n=" + rows.n
				+ ",nnz=" + rows.entries.size());
	}

	private static DStabNetwork3Phase loadCase(CaseSpec spec) throws Exception {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.setRegControlEnabled(false);
		assertTrue(parser.parseFeederData(spec.folder, spec.file));
		assertTrue(parser.calcVoltageBases());
		assertTrue(parser.convertActualValuesToPU(1.0));
		return parser.getDistNetwork();
	}

	private static void applySwingBusVoltageBoundary(DStabNetwork3Phase distNet,
			ISparseEqnComplexMatrix3x3 yMatrix) {
		Complex3x3 zero = new Complex3x3();
		Complex3x3 unit = Complex3x3.createUnitMatrix();
		for(Object busObj : distNet.getBusList()) {
			BaseAclfBus<?, ?> bus = (BaseAclfBus<?, ?>) busObj;
			if(bus.isActive() && bus.isSwing()) {
				int swingSortNumber = bus.getSortNumber();
				for(int row = 0; row < distNet.getNoBus(); row++) {
					if(row != swingSortNumber) {
						yMatrix.setA(zero, row, swingSortNumber);
					}
					yMatrix.setA(zero, swingSortNumber, row);
				}
				yMatrix.setA(unit, swingSortNumber, swingSortNumber);
			}
		}
	}

	private static SparseRows collectRows(ISparseEqnComplex eqn) {
		if(!(eqn instanceof AbstractSparseEqnComplexImpl)) {
			throw new IllegalStateException("Unsupported sparse equation type: " + eqn.getClass().getName());
		}

		@SuppressWarnings("rawtypes")
		AbstractSparseEqnComplexImpl sparse = (AbstractSparseEqnComplexImpl) eqn;
		SparseRows rows = new SparseRows();
		rows.n = eqn.getDimension();
		rows.entries = new ArrayList<Entry>(eqn.getTotalElements());
		for(int row = 0; row < rows.n; row++) {
			ComplexSEqnRow sparseRow = sparse.getElem(row);
			addIfNonZero(rows.entries, row, row, sparseRow.aii);
			for(Object elemObj : sparseRow.aijList) {
				ComplexSEqnElem elem = (ComplexSEqnElem) elemObj;
				addIfNonZero(rows.entries, row, elem.j, elem.aij);
			}
		}
		return rows;
	}

	private static void addIfNonZero(List<Entry> entries, int row, int col, Complex value) {
		if(value != null && (value.getReal() != 0.0 || value.getImaginary() != 0.0)) {
			Entry entry = new Entry();
			entry.row = row;
			entry.col = col;
			entry.value = value;
			entries.add(entry);
		}
	}

	private static void regularizeZeroDiagonalRows(SparseRows rows) {
		Entry[] diagonal = new Entry[rows.n];
		for(Entry entry : rows.entries) {
			if(entry.row == entry.col) {
				diagonal[entry.row] = entry;
			}
		}
		for(int row = 0; row < rows.n; row++) {
			Entry entry = diagonal[row];
			if(entry == null) {
				entry = new Entry();
				entry.row = row;
				entry.col = row;
				entry.value = Complex.ONE;
				rows.entries.add(entry);
			}
			else if(entry.value.abs() < 1.0e-8) {
				entry.value = Complex.ONE;
			}
		}
	}

	private static Complex[] knownSolution(int n) {
		Complex[] x = new Complex[n];
		for(int i = 0; i < n; i++) {
			double real = 1.0 + (i % 5) * 0.001;
			double imag = ((i % 7) - 3) * 0.01;
			x[i] = new Complex(real, imag);
		}
		return x;
	}

	private static Complex[] multiply(SparseRows rows, Complex[] x) {
		Complex[] y = new Complex[rows.n];
		for(int i = 0; i < rows.n; i++) {
			y[i] = Complex.ZERO;
		}
		for(Entry entry : rows.entries) {
			y[entry.row] = y[entry.row].add(entry.value.multiply(x[entry.col]));
		}
		return y;
	}

	private static void writeMatrixMarket(Path file, SparseRows rows) throws IOException {
		BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
		try {
			writer.write("%%MatrixMarket matrix coordinate complex general\n");
			writer.write("% Generated from InterPSS OpenDSS three-phase Y matrix for JKLU replay benchmarks\n");
			writer.write(rows.n + " " + rows.n + " " + rows.entries.size() + "\n");
			for(Entry entry : rows.entries) {
				writer.write((entry.row + 1) + " " + (entry.col + 1) + " "
						+ Double.toString(entry.value.getReal()) + " "
						+ Double.toString(entry.value.getImaginary()) + "\n");
			}
		}
		finally {
			writer.close();
		}
	}

	private static void writeVectorMarket(Path file, Complex[] rhs) throws IOException {
		BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
		try {
			writer.write("%%MatrixMarket matrix array complex general\n");
			writer.write("% RHS generated as b=A*x with deterministic complex x\n");
			writer.write(rhs.length + " 1\n");
			for(Complex value : rhs) {
				writer.write(Double.toString(value.getReal()) + " "
						+ Double.toString(value.getImaginary()) + "\n");
			}
		}
		finally {
			writer.close();
		}
	}

	private static final class SparseRows {
		int n;
		List<Entry> entries;
	}

	private static final class Entry {
		int row;
		int col;
		Complex value;
	}

	private static final class CaseSpec {
		final String name;
		final String folder;
		final String file;

		CaseSpec(String name, String folder, String file) {
			this.name = name;
			this.folder = folder;
			this.file = file;
		}

		static CaseSpec forName(String name) {
			if("Ckt24".equalsIgnoreCase(name)) {
				return new CaseSpec("Ckt24", "testData/feeder/Ckt24", "master_ckt24_interpss.dss");
			}
			if("Ckt7".equalsIgnoreCase(name)) {
				return new CaseSpec("Ckt7", "testData/feeder/Ckt7", "Master_ckt7.dss");
			}
			if("IEEE8500".equalsIgnoreCase(name)) {
				return new CaseSpec("IEEE8500", "testData/feeder/IEEE8500", "Master-InterPSS.dss");
			}
			return null;
		}
	}
}
