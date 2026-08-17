package org.interpss.plugin.contingency.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import com.interpss.core.contingency.definition.ContingencyAction;
import com.interpss.core.contingency.definition.ContingencyActionType;
import com.interpss.core.contingency.definition.ContingencyDefinition;
import com.interpss.core.contingency.definition.ContingencyObjectType;
import org.interpss.plugin.contingency.util.DclfContingencyPreScreenUtil.Classification;
import org.interpss.plugin.contingency.util.DclfContingencyPreScreenUtil.ContingencyPreScreenReport;
import org.interpss.plugin.contingency.util.DclfContingencyPreScreenUtil.ContingencyPreScreenResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.contingency.dclf.DclfMultiOutage;

public class DclfContingencyPreScreenUtilTest extends CorePluginTestSetup {
    @TempDir
    Path tempDir;

    @Test
    public void scanIeee14ContingencyDefinitionsForIslandingAndEptdfStatus()
            throws Exception {
        AclfNetwork net = loadIeee14();
        ContingencyDefinition normal = definition(
                "normal-n1",
                open("Bus2->Bus4(1)"));
        ContingencyDefinition islanding = definition(
                "bus14-island",
                open("Bus9->Bus14(1)"),
                open("Bus13->Bus14(1)"));
        ContingencyDefinition duplicate = definition(
                "duplicate-non-islanding",
                open("Bus2->Bus5(1)"),
                open("Bus2->Bus5(1)"));

        ContingencyPreScreenReport report =
                DclfContingencyPreScreenUtil.scan(net, List.of(normal, islanding, duplicate));

        assertEquals(3, report.totalContingencies());
        assertEquals(1, report.normalContingencies());
        assertEquals(1, report.islandingContingencies());
        assertEquals(1, report.ePtdfSingularContingencies());
        assertEquals(0, report.unsupportedContingencies());

        ContingencyPreScreenResult islandResult = byId(report, "bus14-island");
        assertEquals(Classification.ISLANDING, islandResult.classification());
        assertEquals(1, islandResult.islandCount());
        assertEquals(List.of(1), islandResult.islandBusCounts());

        ContingencyPreScreenResult duplicateResult = byId(report, "duplicate-non-islanding");
        assertEquals(Classification.E_PTDF_SINGULAR, duplicateResult.classification());
        assertEquals(2, duplicateResult.ePtdfOriginalSize());
        assertTrue(duplicateResult.ePtdfEffectiveSize() < duplicateResult.ePtdfOriginalSize());
    }

    @Test
    public void scanContingenciesFromJsonFile()
            throws Exception {
        AclfNetwork net = loadIeee14();
        File file = tempDir.resolve("pre-screen-contingencies.json").toFile();
        ContingencyFileUtil.exportContingencyDefinitionsToJson(
                file,
                List.of(
                        definition("normal-n1", open("Bus2->Bus4(1)")),
                        definition("bus14-island", open("Bus9->Bus14(1)"), open("Bus13->Bus14(1)"))));

        ContingencyPreScreenReport report =
                ContingencyFileUtil.preScreenDclfContingencies(net, file);

        assertEquals(2, report.totalContingencies());
        assertEquals(1, report.normalContingencies());
        assertEquals(1, report.islandingContingencies());
    }

    @Test
    public void scanTexas2kLargeContingencyJson()
            throws Exception {
        AclfNetwork net = loadTexas2k();
        File contingencies = new File("testData/psse/v36/Texas2k/2k_contingencies_115kVAbove.json");

        long startNs = System.nanoTime();
        ContingencyPreScreenReport report =
                ContingencyFileUtil.preScreenDclfContingencies(net, contingencies);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        System.out.println("Texas2k contingency pre-screen: total=" + report.totalContingencies()
                + ", islanding=" + report.islandingContingencies()
                + ", ePtdfSingular=" + report.ePtdfSingularContingencies()
                + ", normal=" + report.normalContingencies()
                + ", unsupported=" + report.unsupportedContingencies()
                + ", elapsedMs=" + elapsedMs);

        assertTrue(report.totalContingencies() > 1000);
        assertEquals(report.totalContingencies(),
                report.islandingContingencies()
                        + report.ePtdfSingularContingencies()
                        + report.normalContingencies()
                        + report.unsupportedContingencies());
    }

    @Test
    public void createAndScanTexas2kMixedPreScreenContingencyJson()
            throws Exception {
        AclfNetwork net = loadTexas2k();
        List<ContingencyDefinition> definitions = createTexas2kMixedPreScreenDefinitions(net);
        assertEquals(100, definitions.size());

        File file = tempDir.resolve("2k_mixed_prescreen_contingencies_100.json")
                .toFile();
        ContingencyFileUtil.exportContingencyDefinitionsToJson(file, definitions);

        long startNs = System.nanoTime();
        ContingencyPreScreenReport report =
                ContingencyFileUtil.preScreenDclfContingencies(net, file);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        System.out.println("Texas2k mixed pre-screen fixture: file=" + file.getPath()
                + ", total=" + report.totalContingencies()
                + ", islanding=" + report.islandingContingencies()
                + ", ePtdfSingular=" + report.ePtdfSingularContingencies()
                + ", normal=" + report.normalContingencies()
                + ", unsupported=" + report.unsupportedContingencies()
                + ", elapsedMs=" + elapsedMs);

        assertEquals(100, report.totalContingencies());
        assertEquals(30, report.islandingContingencies());
        assertEquals(30, report.ePtdfSingularContingencies());
        assertEquals(40, report.normalContingencies());
        assertEquals(0, report.unsupportedContingencies());
    }

