package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.texas2K.Texas2K_TestCaseInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.DclfIslandingTreatment;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.solver.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

public class DclfAnchoredCompensationTexas2kTest extends CorePluginTestSetup {
    private static final int CONTINGENCY_COUNT = 50;
    private static final double MW_TOLERANCE = 0.05;
    private static final double ACTIVE_P_TOLERANCE = 1.0e-8;

    @Test
    @Tag("large")
    public void anchoredOneBusCompensationMatchesFullReplayForTexas2kBusTypes() throws Exception {
        AclfNetwork selectionNet = Texas2K_TestCaseInfo.createTestCaseNetwork();
        assertTrue(createContingencyAnalysisAlgorithm(selectionNet).calculateDclf(), "Texas2k base DCLF");

        List<IslandCase> islandCases = selectIslandCases(selectionNet, CONTINGENCY_COUNT);
        assertEquals(CONTINGENCY_COUNT, islandCases.size(), "Unexpected Texas2k one-bus island case count");

        EnumMap<BusCategory, Integer> categoryCounts = categoryCounts(islandCases);
        for (BusCategory category : BusCategory.values()) {
            assertTrue(categoryCounts.getOrDefault(category, 0) > 0,
                    "Expected at least one Texas2k island case for " + category
                            + ", counts=" + categoryCounts);
        }

        Set<String> monitoredBranchIds = allDclfMonitorableBranchIds(selectionNet);
        assertTrue(!monitoredBranchIds.isEmpty(), "No Texas2k DCLF-monitorable branches found");

        AclfNetwork anchoredNet = Texas2K_TestCaseInfo.createTestCaseNetwork();
        List<DclfMultiOutage> anchoredContingencies = contingencies(anchoredNet, islandCases);
        ConcurrentLinkedQueue<BranchCAResultRec> anchoredResults =
                ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
                        anchoredNet,
                        anchoredContingencies,
                        monitoredBranchIds,
                        config(DclfIslandingTreatment.ANCHORED_COMPENSATE_ONE_BUS),
                        1);

