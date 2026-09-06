package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelImportEntry;
import org.interpss.fadapter.psse.dyr.DynamicModelImportReport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.BaseDStabNetwork;

/** Whole-file attachment audit for all six Texas2k Series 24 cases. */
public class Texas2kFullDynamicCoverageTest {
    private static final Set<String> REVIEWED_INCOMPLETE_STACKS = Set.of(
            "5045:1", "5394:1", "5395:1", "7095:1", "7099:2");
    private static final Set<String> REVIEWED_DEPENDENCY_MODELS = Set.of(
            "REECA1", "REPCA1", "WTARA1", "WTPTA1", "WTTQA1");
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final List<CaseFile> CASES = List.of(
            new CaseFile("Texas2k_series24_case1_2016summerpeak",
                    "Texas2k_series24_case1_2016summerPeak_v36.RAW", "dynamic_models_case1.dyr", 2223, 0),
            new CaseFile("Texas2k_series24_case2_2016lowload",
                    "Texas2k_series24_case2_2016lowload.RAW", "dynamic_models_case2.dyr", 2223, 0),
            new CaseFile("Texas2k_series24_case3_2024summerpeak",
                    "Texas2k_series24_case3_2024summerpeak_v30.RAW", "dynamic_models_case3.dyr", 2965, 25),
            new CaseFile("Texas2k_series24_case4_2024lowload",
                    "Texas2k_series24_case4_2024lowload.RAW", "dynamic_models_case4.dyr", 2965, 25),
            new CaseFile("Texas2k_series24_case5_2024highrenewables",
                    "Texas2k_series24_case5_2024highrenewables.RAW", "dynamic_models_case5.dyr", 2965, 25),
            new CaseFile("Texas2k_series24_case6_2024lowloadwithgfm",
                    "Texas2k_series24_case6_2024lowloadwithgfm.RAW", "dynamic_models_case6.dyr", 2970, 25));

    @BeforeAll
    static void initializePlugin() {
        IpssCorePlugin.init();
    }

    @Test
    void everyRecordEitherAttachesOrIsAReviewedMissingMachineDependency(@TempDir Path tempDir)
            throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k root: " + ROOT);
        Path emptyDyr = tempDir.resolve("empty.dyr");
        Files.writeString(emptyDyr, "");

        for (CaseFile source : CASES) {
            Path directory = ROOT.resolve(source.directory());
            Path raw = directory.resolve(source.raw());
            Path dyr = directory.resolve(source.dyr());
            assumeTrue(Files.isRegularFile(raw), "Missing Texas2k RAW: " + raw);
            assumeTrue(Files.isRegularFile(dyr), "Missing Texas2k DYR: " + dyr);

            BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                    .loadDStab(raw.toString(), emptyDyr.toString()).getDStabilityNet();
            PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network));
            parser.parseDynFile(dyr.toString());
            DynamicModelImportReport report = parser.getLastImportReport();
            assertEquals(source.records(), report.totalRecordCount(), source.directory());
            assertEquals(source.reviewedDependencyFailures(), report.failures().size(),
                    () -> source.directory() + ": " + summarize(report.failures()));
            if (source.reviewedDependencyFailures() == 0) {
                assertTrue(report.isStrictlyComplete(), source.directory());
            } else {
                Map<String, Long> failures = report.failures().stream().collect(Collectors.groupingBy(
                        DynamicModelImportEntry::canonicalModelName, Collectors.counting()));
                assertEquals(Map.of("REECA1", 5L, "REPCA1", 5L,
                        "WTARA1", 5L, "WTPTA1", 5L, "WTTQA1", 5L), failures,
                        source.directory());
                assertEquals(REVIEWED_INCOMPLETE_STACKS,
                        report.failures().stream()
                                .map(entry -> entry.busNumber() + ":" + entry.deviceId())
                                .collect(Collectors.toSet()),
                        source.directory() + " incomplete generator keys");
                assertEquals(REVIEWED_DEPENDENCY_MODELS,
                        report.failures().stream()
                                .map(DynamicModelImportEntry::canonicalModelName)
                                .collect(Collectors.toSet()),
                        source.directory() + " rejected controller models");
                assertEquals(25, report.failures().stream()
                                .map(entry -> entry.canonicalModelName() + "@"
                                        + entry.busNumber() + ":" + entry.deviceId())
                                .collect(Collectors.toSet()).size(),
                        source.directory() + " unique model/device diagnostics");
            }
        }
    }

    private static String summarize(List<DynamicModelImportEntry> failures) {
        return failures.stream().limit(20)
                .map(entry -> entry.canonicalModelName() + "@" + entry.busNumber()
                        + ":" + entry.deviceId() + "=" + entry.message())
                .collect(Collectors.joining(", "));
    }

    private record CaseFile(String directory, String raw, String dyr, int records,
            int reviewedDependencyFailures) { }
}
