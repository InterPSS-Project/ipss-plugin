package org.interpss.plugin.contingency.util;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.BranchRatingProvider;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

import org.interpss.fadapter.psse.PSSEDirectParser;
public class BranchRatingFileUtilTest extends CorePluginTestSetup {
    @TempDir
    Path tempDir;

    @Test
    public void importCsvRatingsAndFallbackToRatingB() throws IOException, InterpssException {
        AclfNetwork net = loadIeee14();
        Path csv = tempDir.resolve("branch-ratings.csv");
        Files.writeString(csv, """
                branch_id,rating_mva
                Bus2->Bus5(1),75.5
                """);

        BranchRatingProvider provider = BranchRatingFileUtil.importBranchRatingProvider(csv);

        assertEquals(75.5, provider.getRatingMva(net.getBranch("Bus2->Bus5(1)")), 1.0e-8);
        AclfBranch missing = net.getBranch("Bus4->Bus7(1)");
        assertEquals(missing.getRatingMvaB(), provider.getRatingMva(missing), 1.0e-8);
    }

    @Test
    public void importJsonRatingsAndUseInParallelContingencyAnalysis()
            throws IOException, InterpssException {
        AclfNetwork net = loadIeee14();
        Path json = tempDir.resolve("branch-ratings.json");
        Files.writeString(json, """
                {
                  "branch_ratings": [
                    { "branch_id": "Bus2->Bus5(1)", "rating_mva": 50.0 }
                  ]
                }
                """);

        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setOverloadThreshold(0.0);
        config.setDclfInclLoss(false);
        config.setCustomBranchRatings(BranchRatingFileUtil.importBranchRatings(json));

        ConcurrentLinkedQueue<BranchCAResultRec> results =
                ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
                        net,
                        List.of(singleOpenContingency(net, "Bus1->Bus5(1)")),
                        Set.of("Bus2->Bus5(1)", "Bus4->Bus7(1)"),
                        config,
                        1);

        Map<String, BranchCAResultRec> byBranch = results.stream()
                .collect(java.util.stream.Collectors.toMap(result -> result.aclfBranch.getId(), result -> result));

        BranchCAResultRec customRated = byBranch.get("Bus2->Bus5(1)");
        assertTrue(customRated != null);
        assertEquals(50.0, customRated.getBranchRatingMva(), 1.0e-8);
        assertEquals(100.0 * Math.abs(customRated.getPostFlowMW()) / 50.0,
                customRated.calLoadingPercent(), 1.0e-8);

