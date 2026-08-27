package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

import org.interpss.fadapter.psse.PSSEDirectParser;
public class DclfMixedActionComparisonTest extends CorePluginTestSetup {
    private static final double MW_TOLERANCE = 1.0e-4;
    private static final int CLOSE_TARGET_COUNT = 20;
    private static final int OPEN_CANDIDATE_COUNT = 100;
    private static final int MONITOR_COUNT = 40;
    private static final int PARALLELISM = 4;
    private static final int STRESS_MIXED_COUNT = Integer.getInteger("interpss.dclfMixedStress.mixedCount", 70);
    private static final int STRESS_SINGULAR_COUNT = Integer.getInteger("interpss.dclfMixedStress.singularCount", 30);
    private static final int STRESS_CLOSE_TARGET_COUNT = 40;
    private static final int STRESS_OPEN_CANDIDATE_COUNT = 240;
    private static final int STRESS_SOLVABLE_OPEN_PREFIX_COUNT =
            Integer.getInteger("interpss.dclfMixedStress.solvableOpenPrefixes", 1);
    private static final int[][] MIXED_ACTION_PATTERNS = {
            {1, 1},
            {2, 1},
            {1, 2},
            {2, 2}
    };
    private static final String IEEE300_FILE = "testData/adpter/ieee_format/ieee300.ieee";
    private static final Path TEXAS7K_DIR = resolveTexas7kDir();
    private static final Path ACTIVS25K_DIR = resolveActivs25kDir();

    @Test
    public void compareIeee300OpenCloseAndMixedContingencies() throws Exception {
        compareCase(CaseSpec.ieee300());
    }