    @Test
    public void runTexas2kMixedContingencyAnalysisWithIslandingAndSingularCases()
            throws Exception {
        AclfNetwork net = loadTexas2k();
        File file = new File("testData/psse/v36/Texas2k/2k_mixed_prescreen_contingencies_100.json");
        if (!file.isFile()) {
            ContingencyFileUtil.exportContingencyDefinitionsToJson(
                    file,
                    createTexas2kMixedPreScreenDefinitions(net));
        }

        ContingencyPreScreenReport preScreen =
                ContingencyFileUtil.preScreenDclfContingencies(net, file);
        assertEquals(100, preScreen.totalContingencies());
        assertEquals(30, preScreen.islandingContingencies());
        assertEquals(30, preScreen.ePtdfSingularContingencies());
        assertEquals(40, preScreen.normalContingencies());

        ContingencyAnalysisAlgorithm dclfAlgo =
                com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf());
        List<ContingencyDefinition> definitions =
                ContingencyFileUtil.importContingencyDefinitionsFromJson(file);
        List<DclfMultiOutage> contingencies =
                new DclfMultiOutageContingencyHelper(dclfAlgo)
                        .createDclfMultiOutageContListFromDefinitions(definitions);
        Set<String> monitorIds = sortedActiveBranches(net).stream()
                .limit(200)
                .map(AclfBranch::getId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setOverloadThreshold(0.0);
        config.setDclfInclLoss(false);

        long startNs = System.nanoTime();
        ConcurrentLinkedQueue<BranchCAResultRec> results =
                ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
                        net,
                        contingencies,
                        monitorIds,
                        config,
                        1);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        Set<String> contingenciesWithResults = results.stream()
                .map(result -> result.contingency.getId())
                .collect(Collectors.toSet());
        long finiteResultCount = results.stream()
                .filter(result -> Double.isFinite(result.getPostFlowMW()))
                .count();

        System.out.println("Texas2k mixed contingency analysis: contingencies=" + contingencies.size()
                + ", monitors=" + monitorIds.size()
                + ", resultRows=" + results.size()
                + ", contingenciesWithResults=" + contingenciesWithResults.size()
                + ", finiteResultRows=" + finiteResultCount
                + ", elapsedMs=" + elapsedMs
                + ", preScreen=[islanding=" + preScreen.islandingContingencies()
                + ", ePtdfSingular=" + preScreen.ePtdfSingularContingencies()
                + ", normal=" + preScreen.normalContingencies()
                + "]");

