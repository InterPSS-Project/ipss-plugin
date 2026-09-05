package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Strict private-data gate for the five Case-6 REGFMA1 resources. */
class Texas2kRegfma1CoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void case6AttachesInitializesAndHoldsAllFiveGfmResources(@TempDir Path tempDir)
            throws Exception {
        Path caseDir = ROOT.resolve("Texas2k_series24_case6_2024lowloadwithgfm");
        Path raw = caseDir.resolve("Texas2k_series24_case6_2024lowloadwithgfm.RAW");
        Path dyr = caseDir.resolve("dynamic_models_case6.dyr");
        assumeTrue(Files.isRegularFile(raw), "Missing private Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing private Texas2k DYR: " + dyr);

        List<PsseDyrRecord> records = PsseDyrRecordReader.read(dyr);
        StringBuilder machines = new StringBuilder();
        StringBuilder gfm = new StringBuilder();
        for (PsseDyrRecord record : records) {
            String model = record.canonicalModelName();
            if (model.equals("GENROU") || model.equals("GENSAL")) {
                machines.append(record.rawText()).append(" /\n");
            } else if (model.equals("REGFMA1")) {
                gfm.append(record.rawText()).append(" /\n");
            }
        }
        machines.append("1090 'GENCLS' 1 999999 0 /\n")
                .append("1090 'GENCLS' 2 999999 0 /\n");
        Path machineDyr = tempDir.resolve("case6-machines.dyr");
        Path gfmDyr = tempDir.resolve("case6-regfma1.dyr");
        Files.writeString(machineDyr, machines);
        Files.writeString(gfmDyr, gfm);

        BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                .loadDStab(raw.toString(), machineDyr.toString()).getDStabilityNet();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                .setStrictImport(true);
        parser.parseDynFile(gfmDyr.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete(),
                () -> parser.getLastImportReport().failures().toString());
        assertEquals(5, parser.getLastImportReport().totalRecordCount());
        assertEquals(5, parser.getLastImportReport().attachedCountsByModel().get("REGFMA1"));

        network.setBypassDataCheck(true);
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(network);
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        assertTrue(loadflow.loadflow(), "Case 6 load flow must converge");

        long initialized = network.getBusList().stream()
                .flatMap(bus -> bus.getContributeGenList().stream())
                .filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regfma1Model)
                .peek(gen -> verifyEquilibrium((Regfma1Model) gen.getDynamicGenDevice(), gen))
                .count();
        assertEquals(5, initialized);
    }

    private static void verifyEquilibrium(Regfma1Model model, DStabGen gen) {
        Complex expected = gen.getGen();
        assertTrue(model.initStates(model.getDStabBus()), model.getExtendedDeviceId());
        Complex norton = (Complex) model.getOutputObject();
        Complex z = gen.getPosGenZ().multiply(gen.getZMultiFactor());
        Complex current = norton.subtract(model.getDStabBus().getVoltage().divide(z));
        Complex actual = model.getDStabBus().getVoltage().multiply(current.conjugate());
        assertEquals(expected.getReal(), actual.getReal(), 1.0e-8, model.getExtendedDeviceId());
        assertEquals(expected.getImaginary(), actual.getImaginary(), 1.0e-8, model.getExtendedDeviceId());
        for (int step = 0; step < 20; step++) {
            assertTrue(model.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, 0));
            assertTrue(model.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, 1));
            model.getOutputObject();
        }
        assertTrue(Double.isFinite(model.getSpeed()));
        assertTrue(Double.isFinite(model.getInternalVoltage()));
    }
}
