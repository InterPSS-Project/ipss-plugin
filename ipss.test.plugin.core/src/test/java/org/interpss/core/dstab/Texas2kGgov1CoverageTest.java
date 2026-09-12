package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;

/** Private-data integration gate for every Case 1 synchronous machine and GGOV1. */
class Texas2kGgov1CoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of("testData", "private", "texas2k").toString()));
    private static final Path CASE = ROOT.resolve("Texas2k_series24_case1_2016summerpeak");
    private static final Path RAW = CASE.resolve("Texas2k_series24_case1_2016summerPeak_v36.RAW");
    private static final Path DYR = CASE.resolve("dynamic_models_case1.dyr");

    @Test
    void attachesAllCase1Ggov1ModelsOnSolvedNetwork(@TempDir Path tempDir) throws Exception {
        assumeTrue(Files.isRegularFile(RAW), "Missing private Texas2k RAW: " + RAW);
        assumeTrue(Files.isRegularFile(DYR), "Missing private Texas2k DYR: " + DYR);
        IpssCorePlugin.init();

        StringBuilder machines = new StringBuilder();
        StringBuilder runtimeDevices = new StringBuilder();
        PsseDyrRecordReader.read(DYR).forEach(record -> {
            if (record.canonicalModelName().equals("GENROU")
                    || record.canonicalModelName().equals("GENSAL")) {
                machines.append(record.rawText()).append(" /\n");
            } else if (record.canonicalModelName().equals("GGOV1")) {
                runtimeDevices.append(record.rawText()).append(" /\n");
            } else if (record.canonicalModelName().equals("REGCA1")) {
                runtimeDevices.append(record.rawText()).append(" /\n");
            }
        });
        // The source DYR intentionally omits the two Bus 1090 external equivalents.
        // Give them explicit classical infinite-inertia models in this isolated slice test.
        machines.append("1090 'GENCLS' 1 999999 0 /\n")
                .append("1090 'GENCLS' 2 999999 0 /\n");
        Path machineDyr = tempDir.resolve("texas2k-case1-machines.dyr");
        Path governorDyr = tempDir.resolve("texas2k-case1-ggov1.dyr");
        Files.writeString(machineDyr, machines);
        Files.writeString(governorDyr, runtimeDevices);

        BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                .loadDStab(RAW.toString(), machineDyr.toString()).getDStabilityNet();

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                .setStrictImport(true);
        parser.parseDynFile(governorDyr.toString());
        assertEquals(474, parser.getLastImportReport().totalRecordCount());
        assertEquals(367, parser.getLastImportReport().attachedCountsByModel().get("GGOV1"));
        assertEquals(107, parser.getLastImportReport().attachedCountsByModel().get("REGCA1"));

        List<String> missingDevices = new ArrayList<>();
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            for (DStabGen gen : bus.getContributeGenList()) {
                if (gen.isActive() && gen.getDynamicGenDevice() == null) {
                    missingDevices.add(bus.getId() + ":" + gen.getId());
                }
            }
        }
        assertTrue(missingDevices.isEmpty(), "Active generators without a dynamic device: " + missingDevices);

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
