package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

/** Private-data integration gate for all HYGOV records in Texas2k Case 2. */
public class Texas2kHygovCoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final Path CASE = ROOT.resolve("Texas2k_series24_case2_2016lowload");
    private static final Path RAW = CASE.resolve("Texas2k_series24_case2_2016lowload.RAW");
    private static final Path DYR = CASE.resolve("dynamic_models_case2.dyr");

    @Test
    void inventoriesEveryTexas2kHygovParameterProfile() throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        List<org.interpss.fadapter.psse.dyr.PsseDyrRecord> records = new ArrayList<>();
        try (var directories = Files.list(ROOT)) {
            for (Path directory : directories
                    .filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList())) {
                try (var files = Files.list(directory)) {
                    Path dyr = files.filter(path -> path.getFileName().toString().endsWith(".dyr"))
                            .findFirst().orElse(null);
                    if (dyr != null) {
                        PsseDyrRecordReader.read(dyr).stream()
                                .filter(record -> record.canonicalModelName().equals("HYGOV"))
                                .forEach(records::add);
                    }
                }
            }
        }

        assertEquals(150, records.size(), "25 HYGOV records in each of six cases");
        assertTrue(records.stream().allMatch(record -> record.parameterCount() == 12),
                "Every Texas2k HYGOV record must retain all 12 PSS/E parameters");
        Set<List<String>> profiles = records.stream()
                .map(record -> List.copyOf(record.parameters()))
                .collect(Collectors.toSet());
        assertEquals(25, profiles.size(), "Texas2k HYGOV parameter profiles");
    }

    @Test
    void attachesEveryCase2HygovRecord(@TempDir Path tempDir) throws Exception {
        assumeTrue(Files.isRegularFile(RAW), "Missing private Texas2k RAW: " + RAW);
        assumeTrue(Files.isRegularFile(DYR), "Missing private Texas2k DYR: " + DYR);
        IpssCorePlugin.init();

        StringBuilder machines = new StringBuilder();
        StringBuilder governors = new StringBuilder();
        PsseDyrRecordReader.read(DYR).forEach(record -> {
            if (record.canonicalModelName().equals("GENROU")
                    || record.canonicalModelName().equals("GENSAL")) {
                machines.append(record.rawText()).append(" /\n");
            } else if (record.canonicalModelName().equals("HYGOV")) {
                governors.append(record.rawText()).append(" /\n");
            } else if (record.canonicalModelName().equals("REGCA1")) {
                governors.append(record.rawText()).append(" /\n");
            }
        });
        machines.append("1090 'GENCLS' 1 999999 0 /\n")
                .append("1090 'GENCLS' 2 999999 0 /\n");
        Path machineDyr = tempDir.resolve("texas2k-case2-machines.dyr");
        Path governorDyr = tempDir.resolve("texas2k-case2-hygov.dyr");
        Files.writeString(machineDyr, machines);
        Files.writeString(governorDyr, governors);

        BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                .loadDStab(RAW.toString(), machineDyr.toString()).getDStabilityNet();
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                .setStrictImport(true);
        parser.parseDynFile(governorDyr.toString());
        assertEquals(132, parser.getLastImportReport().totalRecordCount());
        assertEquals(25, parser.getLastImportReport().attachedCountsByModel().get("HYGOV"));
        assertEquals(107, parser.getLastImportReport().attachedCountsByModel().get("REGCA1"));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        network.setBypassDataCheck(true);
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(network);
        loadflow.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        loadflow.getDataCheckConfig().setTurnOffIslandBus(true);
        loadflow.setNonDivergent(true);
        loadflow.setMaxIterations(50);
        assertTrue(loadflow.loadflow(), "Texas2k Case 2 load flow must converge");

        List<String> missingDevices = new ArrayList<>();
        for (BaseDStabBus<?, ?> bus : network.getBusList()) {
            for (DStabGen gen : bus.getContributeGenList()) {
                if (gen.isActive() && gen.getDynamicGenDevice() == null) {
                    missingDevices.add(bus.getId() + ":" + gen.getId());
                }
            }
        }
        assertTrue(missingDevices.isEmpty(), "Active generators without a dynamic device: "
                + missingDevices);

        // Model-level tests exercise initialization and time stepping. Full Case 2
        // initialization is retained for the later coupled-stack gate because this
        // slice intentionally omits its exciter and renewable-controller records.
    }
}
