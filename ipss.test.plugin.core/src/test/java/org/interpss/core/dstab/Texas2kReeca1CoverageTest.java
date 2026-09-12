package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
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

/** End-to-end attachment and initialization gate for Texas2k REECA1 chains. */
class Texas2kReeca1CoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of("testData", "private", "texas2k").toString()));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void case1AttachesAndInitializesAllReeca1Chains(@TempDir Path tempDir) throws Exception {
        verifyCase("Texas2k_series24_case1_2016summerpeak",
                "Texas2k_series24_case1_2016summerPeak_v36.RAW",
                "dynamic_models_case1.dyr", 107, tempDir);
    }

    @Test
    void case6AttachesAllRegcaBackedReeca1Chains(@TempDir Path tempDir) throws Exception {
        verifyCase("Texas2k_series24_case6_2024lowloadwithgfm",
                "Texas2k_series24_case6_2024lowloadwithgfm.RAW",
                "dynamic_models_case6.dyr", 301, tempDir);
    }

    private static void verifyCase(String directory, String rawName, String dyrName,
            int expectedChains, Path tempDir) throws Exception {
        Path caseDir = ROOT.resolve(directory);
        Path raw = caseDir.resolve(rawName);
        Path dyr = caseDir.resolve(dyrName);
        assumeTrue(Files.isRegularFile(raw), "Missing private Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing private Texas2k DYR: " + dyr);

        List<PsseDyrRecord> records = PsseDyrRecordReader.read(dyr);
        Set<String> regcaKeys = new HashSet<>();
        records.stream().filter(record -> record.canonicalModelName().equals("REGCA1"))
                .forEach(record -> regcaKeys.add(key(record)));
        assertEquals(expectedChains, regcaKeys.size());

        StringBuilder machines = new StringBuilder();
        StringBuilder renewable = new StringBuilder();
        for (PsseDyrRecord record : records) {
            String model = record.canonicalModelName();
            if (model.equals("GENROU") || model.equals("GENSAL")) {
                machines.append(record.rawText()).append(" /\n");
            } else if (model.equals("REGCA1")
                    || ((model.equals("REECA1") || model.equals("REPCA1"))
                            && regcaKeys.contains(key(record)))) {
                renewable.append(record.rawText()).append(" /\n");
            }
        }
        machines.append("1090 'GENCLS' 1 999999 0 /\n")
                .append("1090 'GENCLS' 2 999999 0 /\n");
        Path machineDyr = tempDir.resolve(directory + "-machines.dyr");
        Path renewableDyr = tempDir.resolve(directory + "-regca-reeca-repca.dyr");
        Files.writeString(machineDyr, machines);
        Files.writeString(renewableDyr, renewable);

        BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                .loadDStab(raw.toString(), machineDyr.toString()).getDStabilityNet();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                .setStrictImport(true);
        parser.parseDynFile(renewableDyr.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete(),
                () -> parser.getLastImportReport().failures().toString());
        assertEquals(expectedChains * 3, parser.getLastImportReport().totalRecordCount());
        assertEquals(expectedChains, parser.getLastImportReport().attachedCountsByModel().get("REGCA1"));
        assertEquals(expectedChains, parser.getLastImportReport().attachedCountsByModel().get("REECA1"));
        assertEquals(expectedChains, parser.getLastImportReport().attachedCountsByModel().get("REPCA1"));

        network.setBypassDataCheck(true);
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(network);
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        assertTrue(loadflow.loadflow(), directory + " load flow must converge");

        long initialized = network.getBusList().stream()
                .flatMap(bus -> bus.getContributeGenList().stream())
                .filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .map(gen -> (Regca1Model) gen.getDynamicGenDevice())
                .peek(model -> {
                    assertTrue(model.initStates(model.getDStabBus()), model.getExtendedDeviceId());
                    Reeca1Model controller = model.getReeca1Controller();
                    assertNotNull(controller, model.getExtendedDeviceId());
                    assertNotNull(controller.getPlantController(), model.getExtendedDeviceId());
                    assertEquals(1, controller.getPlantController().getData().puFlag(),
                            model.getExtendedDeviceId()
                                    + " omitted REPCA1 PUflag must use PowerWorld default");
                    assertTrue(Double.isFinite(controller.getIpcmd()));
                    assertTrue(Double.isFinite(controller.getIqcmd()));
                })
                .count();
        assertEquals(expectedChains, initialized);
    }

    private static String key(PsseDyrRecord record) {
        return record.busNumber() + ":" + record.deviceId().trim();
    }
}
