package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;

/** End-to-end attachment gate for the conventional generator stacks. */
class Texas2kConventionalStackCoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final Set<String> CONVENTIONAL_CONTROLLERS = Set.of(
            "EXST1", "IEEET1", "ESST1A", "ESST4B",
            "IEEEG1", "GGOV1", "HYGOV", "PSS2A");
    private static final Map<String, Integer> EXPECTED = Map.ofEntries(
            Map.entry("EXST1", 126), Map.entry("IEEET1", 16),
            Map.entry("ESST1A", 28), Map.entry("ESST4B", 265),
            Map.entry("IEEEG1", 43), Map.entry("GGOV1", 367),
            Map.entry("HYGOV", 25), Map.entry("PSS2A", 435));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void case1ConventionalStacksAreComplete(@TempDir Path tempDir) throws Exception {
        verifyCase("Texas2k_series24_case1_2016summerpeak",
                "Texas2k_series24_case1_2016summerPeak_v36.RAW",
                "dynamic_models_case1.dyr", tempDir);
    }

    @Test
    void case6ConventionalStacksAreComplete(@TempDir Path tempDir) throws Exception {
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

        StringBuilder machines = new StringBuilder();
        StringBuilder controllers = new StringBuilder();
        List<String> controlledMachineIds = new ArrayList<>();
        PsseDyrRecordReader.read(dyr).forEach(record -> {
                    String model = record.canonicalModelName();
                    if (model.equals("GENROU") || model.equals("GENSAL")) {
                        machines.append(record.rawText()).append(" /\n");
                    } else if (CONVENTIONAL_CONTROLLERS.contains(model)) {
                        controllers.append(record.rawText()).append(" /\n");
                    }
                    if (model.equals("PSS2A")) {
                        controlledMachineIds.add("Bus" + record.fields().get(0)
                                + "-mach" + record.fields().get(2).replace("'", ""));
                    }
                });
        // The source cases intentionally omit machine models for the two Bus 1090 sources.
        machines.append("1090 'GENCLS' 1 999999 0 /\n")
                .append("1090 'GENCLS' 2 999999 0 /\n");
        Path machineDyr = tempDir.resolve(directory + "-machines.dyr");
        Path controllerDyr = tempDir.resolve(directory + "-controllers.dyr");
        Files.writeString(machineDyr, machines);
        Files.writeString(controllerDyr, controllers);

        BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                .loadDStab(raw.toString(), machineDyr.toString()).getDStabilityNet();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                .setStrictImport(true);
        parser.parseDynFile(controllerDyr.toString());
        assertEquals(1305, parser.getLastImportReport().totalRecordCount());
        EXPECTED.forEach((model, count) -> assertEquals(count,
                parser.getLastImportReport().attachedCountsByModel().get(model), model));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        long completeStacks = controlledMachineIds.stream()
                .map(network::getMachine)
                .filter(machine -> machine != null && machine.getExciter() != null
                        && machine.getGovernor() != null
                        && machine.getStabilizer() != null)
                .count();
        assertEquals(435, completeStacks);

        network.setBypassDataCheck(true);
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(network);
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        assertTrue(loadflow.loadflow(), directory + " load flow must converge");
    }
}
