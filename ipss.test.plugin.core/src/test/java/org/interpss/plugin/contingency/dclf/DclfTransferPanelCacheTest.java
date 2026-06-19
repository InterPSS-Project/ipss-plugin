package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.contingency.DclfContingencyConfig;
import org.interpss.plugin.contingency.ParallelDclfContingencyAnalyzer;
import org.interpss.plugin.optadj.IEEE14_SensHelper_Test;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.solver.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.solver.DclfContingencyWoodburySolver;
import com.interpss.core.algo.dclf.solver.DclfContingencyWoodburySolver.OpenBranchOutageBatch;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class DclfTransferPanelCacheTest extends CorePluginTestSetup {
    @Test
    public void panelMatchesInterpssLineOutageFactors() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, 3);
        Set<String> monitors = new LinkedHashSet<>(Arrays.asList(
                "Bus2->Bus3(1)",
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)",
                "Bus4->Bus5(1)"));

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .build();

        OpenBranchOutageBatch cache = buildOpenBranchOutageBatch(spec);
        ContingencyAnalysisAlgorithm oracle = cache.getDclfAlgorithm();
        AclfBranch[] monitoredBranches = cache.getMonitoredBranches();
        DclfBranchOutage[] cachedContingencies = cache.getContingencies();

        assertEquals(monitors.size(), cache.getMonitorCount());
        assertEquals(contingencies.size(), cache.getOutageCount());
        assertTrue(cache.estimatePanelBytes() > 0);

        for (int outageIndex = 0; outageIndex < cachedContingencies.length; outageIndex++) {
            assertTrue(cache.isValidOutage(outageIndex));
            DclfOutageBranch outage = cachedContingencies[outageIndex].getOutageEquip();
            for (int monitorIndex = 0; monitorIndex < monitoredBranches.length; monitorIndex++) {
                double expected = oracle.lineOutageDFactor(outage, monitoredBranches[monitorIndex]);
                assertEquals(expected, cache.getLodf(monitorIndex, outageIndex), 1.0e-10);
            }
        }
    }

    @Test
    public void cachedAnalyzerMatchesParallelAnalyzerPostFlows() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, 5);
        Set<String> monitors = new LinkedHashSet<>(Arrays.asList(
                "Bus2->Bus3(1)",
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)",
                "Bus4->Bus5(1)",
                "Bus6->Bus11(1)"));

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .overloadThreshold(0.0)
                .build();

        OpenBranchOutageBatch cache = buildOpenBranchOutageBatch(spec);
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                cache.analyzeCurrentProfile(spec.getOverloadThreshold(), spec.getShiftThresholdMw(), 1);
        ConcurrentLinkedQueue<BranchCAResultRec> parallelResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net, contingencies, monitors, 0.0, false, 1);

        Map<String, BranchCAResultRec> cachedByKey = toResultMap(cachedResults);
        Map<String, BranchCAResultRec> parallelByKey = toResultMap(parallelResults);

        assertEquals(parallelByKey.keySet(), cachedByKey.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : parallelByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = cachedByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, 1.0e-8);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, 1.0e-8);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), 1.0e-8);
        }
    }

    @Test
    public void chunkedPanelMatchesFullPanelAndAnalyzerResults() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, 4);
        Set<String> monitors = new LinkedHashSet<>(Arrays.asList(
                "Bus2->Bus3(1)",
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)",
                "Bus4->Bus5(1)",
                "Bus6->Bus11(1)"));

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .overloadThreshold(0.0)
                .build();

        OpenBranchOutageBatch fullCache = buildOpenBranchOutageBatch(spec);
        OpenBranchOutageBatch chunkedCache = buildOpenBranchOutageBatch(spec, 2);

        assertEquals(1, fullCache.getChunkCount());
        assertEquals(3, chunkedCache.getChunkCount());
        assertEquals(fullCache.estimatePanelBytes(), chunkedCache.estimatePanelBytes());

        for (int monitorIndex = 0; monitorIndex < fullCache.getMonitorCount(); monitorIndex++) {
            for (int outageIndex = 0; outageIndex < fullCache.getOutageCount(); outageIndex++) {
                assertEquals(
                        fullCache.getLodf(monitorIndex, outageIndex),
                        chunkedCache.getLodf(monitorIndex, outageIndex),
                        1.0e-10);
            }
        }

        Map<String, BranchCAResultRec> fullResults =
                toResultMap(fullCache.analyzeCurrentProfile(
                        spec.getOverloadThreshold(), spec.getShiftThresholdMw(), 1));
        Map<String, BranchCAResultRec> chunkedResults =
                toResultMap(chunkedCache.analyzeCurrentProfile(
                        spec.getOverloadThreshold(), spec.getShiftThresholdMw(), 1));

        assertEquals(fullResults.keySet(), chunkedResults.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : fullResults.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = chunkedResults.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, 1.0e-8);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, 1.0e-8);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), 1.0e-8);
        }
    }

    @Test
    public void parallelAnalyzerWoodburyUsesCachedOpenBranchBatchByDefault() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, 5);
        Set<String> monitors = new LinkedHashSet<>(Arrays.asList(
                "Bus2->Bus3(1)",
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)",
                "Bus4->Bus5(1)",
                "Bus6->Bus11(1)"));

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .overloadThreshold(0.0)
                .build();
        OpenBranchOutageBatch cache = buildOpenBranchOutageBatch(spec);
        ConcurrentLinkedQueue<BranchCAResultRec> expectedResults =
                cache.analyzeCurrentProfile(spec.getOverloadThreshold(), spec.getShiftThresholdMw(), 1);

        ConcurrentLinkedQueue<BranchCAResultRec> woodburyResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        contingencies,
                        monitors,
                        spec.getOverloadThreshold(),
                        false,
                        2,
                        DclfContingencySolutionMethod.WoodburyMatrixUpdate);

        Map<String, BranchCAResultRec> expectedByKey = toResultMap(expectedResults);
        Map<String, BranchCAResultRec> woodburyByKey = toResultMap(woodburyResults);
        assertEquals(expectedByKey.keySet(), woodburyByKey.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : expectedByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = woodburyByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, 1.0e-5);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, 1.0e-5);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), 1.0e-5);
        }
    }

    @Test
    public void parallelAnalyzerWoodburyHonorsConfiguredMonitorBatchSize() throws InterpssException {
        String monitorBatchSizeProperty = "interpss.dclf.woodbury.monitorBatchSize";
        String originalMonitorBatchSize = System.getProperty(monitorBatchSizeProperty);
        try {
            System.setProperty(monitorBatchSizeProperty, "2");

            AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
            ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
            sourceAlgo.calculateDclf();

            List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, 5);
            Set<String> monitors = new LinkedHashSet<>(Arrays.asList(
                    "Bus2->Bus3(1)",
                    "Bus2->Bus4(1)",
                    "Bus3->Bus4(1)",
                    "Bus4->Bus5(1)",
                    "Bus6->Bus11(1)"));

            ConcurrentLinkedQueue<BranchCAResultRec> sparseResults =
                    ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                            net,
                            contingencies,
                            monitors,
                            0.0,
                            false,
                            2,
                            DclfContingencySolutionMethod.SparseEqnSolve);
            ConcurrentLinkedQueue<BranchCAResultRec> woodburyResults =
                    ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                            net,
                            contingencies,
                            monitors,
                            0.0,
                            false,
                            2,
                            DclfContingencySolutionMethod.WoodburyMatrixUpdate);

            Map<String, BranchCAResultRec> sparseByKey = toResultMap(sparseResults);
            Map<String, BranchCAResultRec> woodburyByKey = toResultMap(woodburyResults);
            assertEquals(sparseByKey.keySet(), woodburyByKey.keySet());
            for (Map.Entry<String, BranchCAResultRec> entry : sparseByKey.entrySet()) {
                BranchCAResultRec expected = entry.getValue();
                BranchCAResultRec actual = woodburyByKey.get(entry.getKey());
                assertEquals(expected.preFlowMW, actual.preFlowMW, 1.0e-5);
                assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, 1.0e-5);
                assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), 1.0e-5);
            }
        } finally {
            restoreProperty(monitorBatchSizeProperty, originalMonitorBatchSize);
        }
    }

    @Test
    public void parallelAnalyzerSupportsMultiOutageSolutionMethodSelection() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        DclfMultiOutage multiOutage =
                createMultiOutageContingency("configured-method-multi-open", ContingencyBranchOutageType.OPEN);
        for (DclfBranchOutage contingency : firstNonRefBranchOutages(net, sourceAlgo, 2)) {
            multiOutage.getOutageEquips().add(contingency.getOutageEquip());
        }
        Set<String> monitors = new LinkedHashSet<>(Arrays.asList(
                "Bus2->Bus3(1)",
                "Bus2->Bus4(1)",
                "Bus3->Bus4(1)",
                "Bus4->Bus5(1)"));

        DclfContingencyConfig sparseConfig = DclfContingencyConfig.createDefaultConfig();
        sparseConfig.setOverloadThreshold(0.0);
        sparseConfig.setDclfInclLoss(false);
        sparseConfig.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);

        DclfContingencyConfig woodburyConfig = DclfContingencyConfig.createDefaultConfig();
        woodburyConfig.setOverloadThreshold(0.0);
        woodburyConfig.setDclfInclLoss(false);
        woodburyConfig.setSolutionMethod(DclfContingencySolutionMethod.WoodburyMatrixUpdate);

        ConcurrentLinkedQueue<BranchCAResultRec> sparseResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        List.of(multiOutage),
                        monitors,
                        sparseConfig.getOverloadThreshold(),
                        sparseConfig.isDclfInclLoss(),
                        1,
                        sparseConfig.getSolutionMethod());
        ConcurrentLinkedQueue<BranchCAResultRec> woodburyResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        List.of(multiOutage),
                        monitors,
                        woodburyConfig.getOverloadThreshold(),
                        woodburyConfig.isDclfInclLoss(),
                        1,
                        woodburyConfig.getSolutionMethod());

        assertTrue(!woodburyResults.isEmpty());
        Map<String, BranchCAResultRec> sparseByKey = toResultMap(sparseResults);
        Map<String, BranchCAResultRec> woodburyByKey = toResultMap(woodburyResults);
        assertEquals(sparseByKey.keySet(), woodburyByKey.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : sparseByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = woodburyByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, 1.0e-8);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, 1.0e-8);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), 1.0e-8);
        }
    }

    @Test
    public void ieee118ChunkedPanelMatchesParallelAnalyzerAtLargerScale() throws Exception {
        AclfNetwork net = createIeeeCdfTestCase("testData/adpter/ieee_format/ieee118.ieee");
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, 25);
        Set<String> monitors = firstActiveBranchIds(net, 40);

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .overloadThreshold(0.0)
                .build();

        OpenBranchOutageBatch chunkedCache = buildOpenBranchOutageBatch(spec, 8);
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                chunkedCache.analyzeCurrentProfile(
                        spec.getOverloadThreshold(), spec.getShiftThresholdMw(), 1);
        ConcurrentLinkedQueue<BranchCAResultRec> parallelResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net, contingencies, monitors, 0.0, false, 4);

        assertEquals(25, chunkedCache.getOutageCount());
        assertEquals(40, chunkedCache.getMonitorCount());
        assertEquals(5, chunkedCache.getChunkCount());

        Map<String, BranchCAResultRec> cachedByKey = toResultMap(cachedResults);
        Map<String, BranchCAResultRec> parallelByKey = toResultMap(parallelResults);

        assertEquals(parallelByKey.keySet(), cachedByKey.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : parallelByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = cachedByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, 1.0e-8);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, 1.0e-8);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), 1.0e-8);
        }
    }

    @Test
    public void ieee300ChunkedPanelMatchesParallelAnalyzerAtLargerScale() throws Exception {
        AclfNetwork net = createIeeeCdfTestCase("testData/adpter/ieee_format/ieee300.ieee");
        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, 40);
        Set<String> monitors = firstActiveBranchIds(net, 80);

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .overloadThreshold(0.0)
                .build();

        OpenBranchOutageBatch chunkedCache = buildOpenBranchOutageBatch(spec, 16);
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                chunkedCache.analyzeCurrentProfile(
                        spec.getOverloadThreshold(), spec.getShiftThresholdMw(), 1);
        ConcurrentLinkedQueue<BranchCAResultRec> parallelResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net, contingencies, monitors, 0.0, false, 4);

        assertEquals(40, chunkedCache.getOutageCount());
        assertEquals(80, chunkedCache.getMonitorCount());
        assertEquals(5, chunkedCache.getChunkCount());

        Map<String, BranchCAResultRec> cachedByKey = toResultMap(cachedResults);
        Map<String, BranchCAResultRec> parallelByKey = toResultMap(parallelResults);

        assertEquals(parallelByKey.keySet(), cachedByKey.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : parallelByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = cachedByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, 1.0e-8);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, 1.0e-8);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), 1.0e-8);
        }
    }

    @Test
    public void woodburySingleOpenMatchesInterpssPostOutageFlow() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.calculateDclf();

        DclfBranchOutage contingency = firstNonRefBranchOutages(net, dclfAlgo, 1).get(0);
        DclfOutageBranch outage = contingency.getOutageEquip();
        AclfBranch monitor = net.getBranch("Bus2->Bus4(1)");

        DclfContingencyWoodburySolver solver = new DclfContingencyWoodburySolver(dclfAlgo);
        assertEquals(DclfContingencySolutionMethod.WoodburyMatrixUpdate, solver.getSolutionMethod());
        double expected = dclfAlgo.calPostOutageFlow(outage, dclfAlgo.getDclfAlgoBranch(monitor.getId()));
        double actual = solver.singleOpenPostFlow(outage, dclfAlgo.getDclfAlgoBranch(monitor.getId()));

        assertEquals(expected, actual, 1.0e-10);
    }

    @Test
    public void woodburyMultiOpenMatchesInterpssMultiOpenAnalysis() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, dclfAlgo, 2);
        DclfOutageBranch[] outages = contingencies.stream()
                .map(DclfBranchOutage::getOutageEquip)
                .toArray(DclfOutageBranch[]::new);
        AclfBranch[] monitors = new AclfBranch[] {
                outages[0].getBranch(),
                net.getBranch("Bus2->Bus4(1)"),
                net.getBranch("Bus3->Bus4(1)"),
                net.getBranch("Bus4->Bus5(1)")
        };
        int originalSortNumber = outages[0].getBranch().getSortNumber();

        DclfContingencyWoodburySolver solver = new DclfContingencyWoodburySolver(dclfAlgo);
        DclfContingencyWoodburySolver.MultiOpenResult result = solver.solveMultiOpen(outages, monitors);

        assertEquals(originalSortNumber, outages[0].getBranch().getSortNumber());
        assertEquals(outages.length, result.getOutageCount());
        assertEquals(monitors.length, result.getMonitorCount());
        for (int monitorIndex = 0; monitorIndex < monitors.length; monitorIndex++) {
            assertEquals(result.getPreFlowPu(monitorIndex) + result.getShiftedFlowPu(monitorIndex),
                    result.getPostFlowPu(monitorIndex), 1.0e-10);
        }

        dclfAlgo.multiOpenOutageAnalysis(outages);
        for (int monitorIndex = 0; monitorIndex < monitors.length; monitorIndex++) {
            double expectedShifted = dclfAlgo.getDclfAlgoBranch(monitors[monitorIndex].getId()).getShiftedFlow();
            assertEquals(expectedShifted, result.getShiftedFlowPu(monitorIndex), 1.0e-10);
            assertEquals(
                    dclfAlgo.getDclfAlgoBranch(monitors[monitorIndex].getId()).getDclfFlow() + expectedShifted,
                    result.getPostFlowPu(monitorIndex),
                    1.0e-10);
        }
    }

    @Test
    public void branchResultCombinedShiftingFactorSupportsMultiOutageLodfs() throws InterpssException {
        AclfNetwork net = IEEE14_SensHelper_Test.createSenTestCase();
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, dclfAlgo, 2);
        DclfOutageBranch[] outages = contingencies.stream()
                .map(DclfBranchOutage::getOutageEquip)
                .toArray(DclfOutageBranch[]::new);
        DclfMultiOutage multiOutage =
                createMultiOutageContingency("multi:combined-factor", ContingencyBranchOutageType.OPEN);
        multiOutage.getOutageEquips().addAll(Arrays.asList(outages));

        Set<String> outageBranchIds = Arrays.stream(outages)
                .map(outage -> outage.getBranch().getId())
                .collect(Collectors.toSet());
        AclfBranch monitor = net.getBranchList().stream()
                .filter(branch -> branch.isActive() && !outageBranchIds.contains(branch.getId()))
                .findFirst()
                .orElseThrow();
        DclfContingencyWoodburySolver.MultiOpenResult solved =
                new DclfContingencyWoodburySolver(dclfAlgo).solveMultiOpen(outages, new AclfBranch[] {monitor});

        BranchCAResultRec result =
                new BranchCAResultRec(
                        multiOutage,
                        monitor,
                        solved.getPreFlowMw(0),
                        solved.getShiftedFlowMw(0),
                        solved.getLodfs(0));

        String controlBusId = "Bus3";
        double expected = dclfAlgo.calGenShiftFactor(controlBusId, monitor);
        double[] lodfs = solved.getLodfs(0);
        for (int outageIndex = 0; outageIndex < outages.length; outageIndex++) {
            expected += dclfAlgo.calGenShiftFactor(controlBusId, outages[outageIndex].getBranch())
                    * lodfs[outageIndex];
        }

        assertEquals(expected, result.calCombinedShiftingFactor(controlBusId, dclfAlgo), 1.0e-10);
        assertEquals(2, result.getOutageEquips().size());
    }

    private static Map<String, BranchCAResultRec> toResultMap(ConcurrentLinkedQueue<BranchCAResultRec> results) {
        return results.stream().collect(Collectors.toMap(
                result -> result.contingency.getId() + "|" + result.aclfBranch.getId(),
                result -> result));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static OpenBranchOutageBatch buildOpenBranchOutageBatch(DclfContingencyStudySpec spec)
            throws InterpssException {
        return buildOpenBranchOutageBatch(spec, 0);
    }

    private static OpenBranchOutageBatch buildOpenBranchOutageBatch(
            DclfContingencyStudySpec spec,
            int monitorChunkSize)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo =
                createContingencyAnalysisAlgorithm(spec.getAclfNetwork(), CacheType.SenCached, true);
        AclfBranch[] monitors = resolveMonitoredBranches(dclfAlgo, spec.getMonitoredBranchIds());
        return new DclfContingencyWoodburySolver(dclfAlgo)
                .buildOpenBranchOutageBatch(
                        spec.getContingencies(),
                        monitors,
                        DclfMethod.STD,
                        monitorChunkSize);
    }

    private static AclfBranch[] resolveMonitoredBranches(
            ContingencyAnalysisAlgorithm dclfAlgo,
            Set<String> monitoredBranchIds) {
        List<AclfBranch> branches = new ArrayList<>();
        for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
            AclfBranch branch = dclfBranch.getBranch();
            if (branch != null
                    && branch.isActive()
                    && (monitoredBranchIds == null || monitoredBranchIds.contains(branch.getId()))) {
                branches.add(branch);
            }
        }
        return branches.toArray(new AclfBranch[0]);
    }

    private static List<DclfBranchOutage> firstNonRefBranchOutages(
            AclfNetwork net,
            ContingencyAnalysisAlgorithm dclfAlgo,
            int count) {
        List<DclfBranchOutage> contingencies = new ArrayList<>();

        for (AclfBranch branch : net.getBranchList()) {
            if (branch.isConnect2RefBus()) {
                continue;
            }
            DclfBranchOutage contingency = createContingency("contBranch:" + branch.getId());
            DclfOutageBranch outage =
                    createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), ContingencyBranchOutageType.OPEN);
            contingency.setOutageEquip(outage);
            contingencies.add(contingency);
            if (contingencies.size() == count) {
                break;
            }
        }

        return contingencies;
    }

    private static Set<String> firstActiveBranchIds(AclfNetwork net, int count) {
        Set<String> branchIds = new LinkedHashSet<>();

        for (AclfBranch branch : net.getBranchList()) {
            if (branch.isActive()) {
                branchIds.add(branch.getId());
                if (branchIds.size() == count) {
                    break;
                }
            }
        }

        return branchIds;
    }

    private static AclfNetwork createIeeeCdfTestCase(String path) throws InterpssException {
        AclfNetwork net = CorePluginFactory
                .getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
                .load(path)
                .getAclfNet();

        net.getBranchList().forEach(branch -> {
            branch.setRatingMva1(100.0);
            branch.setRatingMva2(120.0);
        });

        return net;
    }
}