    @Test
    @Tag("large")
    public void compareTexasAndActivsOpenCloseAndMixedContingencies() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.largeDclfMixedActionTests")
                        || Boolean.getBoolean("interpss.largeDclfTests"),
                "Set -Dinterpss.largeDclfMixedActionTests=true to run Texas2k/Texas7k/ACTIVSg25k mixed-action tests");

        for (CaseSpec caseSpec : List.of(
                CaseSpec.psse("Texas2k", Path.of("testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW")),
                CaseSpec.psse("Texas7k", TEXAS7K_DIR.resolve("Texas7k_20210804.RAW")),
                CaseSpec.psse("ACTIVSg25k", ACTIVS25K_DIR.resolve("ACTIVSg25k.RAW")))) {
            assumeTrue(caseSpec.isAvailable(), caseSpec.name() + " fixture not available: " + caseSpec.path());
            compareCase(caseSpec);
        }
    }

    @Test
    @Tag("large")
    public void compareTexas7kAndActivs25kMixedStressContingencies() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.largeDclfMixedActionTests")
                        || Boolean.getBoolean("interpss.largeDclfTests"),
                "Set -Dinterpss.largeDclfMixedActionTests=true to run Texas7k/ACTIVSg25k mixed stress tests");

        for (CaseSpec caseSpec : List.of(
                CaseSpec.psse("Texas7k", TEXAS7K_DIR.resolve("Texas7k_20210804.RAW")),
                CaseSpec.psse("ACTIVSg25k", ACTIVS25K_DIR.resolve("ACTIVSg25k.RAW")))) {
            assumeTrue(caseSpec.isAvailable(), caseSpec.name() + " fixture not available: " + caseSpec.path());
            compareStressCase(caseSpec);
        }
    }

    private static void compareCase(CaseSpec caseSpec) throws Exception {
        AclfNetwork net = caseSpec.load();
        assertTrue(createContingencyAnalysisAlgorithm(net).calculateDclf(), caseSpec.name() + " base DCLF");

        List<String> closeBranches = selectBaseCloseBranches(net, CLOSE_TARGET_COUNT);
        assertTrue(createContingencyAnalysisAlgorithm(net).calculateDclf(), caseSpec.name() + " close-target base DCLF");

        List<String> openBranches = firstActiveNonRefBranches(net, OPEN_CANDIDATE_COUNT);
        List<String> monitorIds = firstActiveNonRefBranches(net, MONITOR_COUNT);
        List<Scenario> scenarios = scenarios(net, openBranches, closeBranches);
        List<DclfMultiOutage> contingencies = contingencies(net, scenarios);
        DclfContingencySolutionMethod solutionMethod =
                DclfContingencySolutionMethod.valueOf(System.getProperty(
                        "interpss.dclfMixedComparison.solutionMethod",
                        DclfContingencySolutionMethod.WoodburyMatrixUpdate.name()));

        ConcurrentLinkedQueue<BranchCAResultRec> parallelResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        contingencies,
                        new LinkedHashSet<>(monitorIds),
                        0.0,
                        false,
                        PARALLELISM,
                        solutionMethod);

        Map<String, Map<String, BranchCAResultRec>> byContingency =
                resultsByContingencyAndMonitor(parallelResults);
        String report = comparisonReport(
                caseSpec.name(),
                net,
                closeBranches,
                monitorIds,
                scenarios,
                byContingency);

        Path reportPath = Path.of("target", caseSpec.reportFileName());
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        System.out.print(report);
    }

    private static void compareStressCase(CaseSpec caseSpec) throws Exception {
        AclfNetwork net = caseSpec.load();
        assertTrue(createContingencyAnalysisAlgorithm(net).calculateDclf(), caseSpec.name() + " base DCLF");

        List<String> closeBranches = selectBaseCloseBranches(net, STRESS_CLOSE_TARGET_COUNT);
        assertTrue(createContingencyAnalysisAlgorithm(net).calculateDclf(), caseSpec.name() + " close-target base DCLF");

        List<String> openBranches = firstActiveNonRefBranches(net, STRESS_OPEN_CANDIDATE_COUNT);
        List<String> monitorIds = firstActiveNonRefBranches(net, MONITOR_COUNT);
        List<Scenario> mixedScenarios =
                solvableMixedScenarios(
                        net,
                        openBranches,
                        closeBranches,
                        STRESS_MIXED_COUNT,
                        STRESS_SOLVABLE_OPEN_PREFIX_COUNT);
        List<Scenario> singularScenarios =
                singularMixedScenarios(net, openBranches, closeBranches, STRESS_SINGULAR_COUNT);
        if ("ACTIVSg25k".equals(caseSpec.name())) {
            assertRemoteSingularScenariosUseTopologyCheck(net, monitorIds, singularScenarios);
        }

        List<DclfMultiOutage> contingencies = contingencies(net, mixedScenarios);
        ConcurrentLinkedQueue<BranchCAResultRec> parallelResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        contingencies,
                        new LinkedHashSet<>(monitorIds),
                        0.0,
                        false,
                        PARALLELISM,
                        DclfContingencySolutionMethod.WoodburyMatrixUpdate);

        Map<String, Map<String, BranchCAResultRec>> byContingency =
                resultsByContingencyAndMonitor(parallelResults);
        String report = stressComparisonReport(
                caseSpec.name(),
                net,
                closeBranches,
                monitorIds,
                mixedScenarios,
                singularScenarios,
                byContingency);

        Path reportPath = Path.of("target", caseSpec.stressReportFileName());
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        System.out.print(report);
    }

    private static void assertRemoteSingularScenariosUseTopologyCheck(
            AclfNetwork net,
            List<String> monitorIds,
            List<Scenario> singularScenarios)
            throws Exception {
        List<Scenario> remoteSingularScenarios = singularScenarios.stream()
                .filter(scenario -> scenario.type().contains("REMOTE"))
                .toList();
        assertTrue(!remoteSingularScenarios.isEmpty(), "Expected ACTIVSg25k remote singular scenarios");
        for (Scenario scenario : remoteSingularScenarios) {
            String topology = topologySummary(net, scenario);
            assertTrue(topology.contains(":1"),
                    () -> "Expected one-bus island for " + scenario.id() + ": " + topology);
        }

        List<DclfMultiOutage> contingencies = contingencies(net, remoteSingularScenarios);
        ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                net,
                contingencies,
                new LinkedHashSet<>(monitorIds),
                0.0,
                false,
                PARALLELISM,
                DclfContingencySolutionMethod.WoodburyMatrixUpdate);
    }

    private static List<String> selectBaseCloseBranches(AclfNetwork net, int count)
            throws InterpssException {
        List<String> closeBranches = new ArrayList<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (!branch.isActive() || branch.isConnect2RefBus()) {
                continue;
            }
            branch.setStatus(false);
            if (createContingencyAnalysisAlgorithm(net).calculateDclf()) {
                closeBranches.add(branch.getId());
                if (closeBranches.size() == count) {
                    return closeBranches;
                }
            } else {
                branch.setStatus(true);
            }
        }
        throw new IllegalArgumentException("Could not find " + count + " close-target branches");
    }

    private static List<String> firstActiveNonRefBranches(AclfNetwork net, int count) {
        List<String> branchIds = new ArrayList<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (branch.isActive() && !branch.isConnect2RefBus()) {
                branchIds.add(branch.getId());
                if (branchIds.size() == count) {
                    return branchIds;
                }
            }
        }
        throw new IllegalArgumentException("Could not find " + count + " active non-reference branches");
    }

    private static List<Scenario> scenarios(
            AclfNetwork baseNet,
            List<String> openBranches,
            List<String> closeBranches)
            throws Exception {
        List<Scenario> scenarios = new ArrayList<>();
        addSolvableSameActionScenarios(baseNet, scenarios, "OPEN", openBranches, 2, 3, 4);
        addSolvableSameActionScenarios(baseNet, scenarios, "CLOSE", closeBranches, 2, 3, 4);
        addSolvableMixedScenarios(baseNet, scenarios, openBranches, closeBranches);
        if (scenarios.size() != 10) {
            throw new IllegalArgumentException("Expected 10 solvable scenarios, found " + scenarios.size());
        }
        return scenarios;
    }

    private static List<Scenario> mixedScenarios(
            AclfNetwork baseNet,
            List<String> openBranches,
            List<String> closeBranches,
            int count,
            boolean requireSolvable)
            throws Exception {
        List<Scenario> scenarios = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int serial = 1;
        int[][] patterns = {
                {1, 1},
                {2, 1},
                {1, 2},
                {2, 2},
                {3, 1},
                {1, 3}
        };
        for (int openStart = 0; openStart < openBranches.size() && scenarios.size() < count; openStart++) {
            for (int closeStart = 0; closeStart < closeBranches.size() && scenarios.size() < count; closeStart++) {
                for (int[] pattern : patterns) {
                    int openCount = pattern[0];
                    int closeCount = pattern[1];
                    if (openStart + openCount > openBranches.size()
                            || closeStart + closeCount > closeBranches.size()) {
                        continue;
                    }
                    List<Action> actions = new ArrayList<>(openCount + closeCount);
                    for (int i = 0; i < openCount; i++) {
                        actions.add(Action.open(openBranches.get(openStart + i)));
                    }
                    for (int i = 0; i < closeCount; i++) {
                        actions.add(Action.close(closeBranches.get(closeStart + i)));
                    }
                    String key = actionKey(actions);
                    if (!seen.add(key)) {
                        continue;
                    }
                    Scenario scenario = new Scenario(
                            (requireSolvable ? "MIX-" : "SING-") + String.format("%03d", serial),
                            requireSolvable ? "MIXED" : "MIXED-SINGULAR",
                            actions);
                    boolean solves = directDclfSolves(baseNet, scenario);
                    if (solves == requireSolvable) {
                        scenarios.add(scenario);
                        serial++;
                        if (scenarios.size() == count) {
                            return scenarios;
                        }
                    }
                }
            }
        }
        if (scenarios.size() != count) {
            throw new IllegalArgumentException("Expected " + count + " "
                    + (requireSolvable ? "solvable" : "singular")
                    + " mixed scenarios, found " + scenarios.size());
        }
        return scenarios;
    }

    private static List<Scenario> solvableMixedScenarios(
            AclfNetwork baseNet,
            List<String> openBranches,
            List<String> closeBranches,
            int count,
            int openPrefixCount)
            throws Exception {
        List<Scenario> scenarios = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (openPrefixCount < 1) {
            throw new IllegalArgumentException("openPrefixCount must be positive: " + openPrefixCount);
        }
        List<List<Action>> solvableOpenPrefixes = solvableOpenPrefixes(baseNet, openBranches, openPrefixCount);
        int serial = 1;
        for (int closeStart = 0; closeStart < closeBranches.size() && scenarios.size() < count; closeStart++) {
            for (int closeCount = 1; closeCount <= 3 && scenarios.size() < count; closeCount++) {
                if (closeStart + closeCount > closeBranches.size()) {
                    continue;
                }
                for (List<Action> openPrefix : solvableOpenPrefixes) {
                    if (closeStart + closeCount > closeBranches.size()
                            || openPrefix.size() + closeCount > 4) {
                        continue;
                    }
                    List<Action> actions = new ArrayList<>(openPrefix);
                    for (int i = 0; i < closeCount; i++) {
                        actions.add(Action.close(closeBranches.get(closeStart + i)));
                    }
                    String key = actionKey(actions);
                    if (!seen.add(key)) {
                        continue;
                    }
                    Scenario scenario = new Scenario(
                            "MIX-" + String.format("%03d", serial),
                            "MIXED",
                            actions);
                    if (directDclfSolves(baseNet, scenario)) {
                        scenarios.add(scenario);
                        serial++;
                    }
                    if (scenarios.size() == count) {
                        return scenarios;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Expected " + count
                + " solvable mixed scenarios, found " + scenarios.size());
    }

    private static List<List<Action>> solvableOpenPrefixes(
            AclfNetwork baseNet,
            List<String> openBranches,
            int targetCount)
            throws Exception {
        List<List<Action>> prefixes = new ArrayList<>();
        for (int openCount = 1; openCount <= 3 && prefixes.size() < targetCount; openCount++) {
            for (int start = 0; start + openCount <= openBranches.size() && prefixes.size() < targetCount; start++) {
                List<Action> actions = new ArrayList<>(openCount);
                for (int i = 0; i < openCount; i++) {
                    actions.add(Action.open(openBranches.get(start + i)));
                }
                Scenario scenario = new Scenario("OPEN-SOLVABLE-PREFIX", "OPEN", actions);
                if (directDclfSolves(baseNet, scenario)) {
                    prefixes.add(actions);
                }
            }
        }
        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("Could not find any solvable open outage prefix");
        }
        return prefixes;
    }

    private static List<Scenario> singularMixedScenarios(
            AclfNetwork baseNet,
            List<String> openBranches,
            List<String> closeBranches,
            int count)
            throws Exception {
        List<Scenario> scenarios = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int serialCount = count / 2;
        int remoteCount = count - serialCount;
        int serial = 1;
        serial = addSingularMixedScenarios(
                baseNet,
                scenarios,
                seen,
                serialSingularOpenPrefixes(baseNet, openBranches, serialCount),
                closeBranches,
                serialCount,
                serial,
                "MIXED-SINGULAR-SERIAL");
        addSingularMixedScenarios(
                baseNet,
                scenarios,
                seen,
                remoteSingularOpenPrefixes(baseNet, openBranches, remoteCount),
                closeBranches,
                count,
                serial,
                "MIXED-SINGULAR-REMOTE");
        if (scenarios.size() != count) {
            throw new IllegalArgumentException("Expected " + count
                    + " singular mixed scenarios, found " + scenarios.size());
        }
        return scenarios;
    }

    private static int addSingularMixedScenarios(
            AclfNetwork baseNet,
            List<Scenario> scenarios,
            Set<String> seen,
            List<List<Action>> openPrefixes,
            List<String> closeBranches,
            int count,
            int serial,
            String type)
            throws Exception {
        for (List<Action> openPrefix : openPrefixes) {
            for (int closeStart = 0; closeStart < closeBranches.size() && scenarios.size() < count; closeStart++) {
                for (int closeCount = 1; closeCount <= 3 && scenarios.size() < count; closeCount++) {
                    if (closeStart + closeCount > closeBranches.size()
                            || openPrefix.size() + closeCount > 4) {
                        continue;
                    }
                    List<Action> actions = new ArrayList<>(openPrefix);
                    for (int i = 0; i < closeCount; i++) {
                        actions.add(Action.close(closeBranches.get(closeStart + i)));
                    }
                    String key = actionKey(actions);
                    if (!seen.add(key)) {
                        continue;
                    }
                    Scenario scenario = new Scenario(
                            "SING-" + String.format("%03d", serial),
                            type,
                            actions);
                    if (!directDclfSolves(baseNet, scenario)) {
                        scenarios.add(scenario);
                        serial++;
                    }
                }
            }
            if (scenarios.size() == count) {
                return serial;
            }
        }
        return serial;
    }

    private static List<List<Action>> serialSingularOpenPrefixes(
            AclfNetwork baseNet,
            List<String> openBranches,
            int targetCount)
            throws Exception {
        List<List<Action>> prefixes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < openBranches.size() && prefixes.size() < targetCount; i++) {
            AclfBranch first = baseNet.getBranch(openBranches.get(i));
            if (!isCandidateOpenBranch(first)) {
                continue;
            }
            for (int j = i + 1; j < openBranches.size() && prefixes.size() < targetCount; j++) {
                AclfBranch second = baseNet.getBranch(openBranches.get(j));
                if (!isCandidateOpenBranch(second) || !sharesEndpoint(first, second)) {
                    continue;
                }
                List<Action> actions = List.of(Action.open(first.getId()), Action.open(second.getId()));
                if (!seen.add(actionKey(actions))) {
                    continue;
                }
                Scenario scenario = new Scenario("OPEN-SINGULAR-SERIAL-PREFIX", "OPEN", actions);
                if (!directDclfSolves(baseNet, scenario)) {
                    prefixes.add(actions);
                }
            }
        }
        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("Could not find any serial two-branch singular open outage prefix");
        }
        return prefixes;
    }

    private static List<List<Action>> remoteSingularOpenPrefixes(
            AclfNetwork baseNet,
            List<String> openBranches,
            int targetCount)
            throws Exception {
        List<List<Action>> prefixes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < openBranches.size() && prefixes.size() < targetCount; i++) {
            AclfBranch first = baseNet.getBranch(openBranches.get(i));
            if (!isCandidateOpenBranch(first)) {
                continue;
            }
            for (int j = openBranches.size() - 1; j > i && prefixes.size() < targetCount; j--) {
                AclfBranch second = baseNet.getBranch(openBranches.get(j));
                if (!isCandidateOpenBranch(second) || sharesEndpoint(first, second)) {
                    continue;
                }
                List<Action> actions = List.of(Action.open(first.getId()), Action.open(second.getId()));
                if (!seen.add(actionKey(actions))) {
                    continue;
                }
                Scenario scenario = new Scenario("OPEN-SINGULAR-REMOTE-PREFIX", "OPEN", actions);
                if (!directDclfSolves(baseNet, scenario)) {
                    prefixes.add(actions);
                }
            }
        }
        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("Could not find any remote two-branch singular open outage prefix");
        }
        return prefixes;
    }

    private static boolean isCandidateOpenBranch(AclfBranch branch) {
        return branch != null && branch.isActive() && !branch.isConnect2RefBus();
    }

    private static boolean sharesEndpoint(AclfBranch first, AclfBranch second) {
        String firstFrom = first.getFromBus().getId();
        String firstTo = first.getToBus().getId();
        return firstFrom.equals(second.getFromBus().getId())
                || firstFrom.equals(second.getToBus().getId())
                || firstTo.equals(second.getFromBus().getId())
                || firstTo.equals(second.getToBus().getId());
    }

    private static String actionKey(List<Action> actions) {
        StringBuilder builder = new StringBuilder();
        for (Action action : actions) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(action.summary());
        }
        return builder.toString();
    }

    private static void addSolvableSameActionScenarios(
            AclfNetwork baseNet,
            List<Scenario> scenarios,
            String type,
            List<String> branches,
            int... actionCounts)
            throws Exception {
        for (int actionCount : actionCounts) {
            boolean added = false;
            for (int start = 0; start + actionCount <= branches.size(); start++) {
                List<Action> actions = new ArrayList<>(actionCount);
                for (int i = 0; i < actionCount; i++) {
                    String branch = branches.get(start + i);
                    actions.add("OPEN".equals(type) ? Action.open(branch) : Action.close(branch));
                }
                if (addIfSolvable(baseNet, scenarios, type, actions)) {
                    added = true;
                    break;
                }
            }
            if (!added) {
                throw new IllegalArgumentException(
                        "Could not find solvable " + type + " scenario with " + actionCount + " actions");
            }
        }
    }

    private static void addSolvableMixedScenarios(
            AclfNetwork baseNet,
            List<Scenario> scenarios,
            List<String> openBranches,
            List<String> closeBranches)
            throws Exception {
        for (int[] pattern : MIXED_ACTION_PATTERNS) {
            int openCount = pattern[0];
            int closeCount = pattern[1];
            boolean added = false;
            for (int openStart = 0; openStart + openCount <= openBranches.size(); openStart++) {
                for (int closeStart = 0; closeStart + closeCount <= closeBranches.size(); closeStart++) {
                    List<Action> actions = new ArrayList<>(openCount + closeCount);
                    for (int i = 0; i < openCount; i++) {
                        actions.add(Action.open(openBranches.get(openStart + i)));
                    }
                    for (int i = 0; i < closeCount; i++) {
                        actions.add(Action.close(closeBranches.get(closeStart + i)));
                    }
                    if (addIfSolvable(baseNet, scenarios, "MIXED", actions)) {
                        added = true;
                        break;
                    }
                }
                if (added) {
                    break;
                }
            }
            if (!added) {
                throw new IllegalArgumentException("Could not find solvable MIXED scenario with "
                        + openCount + " open actions and " + closeCount + " close actions");
            }
        }
    }

    private static boolean addIfSolvable(
            AclfNetwork baseNet,
            List<Scenario> scenarios,
            String type,
            List<Action> actions)
            throws Exception {
        Scenario scenario = new Scenario(
                "CTG-" + String.format("%02d", scenarios.size() + 1),
                type,
                actions);
        if (!directDclfSolves(baseNet, scenario)
                || ("MIXED".equals(type)
                        && !"topology=refConnected".equals(topologySummary(baseNet, scenario)))) {
            return false;
        }
        scenarios.add(scenario);
        return true;
    }

    private static boolean directDclfSolves(AclfNetwork baseNet, Scenario scenario)
            throws Exception {
        Map<AclfBranch, Boolean> originalStatus = applyActions(baseNet, scenario);
        try {
            return createContingencyAnalysisAlgorithm(baseNet).calculateDclf();
        } finally {
            restoreStatus(originalStatus);
            createContingencyAnalysisAlgorithm(baseNet).calculateDclf();
        }
    }

    private static List<DclfMultiOutage> contingencies(AclfNetwork net, List<Scenario> scenarios)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf());

        List<DclfMultiOutage> contingencies = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            DclfMultiOutage contingency =
                    createMultiOutageContingency(scenario.id(), scenario.actions().get(0).type());
            for (Action action : scenario.actions()) {
                DclfOutageBranch outage =
                        createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(action.branchId()), action.type());
                outage.setDclfFlow(dclfAlgo.getDclfAlgoBranch(action.branchId()).getDclfFlow());
                contingency.getOutageEquips().add(outage);
            }
            contingencies.add(contingency);
        }
        return contingencies;
    }

    private static String comparisonReport(
            String caseName,
            AclfNetwork net,
            List<String> closeBranches,
            List<String> monitorIds,
            List<Scenario> scenarios,
            Map<String, Map<String, BranchCAResultRec>> byContingency)
            throws Exception {
        StringBuilder report = new StringBuilder();
        report.append(caseName).append(" ParallelDclfContingencyAnalyzer vs direct DCLF topology replay\n");
        report.append("baseCloseBranches=").append(closeBranches).append('\n');
        report.append("monitors=").append(monitorIds.size()).append('\n');
        report.append(String.format(
                "%-7s %-9s %-54s %10s %10s %10s %s%n",
                "id", "type", "actions", "maxAbsMW", "avgAbsMW", "matches", "worstMonitor"));

        List<Comparison> comparisons = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            Comparison comparison =
                    compareScenario(net, scenario, monitorIds, byContingency.get(scenario.id()));
            comparisons.add(comparison);
            report.append(String.format(
                    "%-7s %-9s %-54s %10.6f %10.6f %10s %s%n",
                    scenario.id(),
                    scenario.type(),
                    scenario.actionsSummary(),
                    comparison.maxAbsMw(),
                    comparison.avgAbsMw(),
                    comparison.matches(),
                    comparison.worstMonitor()));
        }

        for (int i = 0; i < comparisons.size(); i++) {
            Comparison comparison = comparisons.get(i);
            Scenario scenario = scenarios.get(i);
            assertTrue(comparison.maxAbsMw() <= MW_TOLERANCE,
                    () -> caseName + " " + scenario.id()
                            + " actions=" + scenario.actionsSummary()
                            + " maxAbsMW=" + comparison.maxAbsMw()
                            + " worstMonitor=" + comparison.worstMonitor());
        }
        return report.toString();
    }

    private static String stressComparisonReport(
            String caseName,
            AclfNetwork net,
            List<String> closeBranches,
            List<String> monitorIds,
            List<Scenario> mixedScenarios,
            List<Scenario> singularScenarios,
            Map<String, Map<String, BranchCAResultRec>> byContingency)
            throws Exception {
        StringBuilder report = new StringBuilder();
        report.append(caseName).append(" mixed contingency stress comparison\n");
        report.append("baseCloseBranches=").append(closeBranches).append('\n');
        report.append("monitors=").append(monitorIds.size()).append('\n');
        report.append("solvableMixedContingencies=").append(mixedScenarios.size()).append('\n');
        report.append("solvableOpenPrefixes=").append(distinctOpenPrefixes(mixedScenarios)).append('\n');
        report.append("singularMixedContingencies=").append(singularScenarios.size()).append('\n');
        List<EptdfPrecheck> singularEptdfPrechecks = new ArrayList<>();
        for (Scenario scenario : singularScenarios) {
            singularEptdfPrechecks.add(ePtdfPrecheck(net, scenario));
        }
        long eptdfSpecialTreatmentCount = singularEptdfPrechecks.stream()
                .filter(EptdfPrecheck::specialTreatmentNeeded)
                .count();
        report.append("singularEptdfSpecialTreatment=").append(eptdfSpecialTreatmentCount).append('\n');
        report.append(String.format(
                "%-8s %-15s %-54s %10s %10s %10s %s%n",
                "id", "type", "actions", "maxAbsMW", "avgAbsMW", "matches", "worstMonitor"));

        List<Comparison> comparisons = new ArrayList<>();
        for (Scenario scenario : mixedScenarios) {
            Comparison comparison =
                    compareScenario(net, scenario, monitorIds, byContingency.get(scenario.id()));
            comparisons.add(comparison);
            report.append(String.format(
                    "%-8s %-15s %-54s %10.6f %10.6f %10s %s%n",
                    scenario.id(),
                    scenario.type(),
                    scenario.actionsSummary(),
                    comparison.maxAbsMw(),
                    comparison.avgAbsMw(),
                    comparison.matches(),
                    comparison.worstMonitor()));
        }

        report.append("singularCases\n");
        for (int i = 0; i < singularScenarios.size(); i++) {
            Scenario scenario = singularScenarios.get(i);
            EptdfPrecheck precheck = singularEptdfPrechecks.get(i);
            report.append(String.format(
                    "%-8s %-22s eptdfSpecial=%-5s %-34s %-36s %s%n",
                    scenario.id(),
                    scenario.type(),
                    precheck.specialTreatmentNeeded(),
                    precheck.reason(),
                    topologySummary(net, scenario),
                    scenario.actionsSummary()));
        }

        for (int i = 0; i < comparisons.size(); i++) {
            Comparison comparison = comparisons.get(i);
            Scenario scenario = mixedScenarios.get(i);
            assertTrue(comparison.maxAbsMw() <= MW_TOLERANCE,
                    () -> caseName + " " + scenario.id() + " maxAbsMW=" + comparison.maxAbsMw());
        }
        return report.toString();
    }

    private static EptdfPrecheck ePtdfPrecheck(AclfNetwork net, Scenario scenario)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf());

        List<AclfBranch> touchedBranches = new ArrayList<>();
        Map<AclfBranch, Integer> originalSortNumbers = new LinkedHashMap<>();
        dclfAlgo.getOutageBranchList().clear();
        for (Action action : scenario.actions()) {
            DclfOutageBranch outage =
                    createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(action.branchId()), action.type());
            outage.setDclfFlow(dclfAlgo.getDclfAlgoBranch(action.branchId()).getDclfFlow());
            dclfAlgo.getOutageBranchList().add(outage);
            AclfBranch branch = outage.getBranch();
            touchedBranches.add(branch);
            originalSortNumbers.putIfAbsent(branch, branch.getSortNumber());
        }

        try {
            Object inv = dclfAlgo.calMultiOutageInvE_PTDF(scenario.id());
            int inverseSize = inv instanceof double[][] matrix ? matrix.length : -1;
            boolean compacted = false;
            for (AclfBranch branch : touchedBranches) {
                compacted |= branch.getSortNumber() < 0;
            }
            if (compacted || inverseSize != scenario.actions().size()) {
                return new EptdfPrecheck(
                        true,
                        "COMPACTED_E_PTDF(" + inverseSize + "/" + scenario.actions().size() + ")");
            }
            return new EptdfPrecheck(false, "FULL_RANK_E_PTDF");
        } catch (InterpssException e) {
            return new EptdfPrecheck(true, "SINGULAR_E_PTDF");
        } finally {
            for (Map.Entry<AclfBranch, Integer> entry : originalSortNumbers.entrySet()) {
                entry.getKey().setSortNumber(entry.getValue());
            }
            dclfAlgo.getOutageBranchList().clear();
        }
    }

    private static String topologySummary(AclfNetwork net, Scenario scenario) {
        Map<AclfBranch, Boolean> originalStatus = applyActions(net, scenario);
        try {
            String refBusId = net.getRefBusId();
            Map<String, List<String>> adjacency = activeAdjacency(net);
            List<String> islands = new ArrayList<>();
            Set<String> checked = new LinkedHashSet<>();
            for (Action action : scenario.actions()) {
                if (action.type() != ContingencyBranchOutageType.OPEN) {
                    continue;
                }
                AclfBranch branch = net.getBranch(action.branchId());
                addIslandIfFound(adjacency, branch.getFromBus().getId(), refBusId, checked, islands);
                addIslandIfFound(adjacency, branch.getToBus().getId(), refBusId, checked, islands);
            }
            return islands.isEmpty() ? "topology=refConnected" : "topology=island" + islands;
        } finally {
            restoreStatus(originalStatus);
        }
    }

    private static Map<String, List<String>> activeAdjacency(AclfNetwork net) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (!branch.isActive()) {
                continue;
            }
            String fromId = branch.getFromBus().getId();
            String toId = branch.getToBus().getId();
            adjacency.computeIfAbsent(fromId, ignored -> new ArrayList<>()).add(toId);
            adjacency.computeIfAbsent(toId, ignored -> new ArrayList<>()).add(fromId);
        }
        return adjacency;
    }

    private static void addIslandIfFound(
            Map<String, List<String>> adjacency,
            String seedBusId,
            String refBusId,
            Set<String> checked,
            List<String> islands) {
        if (!checked.add(seedBusId)) {
            return;
        }
        Set<String> component = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(seedBusId);
        boolean reachesRef = false;
        while (!queue.isEmpty()) {
            String busId = queue.removeFirst();
            if (!component.add(busId)) {
                continue;
            }
            checked.add(busId);
            if (busId.equals(refBusId)) {
                reachesRef = true;
            }
            for (String neighbor : adjacency.getOrDefault(busId, List.of())) {
                if (!component.contains(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        if (!reachesRef) {
            islands.add(seedBusId + ":" + component.size());
        }
    }

    private static int distinctOpenPrefixes(List<Scenario> scenarios) {
        Set<String> openPrefixes = new LinkedHashSet<>();
        for (Scenario scenario : scenarios) {
            StringBuilder builder = new StringBuilder();
            for (Action action : scenario.actions()) {
                if (action.type() != ContingencyBranchOutageType.OPEN) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('|');
                }
                builder.append(action.summary());
            }
            openPrefixes.add(builder.toString());
        }
        return openPrefixes.size();
    }

    private static Map<String, Map<String, BranchCAResultRec>> resultsByContingencyAndMonitor(
            ConcurrentLinkedQueue<BranchCAResultRec> results) {
        Map<String, Map<String, BranchCAResultRec>> byContingency = new LinkedHashMap<>();
        for (BranchCAResultRec result : results) {
            byContingency
                    .computeIfAbsent(result.contingency.getId(), ignored -> new LinkedHashMap<>())
                    .put(result.aclfBranch.getId(), result);
        }
        return byContingency;
    }

    private static Comparison compareScenario(
            AclfNetwork net,
            Scenario scenario,
            List<String> monitorIds,
            Map<String, BranchCAResultRec> parallelByMonitor)
            throws Exception {
        ContingencyAnalysisAlgorithm baseAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(baseAlgo.calculateDclf());
        Map<String, Double> baseFlowMw = branchFlowsMw(baseAlgo, monitorIds, net.getBaseMva());

        Map<AclfBranch, Boolean> originalStatus = applyActions(net, scenario);
        try {
            ContingencyAnalysisAlgorithm directAlgo = createContingencyAnalysisAlgorithm(net);
            assertTrue(directAlgo.calculateDclf());
            Map<String, Double> directFlowMw = branchFlowsMw(directAlgo, monitorIds, net.getBaseMva());

            double sumAbs = 0.0;
            double maxAbs = 0.0;
            String worstMonitor = "";
            for (String monitorId : monitorIds) {
                double expectedShiftMw = directFlowMw.get(monitorId) - baseFlowMw.get(monitorId);
                BranchCAResultRec result = parallelByMonitor == null ? null : parallelByMonitor.get(monitorId);
                double actualShiftMw = result == null ? 0.0 : result.shiftedFlowMW;
                double abs = Math.abs(actualShiftMw - expectedShiftMw);
                sumAbs += abs;
                if (abs > maxAbs) {
                    maxAbs = abs;
                    worstMonitor = String.format(
                            "%s expected=%.6f actual=%.6f",
                            monitorId,
                            expectedShiftMw,
                            actualShiftMw);
                }
            }
            return new Comparison(maxAbs, sumAbs / monitorIds.size(), maxAbs <= MW_TOLERANCE, worstMonitor);
        } finally {
            restoreStatus(originalStatus);
            assertTrue(createContingencyAnalysisAlgorithm(net).calculateDclf());
        }
    }

    private static Map<AclfBranch, Boolean> applyActions(AclfNetwork net, Scenario scenario) {
        Map<AclfBranch, Boolean> originalStatus = new LinkedHashMap<>();
        for (Action action : scenario.actions()) {
            AclfBranch branch = net.getBranch(action.branchId());
            if (branch == null) {
                throw new IllegalArgumentException("Branch not found: " + action.branchId());
            }
            originalStatus.putIfAbsent(branch, branch.isActive());
            branch.setStatus(action.type() == ContingencyBranchOutageType.CLOSE);
        }
        return originalStatus;
    }

    private static void restoreStatus(Map<AclfBranch, Boolean> originalStatus) {
        for (Map.Entry<AclfBranch, Boolean> entry : originalStatus.entrySet()) {
            entry.getKey().setStatus(entry.getValue());
        }
    }

    private static Map<String, Double> branchFlowsMw(
            ContingencyAnalysisAlgorithm dclfAlgo,
            List<String> branchIds,
            double baseMva) {
        Map<String, Double> flows = new LinkedHashMap<>();
        for (String branchId : branchIds) {
            flows.put(branchId, dclfAlgo.getBranchFlow(branchId) * baseMva);
        }
        return flows;
    }

    private static Path resolveTexas7kDir() {
        List<Path> candidates = List.of(
                Path.of("../ipss-desktop/examples/texas7k"),
                Path.of("../../ipss-desktop/examples/texas7k"),
                Path.of("/Users/ipssdev/github/ipss-desktop/examples/texas7k"));
        return candidates.stream()
                .filter(path -> Files.isRegularFile(path.resolve("Texas7k_20210804.RAW")))
                .findFirst()
                .orElse(candidates.get(0));
    }

    private static Path resolveActivs25kDir() {
        List<Path> candidates = List.of(
                Path.of("../ipss-desktop/examples/25k"),
                Path.of("../../ipss-desktop/examples/25k"),
                Path.of("/Users/ipssdev/github/ipss-desktop/examples/25k"),
                Path.of("testData/psse/v33"));
        return candidates.stream()
                .filter(path -> Files.isRegularFile(path.resolve("ACTIVSg25k.RAW")))
                .findFirst()
                .orElse(candidates.get(candidates.size() - 1));
    }

    private interface CaseLoader {
        AclfNetwork load() throws Exception;
    }

    private record CaseSpec(String name, Path path, CaseLoader loader) {
        static CaseSpec ieee300() {
            return new CaseSpec(
                    "IEEE300",
                    Path.of(IEEE300_FILE),
                    () -> CorePluginFactory
                            .getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
                            .load(IEEE300_FILE)
                            .getAclfNet());
        }

        static CaseSpec psse(String name, Path path) {
            return new CaseSpec(
                    name,
                    path,
                    () -> new PSSEDirectParser().parse(path.toString()));
        }

        AclfNetwork load() throws Exception {
            return loader.load();
        }

        boolean isAvailable() {
            return Files.isRegularFile(path);
        }

        String reportFileName() {
            return name.toLowerCase(java.util.Locale.ROOT) + "-open-close-mixed-comparison.txt";
        }

        String stressReportFileName() {
            return name.toLowerCase(java.util.Locale.ROOT) + "-mixed-stress-comparison.txt";
        }
    }

    private record Action(String branchId, ContingencyBranchOutageType type) {
        static Action open(String branchId) {
            return new Action(branchId, ContingencyBranchOutageType.OPEN);
        }

        static Action close(String branchId) {
            return new Action(branchId, ContingencyBranchOutageType.CLOSE);
        }

        String summary() {
            return type + ":" + branchId;
        }
    }

    private record Scenario(String id, String type, List<Action> actions) {
        String actionsSummary() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < actions.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(actions.get(i).summary());
            }
            return builder.toString();
        }
    }

    private record Comparison(double maxAbsMw, double avgAbsMw, boolean matches, String worstMonitor) {
    }

    private record EptdfPrecheck(boolean specialTreatmentNeeded, String reason) {
    }
}
