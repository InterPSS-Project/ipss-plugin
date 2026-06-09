package org.interpss.core.sparse;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.sparse.ISparseEqnMatrix2x2;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.sparse.DoubleSEqnElem;
import com.interpss.core.sparse.DoubleSEqnRow;
import com.interpss.core.sparse.impl.AbstractSparseEqnDoubleImpl;

public class JacobianMatrixExportTest extends CorePluginTestSetup {

    private static final Path DEFAULT_OUTPUT_DIR =
            Paths.get("/Users/ipssdev/github/JKLU/target/ipss-matrices");

    @Test
    public void exportActivs25kJacobian() throws Exception {
        exportJacobian(
                "ACTIVSg25k",
                "testData/psse/v33/ACTIVSg25k.RAW",
                1.0E-6,
                "ACTIVSg25k-jacobian.mtx");
    }

    @Test
    public void exportOpenEiJacobian() throws Exception {
        exportJacobian(
                "OpenEI",
                "testData/adpter/psse/v33/Base_Eastern_Interconnect_515GW.RAW",
                1.0E-4,
                "OpenEI-jacobian.mtx");
    }

    private static void exportJacobian(String name, String rawFile, double tolerance, String outputFile)
            throws InterpssException, IOException {
        IpssCorePlugin.init();

        AclfNetwork net = org.interpss.plugin.pssl.plugin.IpssAdapter.importAclfNet(rawFile)
                .setFormat(PSSE)
                .setPsseVersion(org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion.PSSE_33)
                .load()
                .getImportedObj();

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
        algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        algo.setTolerance(tolerance);
        assertTrue(algo.loadflow(), name + " loadflow should converge before Jacobian export");

        ISparseEqnMatrix2x2 jacobian = algo.getLfCalculator().getNrSolver().createJMatrix();
        assertTrue(jacobian instanceof AbstractSparseEqnDoubleImpl,
                "Expected InterPSS sparse double-backed Jacobian");

        Path outDir = Paths.get(System.getProperty("jklu.matrix.output.dir", DEFAULT_OUTPUT_DIR.toString()));
        Files.createDirectories(outDir);
        Path out = outDir.resolve(outputFile);
        writeMatrixMarket((AbstractSparseEqnDoubleImpl) jacobian, out, name);
        System.out.println("Exported " + name + " Jacobian to " + out
                + " dimension=" + jacobian.getDimension()
                + " nnz=" + jacobian.getTotalElements());
    }

    private static void writeMatrixMarket(AbstractSparseEqnDoubleImpl matrix, Path out, String caseName)
            throws IOException {
        int dimension = matrix.getDimension();
        int nnz = matrix.getTotalElements();

        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writer.write("%%MatrixMarket matrix coordinate real general\n");
            writer.write("% Generated from InterPSS Newton-Raphson Jacobian for " + caseName + "\n");
            writer.write("% Scalar expansion of InterPSS ISparseEqnMatrix2x2 rows\n");
            writer.write(dimension + " " + dimension + " " + nnz + "\n");

            for (int row = 0; row < dimension; row++) {
                DoubleSEqnRow rowData = matrix.getElem(row);
                if (rowData == null) {
                    continue;
                }
                if (rowData.aii != 0.0) {
                    writeEntry(writer, row, row, rowData.aii);
                }
                if (rowData.aijList != null) {
                    for (DoubleSEqnElem elem : rowData.aijList) {
                        if (elem.aij != 0.0) {
                            writeEntry(writer, row, elem.j, elem.aij);
                        }
                    }
                }
            }
        }
    }

    private static void writeEntry(BufferedWriter writer, int row, int col, double value) throws IOException {
        writer.write(Integer.toString(row + 1));
        writer.write(' ');
        writer.write(Integer.toString(col + 1));
        writer.write(' ');
        writer.write(String.format(Locale.US, "%.17g", value));
        writer.write('\n');
    }
}