        AclfNetwork replayNet = Texas2K_TestCaseInfo.createTestCaseNetwork();
        List<DclfMultiOutage> replayContingencies = contingencies(replayNet, islandCases);
        ConcurrentLinkedQueue<BranchCAResultRec> replayResults =
                ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
                        replayNet,
                        replayContingencies,
                        monitoredBranchIds,
                        config(DclfIslandingTreatment.FULL_DCLF_REPLAY),
                        1);

        Map<String, BranchCAResultRec> anchoredByKey = toResultMap(anchoredResults);
        Map<String, BranchCAResultRec> replayByKey = toResultMap(replayResults);
        int expectedResultCount = CONTINGENCY_COUNT * monitoredBranchIds.size();
        assertEquals(expectedResultCount, anchoredByKey.size(), "Anchored result count");
        assertEquals(expectedResultCount, replayByKey.size(), "Full replay result count");

        double maxDiffMw = 0.0;
        String maxDiffKey = "";
        double anchoredMwAtMax = 0.0;
        double replayMwAtMax = 0.0;
        for (Map.Entry<String, BranchCAResultRec> entry : replayByKey.entrySet()) {
            BranchCAResultRec anchored = anchoredByKey.get(entry.getKey());
            assertNotNull(anchored, "Missing anchored result for " + entry.getKey());
            double anchoredMw = anchored.getPostFlowMW();
            double replayMw = entry.getValue().getPostFlowMW();
            double diff = Math.abs(anchoredMw - replayMw);
            if (diff > maxDiffMw) {
                maxDiffMw = diff;
                maxDiffKey = entry.getKey();
                anchoredMwAtMax = anchoredMw;
                replayMwAtMax = replayMw;
            }
        }

        System.out.println("Texas2k anchored one-bus island regression: cases="
                + islandCases.size()
                + ", monitoredBranches=" + monitoredBranchIds.size()
                + ", categoryCounts=" + categoryCounts
                + ", maxDiffMw=" + maxDiffMw
                + ", maxDiffKey=" + maxDiffKey
                + ", anchoredMw=" + anchoredMwAtMax
                + ", fullReplayMw=" + replayMwAtMax);
        assertTrue(maxDiffMw < MW_TOLERANCE,
                "Texas2k anchored compensation should match full DCLF replay. maxDiffMw="
                        + maxDiffMw
                        + ", key=" + maxDiffKey
                        + ", anchoredMw=" + anchoredMwAtMax
                        + ", fullReplayMw=" + replayMwAtMax);
    }

    private static List<IslandCase> selectIslandCases(AclfNetwork net, int count) {
        EnumMap<BusCategory, List<IslandCase>> byCategory = new EnumMap<>(BusCategory.class);
        for (BusCategory category : BusCategory.values()) {
            byCategory.put(category, new ArrayList<>());
        }

        for (AclfBus bus : net.getBusList()) {
            if (bus == null || !bus.isActive() || net.getRefBusIdSet().contains(bus.getId())) {
                continue;
            }
            List<String> outageBranchIds = activeIncidentBranchIds(net, bus.getId());
            if (outageBranchIds.size() < 2) {
                continue;
            }
            if (!createsOnlyThisBusIsland(net, bus.getId(), outageBranchIds)) {
                continue;
            }
            BusCategory category = category(bus);
            byCategory.get(category).add(new IslandCase(
                    "OPEN:" + bus.getId() + ":oneBusIsland",
                    bus.getId(),
                    category,
                    List.copyOf(outageBranchIds)));
        }

        List<IslandCase> selected = new ArrayList<>(count);
        int baseQuota = count / BusCategory.values().length;
        int remainder = count % BusCategory.values().length;
        for (BusCategory category : BusCategory.values()) {
            List<IslandCase> cases = byCategory.get(category);
            int quota = baseQuota + (remainder-- > 0 ? 1 : 0);
            assertTrue(cases.size() >= Math.min(1, quota),
                    "No Texas2k one-bus island candidates for " + category);
            for (int i = 0; i < Math.min(quota, cases.size()); i++) {
                selected.add(cases.get(i));
            }
        }
        for (BusCategory category : BusCategory.values()) {
            List<IslandCase> cases = byCategory.get(category);
            for (int i = 0; selected.size() < count && i < cases.size(); i++) {
                IslandCase candidate = cases.get(i);
                if (!selected.contains(candidate)) {
                    selected.add(candidate);
                }
            }
        }

        assertTrue(selected.size() == count,
                "Not enough Texas2k one-bus island candidates. selected=" + selected.size()
                        + ", available=" + availableCounts(byCategory));
        return selected;
    }

    private static boolean createsOnlyThisBusIsland(
            AclfNetwork net,
            String islandBusId,
            List<String> outageBranchIds) {
        Set<String> outagedBranches = new LinkedHashSet<>(outageBranchIds);
        String refBusId = net.getRefBusId();
        if (refBusId == null
                || refBusId.equals(islandBusId)
                || net.getBus(refBusId) == null
                || !net.getBus(refBusId).isActive()) {
            return false;
        }

        Set<String> connected = new LinkedHashSet<>();
        ArrayDeque<AclfBus> queue = new ArrayDeque<>();
        queue.add(net.getBus(refBusId));
        while (!queue.isEmpty()) {
            AclfBus bus = queue.removeFirst();
            String busId = bus.getId();
            if (!connected.add(busId)) {
                continue;
            }
            for (Branch branch : bus.getBranchIterable()) {
                if (!branch.isActive()
                        || outagedBranches.contains(branch.getId())
                        || branch.getFromBus() == null
                        || branch.getToBus() == null
                        || !branch.getFromBus().isActive()
                        || !branch.getToBus().isActive()) {
                    continue;
                }
                Bus neighbor = branch.getOppositeBus(bus);
                if (neighbor instanceof AclfBus
                        && neighbor.isActive()
                        && !connected.contains(neighbor.getId())) {
                    queue.addLast((AclfBus) neighbor);
                }
            }
        }

        if (connected.contains(islandBusId)) {
            return false;
        }
        for (AclfBus bus : net.getBusList()) {
            if (bus != null
                    && bus.isActive()
                    && !bus.getId().equals(islandBusId)
                    && !connected.contains(bus.getId())) {
                return false;
            }
        }
        return true;
    }

    private static List<String> activeIncidentBranchIds(AclfNetwork net, String busId) {
        List<String> branchIds = new ArrayList<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (branch.isActive()
                    && branch.getFromBus() != null
                    && branch.getToBus() != null
                    && (branch.getFromBus().getId().equals(busId)
                            || branch.getToBus().getId().equals(busId))) {
                branchIds.add(branch.getId());
            }
        }
        return branchIds;
    }

    private static Set<String> allDclfMonitorableBranchIds(AclfNetwork net) throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "Base DCLF before selecting all monitored branches");
        Set<String> monitoredBranchIds = new LinkedHashSet<>();
        for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
            AclfBranch branch = dclfBranch.getBranch();
            if (branch != null && dclfBranch.isActive()) {
                monitoredBranchIds.add(branch.getId());
            }
        }
        return monitoredBranchIds;
    }

    private static List<DclfMultiOutage> contingencies(
            AclfNetwork net,
            List<IslandCase> islandCases)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        assertTrue(dclfAlgo.calculateDclf(), "Base DCLF before creating Texas2k island contingencies");
        List<DclfMultiOutage> contingencies = new ArrayList<>(islandCases.size());
        for (IslandCase islandCase : islandCases) {
            DclfMultiOutage contingency =
                    createMultiOutageContingency(
                            islandCase.id(),
                            ContingencyBranchOutageType.OPEN);
            for (String branchId : islandCase.outageBranchIds()) {
                DclfAlgoBranch branch = dclfAlgo.getDclfAlgoBranch(branchId);
                assertNotNull(branch, "Missing DCLF branch " + branchId);
                DclfOutageBranch outage =
                        createCaOutageBranch(branch, ContingencyBranchOutageType.OPEN);
                outage.setDclfFlow(branch.getDclfFlow());
                contingency.getOutageEquips().add(outage);
            }
            contingencies.add(contingency);
        }
        return contingencies;
    }

    private static DclfContingencyConfig config(DclfIslandingTreatment treatment) {
        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setOverloadThreshold(0.0);
        config.setDclfInclLoss(false);
        config.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
        config.setIslandingTreatment(treatment);
        return config;
    }

    private static Map<String, BranchCAResultRec> toResultMap(
            ConcurrentLinkedQueue<BranchCAResultRec> results) {
        Map<String, BranchCAResultRec> byKey = new LinkedHashMap<>();
        for (BranchCAResultRec result : results) {
            byKey.put(result.contingency.getId() + "|" + result.aclfBranch.getId(), result);
        }
        return byKey;
    }

    private static EnumMap<BusCategory, Integer> categoryCounts(List<IslandCase> cases) {
        EnumMap<BusCategory, Integer> counts = new EnumMap<>(BusCategory.class);
        for (IslandCase islandCase : cases) {
            counts.merge(islandCase.category(), 1, Integer::sum);
        }
        return counts;
    }

    private static EnumMap<BusCategory, Integer> availableCounts(
            EnumMap<BusCategory, List<IslandCase>> byCategory) {
        EnumMap<BusCategory, Integer> counts = new EnumMap<>(BusCategory.class);
        for (Map.Entry<BusCategory, List<IslandCase>> entry : byCategory.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    private static BusCategory category(AclfBus bus) {
        boolean hasGen = bus.isGen() && Math.abs(bus.getGenP()) > ACTIVE_P_TOLERANCE;
        boolean hasLoad = bus.isLoad() && Math.abs(bus.getLoadP()) > ACTIVE_P_TOLERANCE;
        if (hasGen && hasLoad) {
            return BusCategory.GEN_AND_LOAD;
        }
        if (hasGen) {
            return BusCategory.GEN_ONLY;
        }
        if (hasLoad) {
            return BusCategory.LOAD_ONLY;
        }
        return BusCategory.NO_GEN_NO_LOAD;
    }

    private enum BusCategory {
        GEN_ONLY,
        LOAD_ONLY,
        GEN_AND_LOAD,
        NO_GEN_NO_LOAD
    }

    private record IslandCase(
            String id,
            String busId,
            BusCategory category,
            List<String> outageBranchIds) {
    }
}
