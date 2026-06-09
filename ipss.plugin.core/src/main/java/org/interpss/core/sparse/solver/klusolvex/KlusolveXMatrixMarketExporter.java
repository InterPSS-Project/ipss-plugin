package org.interpss.core.sparse.solver.klusolvex;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.interpss.numeric.exp.IpssNumericException;

/**
 * Optional Matrix Market export for JKLU/KLUSolveX parity benchmarks.
 */
final class KlusolveXMatrixMarketExporter {
	static final String EXPORT_DIR_PROPERTY = "ipss.klusolvex.export.dir";
	static final String EXPORT_PREFIX_PROPERTY = "ipss.klusolvex.export.prefix";

	private static final AtomicLong SEQUENCE = new AtomicLong();

	private KlusolveXMatrixMarketExporter() {
	}

	static boolean enabled() {
		String dir = System.getProperty(EXPORT_DIR_PROPERTY);
		return dir != null && !dir.trim().isEmpty();
	}

	static void exportSnapshot(int dimension, List<KlusolveXMatrixEntry> entries,
			double[] rhsInterleaved, String workflow) throws IpssNumericException {
		if(!enabled()) {
			return;
		}
		if(entries == null) {
			throw new IpssNumericException("KLUSolveX Matrix Market export requested before matrix pattern is available");
		}
		String prefix = System.getProperty(EXPORT_PREFIX_PROPERTY, "klusolvex");
		long sequence = SEQUENCE.incrementAndGet();
		Path dir = Paths.get(System.getProperty(EXPORT_DIR_PROPERTY));
		String base = prefix + "-" + workflow + "-" + String.format("%06d", sequence);
		try {
			Files.createDirectories(dir);
			writeMatrix(dir.resolve(base + "-matrix.mtx"), dimension, entries);
			writeVector(dir.resolve(base + "-rhs.mtx"), dimension, rhsInterleaved);
		}
		catch(IOException e) {
			throw new IpssNumericException("KLUSolveX Matrix Market export failed: " + e.getMessage());
		}
	}

	private static void writeMatrix(Path file, int dimension, List<KlusolveXMatrixEntry> entries)
			throws IOException {
		try(BufferedWriter writer = Files.newBufferedWriter(file)) {
			writer.write("%%MatrixMarket matrix coordinate complex general");
			writer.newLine();
			writer.write("% exported by InterPSS KLUSolveX adapter for JKLU parity benchmarking");
			writer.newLine();
			writer.write(dimension + " " + dimension + " " + entries.size());
			writer.newLine();
			for(KlusolveXMatrixEntry entry : entries) {
				writer.write((entry.row() + 1) + " " + (entry.col() + 1) + " " +
						entry.re() + " " + entry.im());
				writer.newLine();
			}
		}
	}

	private static void writeVector(Path file, int dimension, double[] rhsInterleaved)
			throws IOException {
		if(rhsInterleaved == null || rhsInterleaved.length < 2 * dimension) {
			throw new IOException("RHS vector is not available or is undersized");
		}
		try(BufferedWriter writer = Files.newBufferedWriter(file)) {
			writer.write("%%MatrixMarket matrix array complex general");
			writer.newLine();
			writer.write("% exported by InterPSS KLUSolveX adapter for JKLU parity benchmarking");
			writer.newLine();
			writer.write(dimension + " 1");
			writer.newLine();
			for(int i = 0; i < dimension; i++) {
				writer.write(rhsInterleaved[2 * i] + " " + rhsInterleaved[2 * i + 1]);
				writer.newLine();
			}
		}
	}
}
