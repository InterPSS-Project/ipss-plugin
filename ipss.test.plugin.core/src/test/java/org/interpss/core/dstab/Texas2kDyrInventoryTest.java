package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.fadapter.psse.dyr.PsseDyrInventory;
import org.junit.jupiter.api.Test;

/** Exact coverage anchors for the six private Texas2k Series 24 DYR files. */
class Texas2kDyrInventoryTest {
    private static final Path CASE_ROOT = Path.of(System.getProperty("texas2k.dynamic.case.dir",
            Path.of("testData", "private", "texas2k").toString()));

    private static final List<String> CASES = List.of(
            "Texas2k_series24_case1_2016summerpeak/dynamic_models_case1.dyr",
            "Texas2k_series24_case2_2016lowload/dynamic_models_case2.dyr",
            "Texas2k_series24_case3_2024summerpeak/dynamic_models_case3.dyr",
            "Texas2k_series24_case4_2024lowload/dynamic_models_case4.dyr",
            "Texas2k_series24_case5_2024highrenewables/dynamic_models_case5.dyr",
            "Texas2k_series24_case6_2024lowloadwithgfm/dynamic_models_case6.dyr");

    @Test
    void allSixCasesMatchReviewedInventory() throws Exception {
        assumeTrue(Files.isDirectory(CASE_ROOT), "Missing private Texas2k case root: " + CASE_ROOT);
        List<Path> paths = CASES.stream().map(CASE_ROOT::resolve).toList();
        for (Path path : paths) assumeTrue(Files.isRegularFile(path), "Missing Texas2k DYR: " + path);

        int[] expectedTotals = {2223, 2223, 2965, 2965, 2965, 2970};
        for (int i = 0; i < paths.size(); i++) {
            assertEquals(expectedTotals[i], PsseDyrInventory.scan(paths.get(i)).totalRecordCount(),
                    paths.get(i).toString());
        }

        PsseDyrInventory union = PsseDyrInventory.scan(paths);
        assertEquals(16311, union.totalRecordCount());
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("ESST1A", 168), Map.entry("ESST4B", 1590),
                Map.entry("EXST1", 756), Map.entry("GENROU", 2460),
                Map.entry("GENSAL", 150), Map.entry("GGOV1", 2202),
                Map.entry("HYGOV", 150), Map.entry("IEEEG1", 258),
                Map.entry("IEEET1", 96), Map.entry("PSS2A", 2610),
                Map.entry("REECA1", 1438), Map.entry("REGCA1", 1418),
                Map.entry("REGFMA1", 5), Map.entry("REPCA1", 1438),
                Map.entry("WTARA1", 524), Map.entry("WTPTA1", 524),
                Map.entry("WTTQA1", 524));
        assertEquals(expected.size(), union.byModel().size());
        expected.forEach((model, count) -> assertEquals(count, union.count(model), model));

        List<String> schemaMismatches = new ArrayList<>();
        union.byModel().forEach((name, stats) -> {
            int expectedParameterCount = org.interpss.fadapter.psse.dyr.DynamicModelCatalog.find(name)
                    .orElseThrow().parameterCount();
            if (!stats.parameterCounts().equals(java.util.Set.of(expectedParameterCount))) {
                schemaMismatches.add(name + "=" + stats.parameterCounts());
            }
        });
        assertTrue(schemaMismatches.isEmpty(), "Unexpected Texas2k DYR schemas: " + schemaMismatches);
    }
}
