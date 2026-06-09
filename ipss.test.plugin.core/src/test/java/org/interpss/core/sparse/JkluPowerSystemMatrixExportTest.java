package org.interpss.core.sparse;

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
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.sparse.ComplexSEqnElem;
import com.interpss.core.sparse.ComplexSEqnRow;
import com.interpss.core.sparse.impl.AbstractSparseEqnComplexImpl;

/**
 * Opt-in exporter for generating JKLU Matrix Market replay cases from real
 * InterPSS power-system Y matrices.
 */
public class JkluPowerSystemMatrixExportTest extends CorePluginTestSetup {
    private static final String EXPORT_DIR = "jklu.matrix.export.dir";
    private static final String CASES = "jklu.matrix.export.cases";

    @Test
    public void exportAclfYMatrixCases() throws Exception {
        String dir = System.getProperty(EXPORT_DIR);
        assumeTrue(dir != null && dir.trim().length() > 0,
                "Set -D" + EXPORT_DIR + "=<dir> to generate JKLU Matrix Market cases");

        Path outputDir = Paths.get(dir);
        Files.createDirectories(outputDir);

        int exported = 0;
        for (String caseName : requestedCases()) {
            CaseSpec spec = CaseSpec.forName(caseName.trim());
            if (spec == null) {
                throw new IllegalArgumentException("Unknown JKLU export case: " + caseName);
            }
            exportCase(spec, outputDir);
            exported++;
        }
        assertTrue(exported > 0);
    }

    private static String[] requestedCases() {
        String value = System.getProperty(CASES, "BUS1824,BUS6384,BUS11856");
        return value.split(",");
    }

    private static void exportCase(CaseSpec spec, Path outputDir) throws Exception {
        AclfNetwork net = loadCase(spec);
        if (spec.buses > 0) {
            assertTrue(net.getBusList().size() == spec.buses, spec.name + " bus count");
        }

        ISparseEqnComplex eqn = net.formYMatrix();
        regularizeSwingBuses(net, eqn);

        SparseRows rows = collectRows(eqn);
        Complex[] x = knownSolution(rows.n);
        Complex[] rhs = multiply(rows, x);

        Path matrix = outputDir.resolve(spec.name + "-ymatrix.mtx");
        Path vector = outputDir.resolve(spec.name + "-rhs.mtx");
        writeMatrixMarket(matrix, rows);
        writeVectorMarket(vector, rhs);

        System.out.println("JKLU_MATRIX case=" + spec.name +
                ",matrix=" + matrix.toAbsolutePath() +
                ",rhs=" + vector.toAbsolutePath() +
                ",n=" + rows.n +
                ",nnz=" + rows.entries.size());
    }

    private static AclfNetwork loadCase(CaseSpec spec) throws Exception {
        if (spec.psse) {
            return IpssAdapter.importAclfNet(spec.path)
                    .setFormat(IpssAdapter.FileFormat.PSSE)
                    .setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)
                    .load()
                    .getImportedObj();
        }
        return CorePluginFactory
                .getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
                .load(spec.path)
                .getAclfNet();
    }

    private static void regularizeSwingBuses(AclfNetwork net, ISparseEqnComplex eqn) {
        for (AclfBus bus : net.getBusList()) {
            if (bus.isSwing()) {
                eqn.setA(new Complex(0.0, 1.0e10), bus.getSortNumber(), bus.getSortNumber());
            }
        }
    }

    private static SparseRows collectRows(ISparseEqnComplex eqn) {
        if (!(eqn instanceof AbstractSparseEqnComplexImpl)) {
            throw new IllegalStateException("Unsupported sparse equation type: " + eqn.getClass().getName());
        }

        @SuppressWarnings("rawtypes")
        AbstractSparseEqnComplexImpl sparse = (AbstractSparseEqnComplexImpl) eqn;
        SparseRows rows = new SparseRows();
        rows.n = eqn.getDimension();
        rows.entries = new ArrayList<Entry>(eqn.getTotalElements());
        for (int row = 0; row < rows.n; row++) {
            ComplexSEqnRow sparseRow = sparse.getElem(row);
            addIfNonZero(rows.entries, row, row, sparseRow.aii);
            for (ComplexSEqnElem elem : sparseRow.aijList) {
                addIfNonZero(rows.entries, row, elem.j, elem.aij);
            }
        }
        return rows;
    }

    private static void addIfNonZero(List<Entry> entries, int row, int col, Complex value) {
        if (value != null && (value.getReal() != 0.0 || value.getImaginary() != 0.0)) {
            Entry entry = new Entry();
            entry.row = row;
            entry.col = col;
            entry.value = value;
            entries.add(entry);
        }
    }

    private static Complex[] knownSolution(int n) {
        Complex[] x = new Complex[n];
        for (int i = 0; i < n; i++) {
            double imag = ((i % 7) - 3) * 0.01;
            x[i] = new Complex(1.0, imag);
        }
        return x;
    }

    private static Complex[] multiply(SparseRows rows, Complex[] x) {
        Complex[] y = new Complex[rows.n];
        for (int i = 0; i < rows.n; i++) {
            y[i] = Complex.ZERO;
        }
        for (Entry entry : rows.entries) {
            y[entry.row] = y[entry.row].add(entry.value.multiply(x[entry.col]));
        }
        return y;
    }

    private static void writeMatrixMarket(Path file, SparseRows rows) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        try {
            writer.write("%%MatrixMarket matrix coordinate complex general\n");
            writer.write("% Generated from InterPSS AclfNetwork.formYMatrix() for JKLU replay benchmarks\n");
            writer.write(rows.n + " " + rows.n + " " + rows.entries.size() + "\n");
            for (Entry entry : rows.entries) {
                writer.write((entry.row + 1) + " " + (entry.col + 1) + " " +
                        Double.toString(entry.value.getReal()) + " " +
                        Double.toString(entry.value.getImaginary()) + "\n");
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
            for (Complex value : rhs) {
                writer.write(Double.toString(value.getReal()) + " " +
                        Double.toString(value.getImaginary()) + "\n");
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
        final String path;
        final int buses;
        final boolean psse;

        CaseSpec(String name, String path, int buses) {
            this(name, path, buses, false);
        }

        CaseSpec(String name, String path, int buses, boolean psse) {
            this.name = name;
            this.path = path;
            this.buses = buses;
            this.psse = psse;
        }

        static CaseSpec forName(String name) {
            if ("BUS1824".equalsIgnoreCase(name)) {
                return new CaseSpec("BUS1824", "testData/ipssdata/BUS1824.ipssdat", 1824);
            }
            if ("BUS6384".equalsIgnoreCase(name)) {
                return new CaseSpec("BUS6384", "testData/ipssdata/BUS6384.ipssdat", 6384);
            }
            if ("BUS11856".equalsIgnoreCase(name)) {
                return new CaseSpec("BUS11856", "testData/ipssdata/BUS11856.ipssdat", 11856);
            }
            if ("OpenEI".equalsIgnoreCase(name)) {
                return new CaseSpec("OpenEI",
                        "../ipss.sample/testData/psse/openEI/Base_Eastern_Interconnect_515GW.RAW",
                        -1,
                        true);
            }
            if ("ACTIVSg25k".equalsIgnoreCase(name)) {
                return new CaseSpec("ACTIVSg25k", "testData/psse/v33/ACTIVSg25k.RAW", -1, true);
            }
            return null;
        }
    }
}
