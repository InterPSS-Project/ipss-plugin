package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;

/** Private-data integration gate for all ESST4B records in Texas2k Case 1. */
class Texas2kEsst4bCoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of("testData", "private", "texas2k").toString()));
    private static final Path CASE = ROOT.resolve("Texas2k_series24_case1_2016summerpeak");
    private static final Path RAW = CASE.resolve("Texas2k_series24_case1_2016summerPeak_v36.RAW");
    private static final Path DYR = CASE.resolve("dynamic_models_case1.dyr");

    @Test
    void attachesEveryCase1Esst4bRecord(@TempDir Path tempDir) throws Exception {
        assumeTrue(Files.isRegularFile(RAW), "Missing private Texas2k RAW: " + RAW);
        assumeTrue(Files.isRegularFile(DYR), "Missing private Texas2k DYR: " + DYR);
        IpssCorePlugin.init();

        StringBuilder machines = new StringBuilder();
        StringBuilder exciters = new StringBuilder();
        PsseDyrRecordReader.read(DYR).forEach(record -> {
            if (record.canonicalModelName().equals("GENROU")
                    || record.canonicalModelName().equals("GENSAL")) {
                machines.append(record.rawText()).append(" /\n");
            } else if (record.canonicalModelName().equals("ESST4B")) {
                exciters.append(record.rawText()).append(" /\n");
            }
        });
        machines.append("1090 'GENCLS' 1 999999 0 /\n")
                .append("1090 'GENCLS' 2 999999 0 /\n");
        Path machineDyr = tempDir.resolve("texas2k-case1-machines.dyr");
        Path exciterDyr = tempDir.resolve("texas2k-case1-esst4b.dyr");
        Files.writeString(machineDyr, machines);
        Files.writeString(exciterDyr, exciters);

        BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                .loadDStab(RAW.toString(), machineDyr.toString()).getDStabilityNet();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                .setStrictImport(true);
        parser.parseDynFile(exciterDyr.toString());
        assertEquals(265, parser.getLastImportReport().totalRecordCount());
        assertEquals(265, parser.getLastImportReport().attachedCountsByModel().get("ESST4B"));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        network.setBypassDataCheck(true);
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(network);
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        assertTrue(loadflow.loadflow(), "Texas2k Case 1 load flow must converge");
    }
}
