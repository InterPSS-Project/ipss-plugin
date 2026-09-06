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
import org.interpss.fadapter.psse.PsseGnetIdvProcessor;
import org.interpss.fadapter.psse.dyr.DynamicModelImportEntry;
import org.interpss.fadapter.psse.dyr.DynamicModelImportReport;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.BaseDStabNetwork;

/** Whole-file attachment audit for all six Texas2k Series 24 cases. */
public class Texas2kFullDynamicCoverageTest {
    private static final Set<String> REVIEWED_INCOMPLETE_STACKS = Set.of(
            "5045:1", "7099:2");
    private static final Set<String> GNET_REMOVED_STACKS = Set.of(
            "5394:1", "5395:1", "7095:1");
    private static final Set<String> REVIEWED_DEPENDENCY_MODELS = Set.of(
            "REECA1", "REPCA1", "WTARA1", "WTPTA1", "WTTQA1");
    private static final Path ROOT = Path.of(System.getProperty("texas2k.case.root",
            Path.of(System.getProperty("user.home"), "OneDrive", "Documents", "qiuhua",
                    "private_cases", "Texas2k_series24_cases_with_dynamics",
                    "Texas2k_series24_cases_with_dynamics").toString()));
    private static final List<CaseFile> CASES = List.of(
            new CaseFile("Texas2k_series24_case1_2016summerpeak",
                    "Texas2k_series24_case1_2016summerPeak_v36.RAW", "dynamic_models_case1.dyr",
                    "dynamic_models_case1_gnet.idv", 2223, 0, 0),
            new CaseFile("Texas2k_series24_case2_2016lowload",
                    "Texas2k_series24_case2_2016lowload.RAW", "dynamic_models_case2.dyr",
                    "dynamic_models_case2_gnet.idv", 2223, 0, 0),
            new CaseFile("Texas2k_series24_case3_2024summerpeak",
                    "Texas2k_series24_case3_2024summerpeak_v30.RAW", "dynamic_models_case3.dyr",
                    "dynamic_models_case3_gnet.idv", 2965, 10, 15),
            new CaseFile("Texas2k_series24_case4_2024lowload",
                    "Texas2k_series24_case4_2024lowload.RAW", "dynamic_models_case4.dyr",
                    "dynamic_models_case4_gnet.idv", 2965, 10, 15),
            new CaseFile("Texas2k_series24_case5_2024highrenewables",
                    "Texas2k_series24_case5_2024highrenewables.RAW", "dynamic_models_case5.dyr",
                    "dynamic_models_case5_gnet.idv", 2965, 10, 15),
            new CaseFile("Texas2k_series24_case6_2024lowloadwithgfm",
                    "Texas2k_series24_case6_2024lowloadwithgfm.RAW", "dynamic_models_case6.dyr",
                    "dynamic_models_case6_gnet.idv", 2970, 10, 15));

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
            Path gnet = directory.resolve(source.gnet());
            assumeTrue(Files.isRegularFile(raw), "Missing Texas2k RAW: " + raw);
            assumeTrue(Files.isRegularFile(dyr), "Missing Texas2k DYR: " + dyr);
            assumeTrue(Files.isRegularFile(gnet), "Missing Texas2k GNET: " + gnet);

            BaseDStabNetwork<?, ?> network = new PSSEMultiFileLoader()
                    .loadDStab(raw.toString(), emptyDyr.toString()).getDStabilityNet();
            var gnetResult = PsseGnetIdvProcessor.apply(network, gnet.toString());
            PSSEDStabDirectParser parser = new PSSEDStabDirectParser(new DStabNetworkBuilder(network))
                    .setGnetRemovedGenerators(gnetResult.convertedGeneratorKeys());
            parser.parseDynFile(dyr.toString());
            DynamicModelImportReport report = parser.getLastImportReport();
            assertEquals(source.records(), report.totalRecordCount(), source.directory());
            assertEquals(source.reviewedDependencyFailures(), report.failures().size(),
                    () -> source.directory() + ": " + summarize(report.failures()));
            assertEquals(source.gnetSkippedRecords(),
                    report.count(DynamicModelImportStatus.SKIPPED_GNET), source.directory());
            if (source.gnetSkippedRecords() > 0) {
                List<DynamicModelImportEntry> skipped = report.entries().stream()
                        .filter(entry -> entry.status() == DynamicModelImportStatus.SKIPPED_GNET)
                        .toList();
                assertEquals(GNET_REMOVED_STACKS,
                        skipped.stream().map(entry -> entry.busNumber() + ":" + entry.deviceId())
                                .collect(Collectors.toSet()),
                        source.directory() + " GNET-removed generator keys");
                assertEquals(REVIEWED_DEPENDENCY_MODELS,
                        skipped.stream().map(DynamicModelImportEntry::canonicalModelName)
                                .collect(Collectors.toSet()),
                        source.directory() + " GNET-skipped controller models");
                assertEquals(15, skipped.stream()
                        .map(entry -> entry.canonicalModelName() + "@" + entry.busNumber()
                                + ":" + entry.deviceId())
                        .collect(Collectors.toSet()).size(),
                        source.directory() + " unique GNET skips");
            }
            if (source.reviewedDependencyFailures() == 0) {
                assertTrue(report.isStrictlyComplete(), source.directory());
            } else {
                Map<String, Long> failures = report.failures().stream().collect(Collectors.groupingBy(
                        DynamicModelImportEntry::canonicalModelName, Collectors.counting()));
                assertEquals(Map.of("REECA1", 2L, "REPCA1", 2L,
                        "WTARA1", 2L, "WTPTA1", 2L, "WTTQA1", 2L), failures,
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
                assertEquals(10, report.failures().stream()
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

    private record CaseFile(String directory, String raw, String dyr, String gnet, int records,
            int reviewedDependencyFailures, int gnetSkippedRecords) { }
}