        BranchCAResultRec fallbackRated = byBranch.get("Bus4->Bus7(1)");
        assertTrue(fallbackRated != null);
        assertEquals(net.getBranch("Bus4->Bus7(1)").getRatingMvaB(),
                fallbackRated.getBranchRatingMva(), 1.0e-8);
    }

    @Test
    public void importExtUidCsvRatingsWithProviderFlag() throws IOException, InterpssException {
        AclfNetwork net = loadIeee14();
        AclfBranch customBranch = net.getBranch("Bus2->Bus5(1)");
        customBranch.setExtUID("EXT:2-5-1");
        Path csv = tempDir.resolve("branch-ratings-extuid.csv");
        Files.writeString(csv, """
                extUID,rating_mva
                EXT:2-5-1,88.0
                """);

        BranchRatingProvider provider = BranchRatingFileUtil.importBranchRatingProvider(csv, true);

        assertEquals(88.0, provider.getRatingMva(customBranch), 1.0e-8);
        AclfBranch missing = net.getBranch("Bus4->Bus7(1)");
        assertEquals(missing.getRatingMvaB(), provider.getRatingMva(missing), 1.0e-8);
    }

    @Test
    public void importMixedBranchIdAndExtUidJsonRatingsWithNetworkResolution()
            throws IOException, InterpssException {
        AclfNetwork net = loadIeee14();
        net.getBranch("Bus4->Bus7(1)").setExtUID("EXT:4-7-1");
        Path json = tempDir.resolve("branch-ratings-mixed.json");
        Files.writeString(json, """
                {
                  "branch_ratings": [
                    { "branch_id": "Bus2->Bus5(1)", "rating_mva": 50.0 },
                    { "extUID": "EXT:4-7-1", "rating_mva": 65.0 }
                  ]
                }
                """);

        Map<String, Double> ratings = BranchRatingFileUtil.importBranchRatings(net, json);

        assertEquals(50.0, ratings.get("Bus2->Bus5(1)"), 1.0e-8);
        assertEquals(65.0, ratings.get("Bus4->Bus7(1)"), 1.0e-8);
    }

    @Test
    public void labelledTexas2kCustomLineRatingsLoadFromCsvAndJsonForContingencyAnalysis()
            throws Exception {
        AclfNetwork net = loadLabelledTexas2k();
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf());

        Map<String, Double> ratingsByExtUid = customLineRatingsByExtUid(net);
        assertTrue(ratingsByExtUid.size() > 1000, "Expected broad custom ratings for labelled Texas2k lines");

        Path csv = labelledTexas2kRatingFixturePath("texas2k-line-ratings.csv");
        Path json = labelledTexas2kRatingFixturePath("texas2k-line-ratings.json");
        writeExtUidCsv(csv, ratingsByExtUid);
        writeExtUidJson(json, ratingsByExtUid);

        AclfBranch outageBranch = firstActiveNonRefLine(dclfAlgo, ratingsByExtUid.keySet());
        List<AclfBranch> monitorBranches = firstMonitorLines(net, ratingsByExtUid, outageBranch.getId(), 12);
        Set<String> monitorIds = monitorBranches.stream()
                .map(AclfBranch::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        DclfBranchOutage contingency = singleOpenContingency(net, outageBranch.getId());

        assertCustomRatingsUsedInContingencyResults(
                net,
                List.of(contingency),
                monitorBranches,
                monitorIds,
                BranchRatingFileUtil.importBranchRatingProvider(csv, true),
                ratingsByExtUid);
        assertCustomRatingsUsedInContingencyResults(
                net,
                List.of(contingency),
                monitorBranches,
                monitorIds,
                BranchRatingFileUtil.importBranchRatingProvider(net, json),
                ratingsByExtUid);
    }

    @Test
    public void labelledTexas2kCustomLineRatingSummaryTop20()
            throws Exception {
        AclfNetwork net = loadLabelledTexas2k();
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf());

        Map<String, Double> ratingsByExtUid = customLineRatingsByExtUid(net);
        Path csv = labelledTexas2kRatingFixturePath("texas2k-line-ratings.csv");
        writeExtUidCsv(csv, ratingsByExtUid);

        AclfBranch outageBranch = firstActiveNonRefLine(dclfAlgo, ratingsByExtUid.keySet());
        Set<String> monitorIds = allRatedMonitorLineIds(net, ratingsByExtUid, outageBranch.getId());
        BranchRatingProvider provider = BranchRatingFileUtil.importBranchRatingProvider(csv, true);

        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setOverloadThreshold(0.0);
        config.setDclfInclLoss(false);
        config.setBranchRatingProvider(provider);

        double originalShiftThreshold = BranchCAResultRec.ContingencyShiftThreshold;
        ConcurrentLinkedQueue<BranchCAResultRec> results;
        try {
            BranchCAResultRec.ContingencyShiftThreshold = 0.0;
            results = ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
                    net,
                    List.of(singleOpenContingency(net, outageBranch.getId())),
                    monitorIds,
                    config,
                    1);
        }
        finally {
            BranchCAResultRec.ContingencyShiftThreshold = originalShiftThreshold;
        }

        List<BranchCAResultRec> top20 = results.stream()
                .filter(result -> result.getBranchRatingMva() > 0.0)
                .sorted(Comparator
                        .comparingDouble((BranchCAResultRec result) ->
                                Math.abs(result.getPostFlowMW()) / result.getBranchRatingMva())
                        .reversed())
                .limit(20)
                .toList();

        assertEquals(20, top20.size());
        System.out.println(formatTop20Summary(outageBranch, monitorIds.size(), results.size(), top20));
    }

    private static AclfNetwork loadIeee14() throws InterpssException {
        return CorePluginFactory
                .getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
                .load("testData/adpter/ieee_format/ieee14.ieee")
                .getAclfNet();
    }

    private static AclfNetwork loadLabelledTexas2k() throws Exception {
        return new PSSEDirectParser().parse(
                "testData/adpter/psse/v36/Texas2k_series24_case1_2016summerPeak_v36_labeled.RAW");
    }

    private Path labelledTexas2kRatingFixturePath(String fileName) throws IOException {
        Path dir = tempDir.resolve("branch-ratings");
        Files.createDirectories(dir);
        return dir.resolve(fileName);
    }

    private static Map<String, Double> customLineRatingsByExtUid(AclfNetwork net) {
        Map<String, Double> ratings = new LinkedHashMap<>();
        net.getBranchList().stream()
                .filter(AclfBranch::isActive)
                .filter(branch -> branch.getExtUID() != null && branch.getExtUID().startsWith("line_"))
                .sorted(Comparator.comparing(AclfBranch::getId))
                .forEach(branch -> {
                    double rating = customLineRating(branch);
                    if (rating > 0.0) {
                        ratings.put(branch.getExtUID(), rating);
                    }
                });
        return ratings;
    }

    private static double customLineRating(AclfBranch branch) {
        double ratingB = Math.abs(branch.getRatingMvaB());
        if (ratingB > 0.0) {
            return ratingB * 1.10;
        }
        double ratingA = Math.abs(branch.getRatingMvaA());
        return ratingA > 0.0 ? ratingA * 1.20 : 0.0;
    }

    private static void writeExtUidCsv(Path path, Map<String, Double> ratingsByExtUid) throws IOException {
        StringBuilder csv = new StringBuilder("extUID,rating_mva\n");
        ratingsByExtUid.forEach((extUID, rating) ->
                csv.append(extUID).append(',').append(String.format(java.util.Locale.ROOT, "%.6f", rating)).append('\n'));
        Files.writeString(path, csv.toString());
    }

    private static void writeExtUidJson(Path path, Map<String, Double> ratingsByExtUid) throws IOException {
        StringBuilder json = new StringBuilder("{\n  \"branch_ratings\": [\n");
        int index = 0;
        for (Map.Entry<String, Double> entry : ratingsByExtUid.entrySet()) {
            if (index++ > 0) {
                json.append(",\n");
            }
            json.append("    { \"extUID\": \"")
                    .append(entry.getKey())
                    .append("\", \"rating_mva\": ")
                    .append(String.format(java.util.Locale.ROOT, "%.6f", entry.getValue()))
                    .append(" }");
        }
        json.append("\n  ]\n}\n");
        Files.writeString(path, json.toString());
    }

    private static AclfBranch firstActiveNonRefLine(
            ContingencyAnalysisAlgorithm dclfAlgo,
            Set<String> ratedExtUids) {
        return dclfAlgo.getDclfAlgoBranchList().stream()
                .map(dclfBranch -> dclfBranch.getBranch())
                .filter(branch -> branch.isActive()
                        && !branch.isConnect2RefBus()
                        && ratedExtUids.contains(branch.getExtUID()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No active non-reference Texas2k line found"));
    }

    private static List<AclfBranch> firstMonitorLines(
            AclfNetwork net,
            Map<String, Double> ratingsByExtUid,
            String outageBranchId,
            int count) {
        List<AclfBranch> monitors = new ArrayList<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (branch.isActive()
                    && !branch.getId().equals(outageBranchId)
                    && ratingsByExtUid.containsKey(branch.getExtUID())) {
                monitors.add(branch);
                if (monitors.size() == count) {
                    break;
                }
            }
        }
        assertEquals(count, monitors.size());
        return monitors;
    }

    private static Set<String> allRatedMonitorLineIds(
            AclfNetwork net,
            Map<String, Double> ratingsByExtUid,
            String outageBranchId) {
        Set<String> monitorIds = new LinkedHashSet<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (branch.isActive()
                    && !branch.getId().equals(outageBranchId)
                    && ratingsByExtUid.containsKey(branch.getExtUID())) {
                monitorIds.add(branch.getId());
            }
        }
        assertTrue(monitorIds.size() > 1000, "Expected broad Texas2k branch monitoring set");
        return monitorIds;
    }

    private static String formatTop20Summary(
            AclfBranch outageBranch,
            int monitorCount,
            int resultCount,
            List<BranchCAResultRec> top20) {
        StringBuilder table = new StringBuilder();
        table.append("\nTEXAS2K_CUSTOM_RATING_TOP20\n");
        table.append("outage=").append(outageBranch.getId())
                .append(" extUID=").append(outageBranch.getExtUID())
                .append(" monitoredBranches=").append(monitorCount)
                .append(" resultRows=").append(resultCount)
                .append('\n');
        table.append("| Rank | Branch ID | extUID | Pre MW | Shift MW | Post MW | Rating B | Custom Rating | Existing % | Custom % |\n");
        table.append("|---:|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        int rank = 1;
        for (BranchCAResultRec result : top20) {
            AclfBranch branch = result.aclfBranch;
            double ratingB = branch.getRatingMvaB();
            double existingLoading = ratingB > 0.0 ? 100.0 * Math.abs(result.getPostFlowMW()) / ratingB : 0.0;
            table.append(String.format(Locale.ROOT,
                    "| %d | %s | %s | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f |%n",
                    rank++,
                    branch.getId(),
                    branch.getExtUID(),
                    result.preFlowMW,
                    result.shiftedFlowMW,
                    result.getPostFlowMW(),
                    ratingB,
                    result.getBranchRatingMva(),
                    existingLoading,
                    result.calLoadingPercent()));
        }
        table.append("END_TEXAS2K_CUSTOM_RATING_TOP20\n");
        return table.toString();
    }

    private static void assertCustomRatingsUsedInContingencyResults(
            AclfNetwork net,
            List<DclfBranchOutage> contingencies,
            List<AclfBranch> monitorBranches,
            Set<String> monitorIds,
            BranchRatingProvider provider,
            Map<String, Double> ratingsByExtUid) {
        for (AclfBranch monitor : monitorBranches) {
            assertEquals(ratingsByExtUid.get(monitor.getExtUID()), provider.getRatingMva(monitor), 1.0e-6);
        }

        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setOverloadThreshold(0.0);
        config.setDclfInclLoss(false);
        config.setBranchRatingProvider(provider);

        ConcurrentLinkedQueue<BranchCAResultRec> results =
                ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
                        net,
                        contingencies,
                        monitorIds,
                        config,
                        1);
        assertTrue(!results.isEmpty(), "Expected Texas2k contingency analysis to return monitored branch records");
        for (BranchCAResultRec result : results) {
            double expectedRating = ratingsByExtUid.get(result.aclfBranch.getExtUID());
            assertEquals(expectedRating, result.getBranchRatingMva(), 1.0e-6);
            assertEquals(100.0 * Math.abs(result.getPostFlowMW()) / expectedRating,
                    result.calLoadingPercent(), 1.0e-8);
        }
    }

    private static DclfBranchOutage singleOpenContingency(AclfNetwork net, String branchId)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf());
        DclfBranchOutage contingency = createContingency("contBranch:" + branchId);
        DclfOutageBranch outage = createCaOutageBranch(
                dclfAlgo.getDclfAlgoBranch(branchId),
                ContingencyBranchOutageType.OPEN);
        outage.setDclfFlow(dclfAlgo.getDclfAlgoBranch(branchId).getDclfFlow());
        contingency.setOutageEquip(outage);
        return contingency;
    }
}
