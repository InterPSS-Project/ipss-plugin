package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.interpss.IpssCorePlugin;
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

/** Strict private-data gate for the Texas2k Type-3 wind stacks without WTDTA1. */
class Texas2kType3WindCoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final Set<String> STACK_MODELS = Set.of(
            "REGCA1", "REECA1", "WTPTA1", "WTTQA1", "WTARA1", "REPCA1");

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void case3AttachesEveryRegcaBackedType3WindStack(@TempDir Path tempDir) throws Exception {
        verifyCase("Texas2k_series24_case3_2024summerpeak",
                "Texas2k_series24_case3_2024summerpeak.RAW",
                "dynamic_models_case3.dyr", tempDir);
    }

    @Test
    void case6AttachesEveryRegcaBackedType3WindStack(@TempDir Path tempDir) throws Exception {
        verifyCase("Texas2k_series24_case6_2024lowloadwithgfm",
                "Texas2k_series24_case6_2024lowloadwithgfm.RAW",
                "dynamic_models_case6.dyr", tempDir);
    }

    private static void verifyCase(String directory, String rawName, String dyrName, Path tempDir)
            throws Exception {
        Path caseDir = ROOT.resolve(directory);
        Path raw = caseDir.resolve(rawName);
        Path dyr = caseDir.resolve(dyrName);
        assumeTrue(Files.isRegularFile(raw), "Missing private Texas2k RAW: " + raw);
        assumeTrue(Files.isRegularFile(dyr), "Missing private Texas2k DYR: " + dyr);

        List<PsseDyrRecord> records = PsseDyrRecordReader.read(dyr);
        Set<String> sourceWindKeys = new HashSet<>();
        records.stream().filter(record -> record.canonicalModelName().equals("WTARA1"))
                .forEach(record -> sourceWindKeys.add(key(record)));
        assertEquals(104, sourceWindKeys.size());
        Set<String> regcaKeys = new HashSet<>();
        records.stream().filter(record -> record.canonicalModelName().equals("REGCA1"))
                .forEach(record -> regcaKeys.add(key(record)));
        Set<String> windKeys = new HashSet<>(sourceWindKeys);
        windKeys.retainAll(regcaKeys);
        // Five source stacks intentionally omit REGCA1 and are gated with the
        // REGFMA1/default-machine work rather than silently inventing a converter.
        assertEquals(99, windKeys.size());

        StringBuilder machines = new StringBuilder();
        StringBuilder wind = new StringBuilder();
        for (PsseDyrRecord record : records) {
            String model = record.canonicalModelName();
            if (model.equals("GENROU") || model.equals("GENSAL")) {
                machines.append(record.rawText()).append(" /\n");
            } else if (STACK_MODELS.contains(model) && windKeys.contains(key(record))) {
                wind.append(record.rawText()).append(" /\n");
            }
        }
        machines.append("1090 'GENCLS' 1 999999 0 /\n")
                .append("1090 'GENCLS' 2 999999 0 /\n");
        Path machineDyr = tempDir.resolve(directory + "-machines.dyr");
        Path windDyr = tempDir.resolve(directory + "-type3.dyr");
        Files.writeString(machineDyr, machines);
        Files.writeString(windDyr, wind);

        BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                .loadDStab(raw.toString(), machineDyr.toString()).getDStabilityNet();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                .setStrictImport(true);
        parser.parseDynFile(windDyr.toString());
        assertEquals(594, parser.getLastImportReport().totalRecordCount());
        STACK_MODELS.forEach(model -> assertEquals(99,
                parser.getLastImportReport().attachedCountsByModel().get(model), model));

        network.setBypassDataCheck(true);
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(network);
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        assertTrue(loadflow.loadflow(), directory + " load flow must converge");

        long complete = network.getBusList().stream()
                .flatMap(bus -> bus.getContributeGenList().stream())
                .filter(DStabGen.class::isInstance)
                .map(DStabGen.class::cast)
                .filter(gen -> gen.getDynamicGenDevice() instanceof Regca1Model)
                .map(gen -> (Regca1Model) gen.getDynamicGenDevice())
                .filter(model -> model.getReeca1Controller() != null
                        && model.getReeca1Controller().getWindControlStack() != null
                        && model.getReeca1Controller().getWindControlStack().isComplete())
                .peek(model -> assertTrue(model.initStates(model.getDStabBus()),
                        model.getExtendedDeviceId()))
                .count();
        assertEquals(99, complete);
    }

    private static String key(PsseDyrRecord record) {
        return record.busNumber() + ":" + record.deviceId().trim();
    }
}