        assertTrue(!results.isEmpty(), "Expected mixed contingency analysis to return result rows");
        assertEquals(results.size(), finiteResultCount);
        assertTrue(contingenciesWithResults.size() > 0);
    }

    private static AclfNetwork loadIeee14() throws Exception {
        return CorePluginFactory
                .getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
                .load("testData/adpter/ieee_format/ieee14.ieee")
                .getAclfNet();
    }

    private static AclfNetwork loadTexas2k() throws Exception {
        return IpssAdapter.importAclfNet(
                        "testData/adpter/psse/v36/Texas2k_series24_case1_2016summerPeak_v36_labeled.RAW")
                .setFormat(IpssAdapter.FileFormat.PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_36)
                .load()
                .getImportedObj();
    }

    private static List<ContingencyDefinition> createTexas2kMixedPreScreenDefinitions(AclfNetwork net)
            throws Exception {
        List<ContingencyDefinition> islands =
                firstByClassification(net, texas2kIslandCandidates(net), Classification.ISLANDING, 30);
        List<ContingencyDefinition> singulars =
                firstByClassification(net, texas2kParallelOutageCandidates(net), Classification.E_PTDF_SINGULAR, 30);
        if (singulars.size() < 30) {
            List<ContingencyDefinition> duplicates =
                    firstByClassification(net, texas2kDuplicateOutageCandidates(net), Classification.E_PTDF_SINGULAR, 30 - singulars.size());
            singulars.addAll(duplicates);
        }
        List<ContingencyDefinition> normals =
                firstByClassification(net, texas2kNormalCandidates(net), Classification.NORMAL, 40);

        assertEquals(30, islands.size(), "Expected 30 Texas2k islanding candidates");
        assertEquals(30, singulars.size(), "Expected 30 Texas2k E-PTDF singular candidates");
        assertEquals(40, normals.size(), "Expected 40 Texas2k normal candidates");

        List<ContingencyDefinition> definitions = new ArrayList<>(100);
        definitions.addAll(islands);
        definitions.addAll(singulars);
        definitions.addAll(normals);
        return definitions;
    }

    private static List<ContingencyDefinition> firstByClassification(
            AclfNetwork net,
            List<ContingencyDefinition> candidates,
            Classification classification,
            int count)
            throws Exception {
        ContingencyPreScreenReport report = DclfContingencyPreScreenUtil.scan(net, candidates);
        Map<String, ContingencyDefinition> byName = new LinkedHashMap<>();
        for (ContingencyDefinition candidate : candidates) {
            byName.put(candidate.name, candidate);
        }
        List<ContingencyDefinition> selected = new ArrayList<>();
        for (ContingencyPreScreenResult result : report.results()) {
            if (result.classification() == classification) {
                selected.add(byName.get(result.contingencyId()));
                if (selected.size() == count) {
                    break;
                }
            }
        }
        return selected;
    }

    private static List<ContingencyDefinition> texas2kIslandCandidates(AclfNetwork net) {
        List<ContingencyDefinition> candidates = new ArrayList<>();
        List<AclfBus> buses = new ArrayList<>(net.getBusList());
        buses.sort(Comparator.comparing(AclfBus::getId));
        for (AclfBus bus : buses) {
            if (!bus.isActive() || bus.isRefBus()) {
                continue;
            }
            List<String> incident = activeIncidentBranchIds(bus);
            if (incident.size() < 2 || incident.size() > 4) {
                continue;
            }
            ContingencyAction[] actions = incident.stream()
                    .map(DclfContingencyPreScreenUtilTest::open)
                    .toArray(ContingencyAction[]::new);
            candidates.add(definition("TX2K_ISLAND_" + bus.getId(), actions));
        }
        return candidates;
    }

    private static List<String> activeIncidentBranchIds(AclfBus bus) {
        List<String> ids = new ArrayList<>();
        for (Object object : bus.getBranchList()) {
            AclfBranch branch = (AclfBranch) object;
            if (branch.isActive() && !branch.isGroundBranch()) {
                ids.add(branch.getId());
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private static List<ContingencyDefinition> texas2kParallelOutageCandidates(AclfNetwork net) {
        Map<String, List<AclfBranch>> byEndpoint = new LinkedHashMap<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (!branch.isActive() || branch.isGroundBranch()) {
                continue;
            }
            String from = branch.getFromBus().getId();
            String to = branch.getToBus().getId();
            String key = from.compareTo(to) <= 0 ? from + "|" + to : to + "|" + from;
            byEndpoint.computeIfAbsent(key, ignored -> new ArrayList<>()).add(branch);
        }
        List<ContingencyDefinition> candidates = new ArrayList<>();
        int index = 1;
        for (List<AclfBranch> branches : byEndpoint.values()) {
            if (branches.size() < 2) {
                continue;
            }
            branches.sort(Comparator.comparing(AclfBranch::getId));
            candidates.add(definition(
                    String.format(java.util.Locale.ROOT, "TX2K_SINGULAR_PARALLEL_%03d", index++),
                    open(branches.get(0).getId()),
                    open(branches.get(1).getId())));
        }
        return candidates;
    }

    private static List<ContingencyDefinition> texas2kDuplicateOutageCandidates(AclfNetwork net) {
        List<ContingencyDefinition> candidates = new ArrayList<>();
        int index = 1;
        for (AclfBranch branch : sortedActiveBranches(net)) {
            candidates.add(definition(
                    String.format(java.util.Locale.ROOT, "TX2K_SINGULAR_DUPLICATE_%03d", index++),
                    open(branch.getId()),
                    open(branch.getId())));
            if (candidates.size() >= 50) {
                break;
            }
        }
        return candidates;
    }

    private static List<ContingencyDefinition> texas2kNormalCandidates(AclfNetwork net) {
        List<ContingencyDefinition> candidates = new ArrayList<>();
        int index = 1;
        for (AclfBranch branch : sortedActiveBranches(net)) {
            candidates.add(definition(
                    String.format(java.util.Locale.ROOT, "TX2K_NORMAL_N1_%03d", index++),
                    open(branch.getId())));
            if (candidates.size() >= 120) {
                break;
            }
        }
        return candidates;
    }

    private static List<AclfBranch> sortedActiveBranches(AclfNetwork net) {
        List<AclfBranch> branches = new ArrayList<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (branch.isActive() && !branch.isGroundBranch()) {
                branches.add(branch);
            }
        }
        branches.sort(Comparator.comparing(AclfBranch::getId));
        return branches;
    }

    private static ContingencyDefinition definition(String name, ContingencyAction... actions) {
        ContingencyDefinition definition = new ContingencyDefinition(name);
        for (ContingencyAction action : actions) {
            definition.addAction(action);
        }
        return definition;
    }

    private static ContingencyAction open(String branchId) {
        return new ContingencyAction(
                ContingencyObjectType.BRANCH,
                ContingencyActionType.OPEN,
                branchId);
    }

    private static ContingencyPreScreenResult byId(
            ContingencyPreScreenReport report,
            String contingencyId) {
        return report.results().stream()
                .filter(result -> contingencyId.equals(result.contingencyId()))
                .findFirst()
                .orElseThrow();
    }
}
