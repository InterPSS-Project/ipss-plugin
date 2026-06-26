package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createCaMonitoringBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.interpss.CorePluginTestSetup;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfLoad;
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
import com.interpss.core.contingency.dclf.DclfMonitoringBranch;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.monitor.definition.MonitoredBranchRecord;

import org.apache.commons.math3.complex.Complex;

@Tag("large")
public class DclfTransferPanelLargeCaseTest extends CorePluginTestSetup {
    private static final double MW_TOLERANCE = 1.0e-7;
    private static final double LARGE_CASE_CA_MW_TOLERANCE = 1.0e-2;

    @BeforeEach
    public void requireLargeTestsEnabled() {
        assumeTrue(Boolean.getBoolean("interpss.largeDclfTests"),
                "Set -Dinterpss.largeDclfTests=true to run large DCLF transfer-panel regressions");
    }

    @Test
    public void texas2kChunkedPanelMatchesParallelAnalyzer() throws Exception {
        AclfNetwork net = importPsse(
                "testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW",
                IpssAdapter.PsseVersion.PSSE_36);
        assertChunkedPanelMatchesParallelAnalyzer(net, 60, 120, 24, 4);
    }

    @Test
    public void texas2kJsonRandomMultiOutagesCompareSparseAndWoodburyCa() throws Exception {
        AclfNetwork net = importPsse(
                "testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW",
                IpssAdapter.PsseVersion.PSSE_36);
        setDefaultRatings(net);

        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<BranchContingencyRecord> contingencyRecords =
                ContingencyFileUtil.importContingenciesFromJson(
                        new File("testData/psse/v36/Texas2k/2k_contingencies_115kVAbove.json"));
        List<MonitoredBranchRecord> monitorRecords =
                ContingencyFileUtil.importMonitoredBranchRecordsFromJson(
                        new File("testData/psse/v36/Texas2k/2k_monitored_branches.json"));
        List<DclfBranchOutage> singleOutages =
                dclfContingenciesFromJsonRecords(net, sourceAlgo, contingencyRecords);
        Set<String> monitors = monitoredBranchIds(net, monitorRecords, 120);
        List<DclfMultiOutage> multiOutages =
                randomMultiOutageContingencies(singleOutages, new int[] {2, 3}, 20260522L);

        assertEquals(2, multiOutages.get(0).getOutageEquips().size());
        assertEquals(3, multiOutages.get(1).getOutageEquips().size());

        DclfContingencyConfig woodburyConfig = DclfContingencyConfig.createDefaultConfig();
        woodburyConfig.setOverloadThreshold(0.0);
        woodburyConfig.setDclfInclLoss(false);
        woodburyConfig.setSolutionMethod(DclfContingencySolutionMethod.WoodburyMatrixUpdate);

        ConcurrentLinkedQueue<BranchCAResultRec> woodburyResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        multiOutages,
                        monitors,
                        woodburyConfig.getOverloadThreshold(),
                        woodburyConfig.isDclfInclLoss(),
                        4,
                        woodburyConfig.getSolutionMethod());
        ConcurrentLinkedQueue<BranchCAResultRec> sparseResults =
                coreCaResults(
                        net,
                        multiOutages,
                        monitors,
                        DclfContingencySolutionMethod.SparseEqnSolve,
                        0.0,
                        BranchCAResultRec.ContingencyShiftThreshold);

        Map<String, BranchCAResultRec> woodburyByKey = toResultMap(woodburyResults);
        Map<String, BranchCAResultRec> sparseByKey = toResultMap(sparseResults);

        assertEquals(sparseByKey.keySet(), woodburyByKey.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : sparseByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = woodburyByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, MW_TOLERANCE);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, MW_TOLERANCE);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), MW_TOLERANCE);
        }
    }

    @Test
    public void activsg25kChunkedPanelMatchesParallelAnalyzer() throws Exception {
        AclfNetwork net = importPsse(
                "testData/psse/v33/ACTIVSg25k.RAW",
                IpssAdapter.PsseVersion.PSSE_33);
        assertChunkedPanelMatchesParallelAnalyzer(net, 30, 60, 15, 4);
    }

    @Test
    public void activsg25kParallelCaSparseVsWoodburyPerformance() throws Exception {
        AclfNetwork net = importPsse(
                "testData/psse/v33/ACTIVSg25k.RAW",
                IpssAdapter.PsseVersion.PSSE_33);
        setDefaultRatings(net);

        int contingencyCount = intProperty("interpss.performance25kContingencies", 30);
        int monitorCount = intProperty("interpss.performance25kMonitors", 60);
        int parallelism = performanceParallelism();

        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, contingencyCount);
        Set<String> monitors = firstActiveBranchIds(net, monitorCount);

        assertParallelCaSparseAndWoodbury(
                "ACTIVSg25k N-1 ca()",
                net,
                contingencies,
                monitors,
                parallelism);
    }

    @Test
    public void activsg25kFullWoodburyAnalyzerCompletesWithDefaultMonitorBatching() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.full25kDclfTests"),
                "Set -Dinterpss.full25kDclfTests=true to run full 25k Woodbury DCLF contingency analysis");

        AclfNetwork net = importPsse(
                "testData/psse/v33/ACTIVSg25k.RAW",
                IpssAdapter.PsseVersion.PSSE_33);
        setDefaultRatings(net);

        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, Integer.MAX_VALUE);
        Set<String> monitors = firstActiveBranchIds(net, Integer.MAX_VALUE);

        assertTrue(contingencies.size() > 10_000);
        assertTrue(monitors.size() > 10_000);
        assertTrue((long) contingencies.size() * (long) monitors.size() > 50_000_000L);

        long startNs = System.nanoTime();
        ConcurrentLinkedQueue<BranchCAResultRec> results =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        contingencies,
                        monitors,
                        performanceOverloadThreshold(),
                        false,
                        performanceParallelism(),
                        DclfContingencySolutionMethod.WoodburyMatrixUpdate);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        System.out.println("ACTIVSg25k full Woodbury analyzer: contingencies="
                + contingencies.size()
                + ", monitors=" + monitors.size()
                + ", records=" + results.size()
                + ", elapsedMs=" + elapsedMs);
        assertTrue(results.size() > 0);
    }

    @Test
    public void openEiJsonParallelCaSparseVsWoodburyPerformance() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.fullJsonDclfTests"),
                "Set -Dinterpss.fullJsonDclfTests=true to run OpenEI JSON DCLF performance comparison");

        AclfNetwork net = importPsse(
                "testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW",
                IpssAdapter.PsseVersion.PSSE_33);
        setDefaultRatings(net);

        int contingencyCount = intProperty("interpss.performanceOpenEiContingencies", 10);
        int monitorCount = intProperty("interpss.performanceOpenEiMonitors", 60);
        int parallelism = performanceParallelism();

        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<BranchContingencyRecord> contingencyRecords =
                ContingencyFileUtil.importContingenciesFromJson(
                        new File("testData/psse/v33/OpenEI_filtered_contingencies.json"));
        List<MonitoredBranchRecord> monitorRecords =
                ContingencyFileUtil.importMonitoredBranchRecordsFromJson(
                        new File("testData/psse/v33/OpenEI_monitored_branches.json"));
        List<DclfBranchOutage> contingencies =
                dclfContingenciesFromJsonRecords(net, sourceAlgo, contingencyRecords)
                        .stream()
                        .limit(contingencyCount)
                        .collect(Collectors.toList());
        Set<String> monitors = monitoredBranchIds(net, monitorRecords, monitorCount);

        assertParallelCaSparseAndWoodbury(
                "OpenEI JSON N-1 ca()",
                net,
                contingencies,
                monitors,
                parallelism);
    }

    @Test
    public void openEiJsonParallelAnalyzerSparseVsWoodburyPerformance() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.fullJsonDclfTests"),
                "Set -Dinterpss.fullJsonDclfTests=true to run OpenEI JSON DCLF performance comparison");

        AclfNetwork net = importPsse(
                "testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW",
                IpssAdapter.PsseVersion.PSSE_33);
        setDefaultRatings(net);

        int contingencyCount = intProperty("interpss.performanceOpenEiContingencies", 10);
        int monitorCount = intProperty("interpss.performanceOpenEiMonitors", 60);
        int parallelism = performanceParallelism();
        double overloadThreshold = performanceOverloadThreshold();

        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<BranchContingencyRecord> contingencyRecords =
                ContingencyFileUtil.importContingenciesFromJson(
                        new File("testData/psse/v33/OpenEI_filtered_contingencies.json"));
        List<MonitoredBranchRecord> monitorRecords =
                ContingencyFileUtil.importMonitoredBranchRecordsFromJson(
                        new File("testData/psse/v33/OpenEI_monitored_branches.json"));
        List<DclfBranchOutage> contingencies =
                dclfContingenciesFromJsonRecords(net, sourceAlgo, contingencyRecords)
                        .stream()
                        .limit(contingencyCount)
                        .collect(Collectors.toList());
        Set<String> monitors = monitoredBranchIds(net, monitorRecords, monitorCount);

        AnalyzerRun sparseRun =
                measureParallelAnalyzer(
                        "OpenEI JSON N-1 analyzer",
                        net,
                        contingencies,
                        monitors,
                        DclfContingencySolutionMethod.SparseEqnSolve,
                        overloadThreshold,
                        parallelism);
        AnalyzerRun woodburyRun =
                measureParallelAnalyzer(
                        "OpenEI JSON N-1 analyzer",
                        net,
                        contingencies,
                        monitors,
                        DclfContingencySolutionMethod.WoodburyMatrixUpdate,
                        overloadThreshold,
                        parallelism);

        assertSameResults(
                "OpenEI JSON N-1 analyzer",
                woodburyRun.results,
                sparseRun.results,
                LARGE_CASE_CA_MW_TOLERANCE);
        printAnalyzerPerformanceComparison(sparseRun, woodburyRun);
    }

    @Test
    public void openEiFullJsonChunkedPanelMatchesParallelAnalyzer() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.fullJsonDclfTests"),
                "Set -Dinterpss.fullJsonDclfTests=true to run full JSON DCLF transfer-panel regressions");

        AclfNetwork net = importPsse(
                "testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW",
                IpssAdapter.PsseVersion.PSSE_33);
        setDefaultRatings(net);

        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<BranchContingencyRecord> contingencyRecords =
                ContingencyFileUtil.importContingenciesFromJson(
                        new File("testData/psse/v33/OpenEI_filtered_contingencies.json"));
        List<MonitoredBranchRecord> monitorRecords =
                ContingencyFileUtil.importMonitoredBranchRecordsFromJson(
                        new File("testData/psse/v33/OpenEI_monitored_branches.json"));
        List<DclfBranchOutage> contingencies = dclfContingenciesFromJsonRecords(net, sourceAlgo, contingencyRecords);
        Set<String> monitors = monitoredBranchIds(net, monitorRecords);

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .overloadThreshold(100.0)
                .build();

        long cachedStartNs = System.nanoTime();
        OpenBranchOutageBatch cache = buildOpenBranchOutageBatch(spec, 256);
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                cache.analyzeCurrentProfile(spec.getOverloadThreshold(), spec.getShiftThresholdMw(), 8);
        long cachedElapsedMs = (System.nanoTime() - cachedStartNs) / 1_000_000L;

        long parallelStartNs = System.nanoTime();
        ConcurrentLinkedQueue<BranchCAResultRec> parallelResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net, contingencies, monitors, 100.0, false, 8);
        long parallelElapsedMs = (System.nanoTime() - parallelStartNs) / 1_000_000L;

        System.out.println("OpenEI full JSON DCLF cached: outages=" + cache.getOutageCount()
                + ", monitors=" + cache.getMonitorCount()
                + ", chunks=" + cache.getChunkCount()
                + ", estimatedPanelMB=" + cache.estimatePanelBytes() / (1024 * 1024)
                + ", records=" + cachedResults.size()
                + ", elapsedMs=" + cachedElapsedMs);
        System.out.println("OpenEI full JSON DCLF parallel: records=" + parallelResults.size()
                + ", elapsedMs=" + parallelElapsedMs);

        Map<String, BranchCAResultRec> cachedByKey = toResultMap(cachedResults);
        Map<String, BranchCAResultRec> parallelByKey = toResultMap(parallelResults);

        assertEquals(contingencies.size(), cache.getOutageCount());
        assertEquals(monitors.size(), cache.getMonitorCount());
        assertEquals(parallelByKey.keySet(), cachedByKey.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : parallelByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = cachedByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, MW_TOLERANCE);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, MW_TOLERANCE);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), MW_TOLERANCE);
        }
    }

    @Test
    public void openEiFullJsonTwentyFourHourProfilesReuseTransferPanel() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.fullJsonDclfTests"),
                "Set -Dinterpss.fullJsonDclfTests=true to run full JSON DCLF transfer-panel regressions");
        assumeTrue(Boolean.getBoolean("interpss.hourlyDclfTests"),
                "Set -Dinterpss.hourlyDclfTests=true to run 24-hour full JSON DCLF performance comparison");

        AclfNetwork net = importPsse(
                "testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW",
                IpssAdapter.PsseVersion.PSSE_33);
        setDefaultRatings(net);

        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<BranchContingencyRecord> contingencyRecords =
                ContingencyFileUtil.importContingenciesFromJson(
                        new File("testData/psse/v33/OpenEI_filtered_contingencies.json"));
        List<MonitoredBranchRecord> monitorRecords =
                ContingencyFileUtil.importMonitoredBranchRecordsFromJson(
                        new File("testData/psse/v33/OpenEI_monitored_branches.json"));
        List<DclfBranchOutage> contingencies = dclfContingenciesFromJsonRecords(net, sourceAlgo, contingencyRecords);
        Set<String> monitors = monitoredBranchIds(net, monitorRecords);
        List<LoadSnapshot> baseLoads = captureLoads(net);
        double[][] hourlyLoadFactors = hourlyLoadFactors(24, baseLoads.size(), 0.05, 20260521L);

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .overloadThreshold(100.0)
                .build();

        long setupStartNs = System.nanoTime();
        OpenBranchOutageBatch cache = buildOpenBranchOutageBatch(spec, 256);
        long setupElapsedMs = (System.nanoTime() - setupStartNs) / 1_000_000L;

        long cachedScanNs = 0L;
        long parallelNs = 0L;
        int cachedRecordTotal = 0;
        int parallelRecordTotal = 0;

        for (int hour = 0; hour < hourlyLoadFactors.length; hour++) {
            applyBusLoads(baseLoads, hourlyLoadFactors[hour]);

            long cachedStartNs = System.nanoTime();
            ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                    cache.analyzeCurrentProfile(spec.getOverloadThreshold(), spec.getShiftThresholdMw(), 8);
            cachedScanNs += System.nanoTime() - cachedStartNs;

            long parallelStartNs = System.nanoTime();
            ConcurrentLinkedQueue<BranchCAResultRec> parallelResults =
                    ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                            net, contingencies, monitors, 100.0, false, 8);
            parallelNs += System.nanoTime() - parallelStartNs;

            cachedRecordTotal += cachedResults.size();
            parallelRecordTotal += parallelResults.size();
            assertSameResults("hour " + hour, cachedResults, parallelResults);
        }

        restoreBusLoads(baseLoads);

        long cachedScanElapsedMs = cachedScanNs / 1_000_000L;
        long cachedTotalElapsedMs = setupElapsedMs + cachedScanElapsedMs;
        long parallelElapsedMs = parallelNs / 1_000_000L;

        System.out.println("OpenEI 24-hour full JSON DCLF cached setup: outages=" + cache.getOutageCount()
                + ", monitors=" + cache.getMonitorCount()
                + ", chunks=" + cache.getChunkCount()
                + ", estimatedPanelMB=" + cache.estimatePanelBytes() / (1024 * 1024)
                + ", variedLoads=" + baseLoads.size()
                + ", setupMs=" + setupElapsedMs);
        System.out.println("OpenEI 24-hour full JSON DCLF cached scans: hours=" + hourlyLoadFactors.length
                + ", records=" + cachedRecordTotal
                + ", scanMs=" + cachedScanElapsedMs
                + ", totalMs=" + cachedTotalElapsedMs);
        System.out.println("OpenEI 24-hour full JSON DCLF parallel baseline: hours=" + hourlyLoadFactors.length
                + ", records=" + parallelRecordTotal
                + ", elapsedMs=" + parallelElapsedMs);
    }

    private static void assertChunkedPanelMatchesParallelAnalyzer(
            AclfNetwork net,
            int outageCount,
            int monitorCount,
            int monitorChunkSize,
            int parallelism)
            throws Exception {
        setDefaultRatings(net);

        ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
        sourceAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = firstNonRefBranchOutages(net, sourceAlgo, outageCount);
        Set<String> monitors = firstActiveBranchIds(net, monitorCount);

        DclfContingencyStudySpec spec = DclfContingencyStudySpec.builder(net)
                .contingencies(contingencies)
                .monitoredBranchIds(monitors)
                .overloadThreshold(0.0)
                .build();

        OpenBranchOutageBatch cache = buildOpenBranchOutageBatch(spec, monitorChunkSize);
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                cache.analyzeCurrentProfile(spec.getOverloadThreshold(), spec.getShiftThresholdMw(), parallelism);
        ConcurrentLinkedQueue<BranchCAResultRec> parallelResults =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net, contingencies, monitors, 0.0, false, parallelism);

        assertEquals(outageCount, cache.getOutageCount());
        assertEquals(monitorCount, cache.getMonitorCount());

        Map<String, BranchCAResultRec> cachedByKey = toResultMap(cachedResults);
        Map<String, BranchCAResultRec> parallelByKey = toResultMap(parallelResults);

        assertEquals(parallelByKey.keySet(), cachedByKey.keySet());
        for (Map.Entry<String, BranchCAResultRec> entry : parallelByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = cachedByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, MW_TOLERANCE);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, MW_TOLERANCE);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), MW_TOLERANCE);
        }
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

    private static void assertParallelCaSparseAndWoodbury(
            String caseName,
            AclfNetwork net,
            List<DclfBranchOutage> contingencies,
            Set<String> monitors,
            int parallelism)
            throws InterpssException {
        PerformanceRun sparseRun =
                measureParallelCoreCa(
                        net,
                        contingencies,
                        monitors,
                        DclfContingencySolutionMethod.SparseEqnSolve,
                        performanceOverloadThreshold(),
                        BranchCAResultRec.ContingencyShiftThreshold,
                        parallelism);
        PerformanceRun woodburyRun =
                measureParallelCoreCa(
                        net,
                        contingencies,
                        monitors,
                        DclfContingencySolutionMethod.WoodburyMatrixUpdate,
                        performanceOverloadThreshold(),
                        BranchCAResultRec.ContingencyShiftThreshold,
                        parallelism);

        assertSameResults(caseName, woodburyRun.results, sparseRun.results, LARGE_CASE_CA_MW_TOLERANCE);
        printPerformanceComparison(caseName, sparseRun, woodburyRun);
    }

    private static PerformanceRun measureParallelCoreCa(
            AclfNetwork net,
            List<DclfBranchOutage> contingencies,
            Set<String> monitorIds,
            DclfContingencySolutionMethod solutionMethod,
            double overloadThreshold,
            double shiftThresholdMw,
            int parallelism)
            throws InterpssException {
        CoreCaRun run =
                parallelCoreCaResults(
                        net,
                        contingencies,
                        monitorIds,
                        solutionMethod,
                        overloadThreshold,
                        shiftThresholdMw,
                        parallelism);
        return new PerformanceRun(
                solutionMethod,
                contingencies.size(),
                monitorIds.size(),
                run.workerCount,
                run.setupNs,
                run.scanNs,
                run.results);
    }

    private static CoreCaRun parallelCoreCaResults(
            AclfNetwork net,
            List<DclfBranchOutage> contingencies,
            Set<String> monitorIds,
            DclfContingencySolutionMethod solutionMethod,
            double overloadThreshold,
            double shiftThresholdMw,
            int parallelism)
            throws InterpssException {
        ConcurrentLinkedQueue<BranchCAResultRec> results = new ConcurrentLinkedQueue<>();
        if (contingencies.isEmpty()) {
            return new CoreCaRun(results, 0L, 0L, 0);
        }

        double baseMva = net.getBaseMva();
        int workerCount = Math.max(1, Math.min(parallelism, contingencies.size()));

        long setupStartNs = System.nanoTime();
        ContingencyAnalysisAlgorithm[] workerAlgorithms =
                buildWorkerAlgorithms(net, solutionMethod, workerCount);
        long setupNs = System.nanoTime() - setupStartNs;

        long scanStartNs = System.nanoTime();
        try {
            ParallelDclfContingencyAnalyzer.executeParallel(
                    java.util.stream.IntStream.range(0, workerCount).boxed(),
                    workerIndex -> {
                        try {
                            ContingencyAnalysisAlgorithm dclfAlgo = workerAlgorithms[workerIndex];
                            for (int contingencyIndex = workerIndex;
                                    contingencyIndex < contingencies.size();
                                    contingencyIndex += workerCount) {
                                DclfBranchOutage workingContingency =
                                        createWorkingBranchOutage(
                                                dclfAlgo,
                                                contingencies.get(contingencyIndex),
                                                monitorIds);
                                dclfAlgo.ca(workingContingency);
                                collectMonitoringResults(
                                        dclfAlgo,
                                        workingContingency,
                                        baseMva,
                                        overloadThreshold,
                                        shiftThresholdMw,
                                        results);
                            }
                        } catch (InterpssException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    workerCount);
        } catch (RuntimeException e) {
            throwInterpssOrRuntime(e);
        }

        return new CoreCaRun(results, setupNs, System.nanoTime() - scanStartNs, workerCount);
    }

    private static ContingencyAnalysisAlgorithm[] buildWorkerAlgorithms(
            AclfNetwork net,
            DclfContingencySolutionMethod solutionMethod,
            int workerCount)
            throws InterpssException {
        ContingencyAnalysisAlgorithm[] workerAlgorithms =
                new ContingencyAnalysisAlgorithm[workerCount];

        try {
            ParallelDclfContingencyAnalyzer.executeParallel(
                    java.util.stream.IntStream.range(0, workerCount).boxed(),
                    workerIndex -> {
                        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
                        dclfAlgo.setSolutionMethod(solutionMethod);
                        dclfAlgo.calculateDclf();
                        workerAlgorithms[workerIndex] = dclfAlgo;
                    },
                    workerCount);
        } catch (RuntimeException e) {
            throwInterpssOrRuntime(e);
        }

        return workerAlgorithms;
    }

    private static void throwInterpssOrRuntime(RuntimeException e) throws InterpssException {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof InterpssException) {
            throw (InterpssException) cause;
        }
        throw e;
    }

    private static DclfBranchOutage createWorkingBranchOutage(
            ContingencyAnalysisAlgorithm dclfAlgo,
            DclfBranchOutage source,
            Set<String> monitorIds)
            throws InterpssException {
        if (source == null || source.getOutageEquip() == null || source.getOutageEquip().getBranch() == null) {
            throw new InterpssException("DCLF branch outage contingency cannot be null");
        }

        DclfBranchOutage working = createContingency(source.getId());
        DclfOutageBranch sourceOutage = source.getOutageEquip();
        DclfAlgoBranch outageDclfBranch =
                dclfAlgo.getDclfAlgoBranch(sourceOutage.getBranch().getId());
        if (outageDclfBranch == null) {
            throw new InterpssException("DCLF outage branch is not available: "
                    + sourceOutage.getBranch().getId());
        }

        DclfOutageBranch workingOutage =
                createCaOutageBranch(outageDclfBranch, sourceOutage.getOutageType());
        workingOutage.setDclfFlow(outageDclfBranch.getDclfFlow());
        working.setOutageEquip(workingOutage);

        for (String monitorId : monitorIds) {
            DclfAlgoBranch monitorDclfBranch = dclfAlgo.getDclfAlgoBranch(monitorId);
            if (monitorDclfBranch != null && monitorDclfBranch.isActive()) {
                working.addMonitoringBranch(createCaMonitoringBranch(monitorDclfBranch));
            }
        }
        return working;
    }

    private static void collectMonitoringResults(
            ContingencyAnalysisAlgorithm dclfAlgo,
            DclfBranchOutage contingency,
            double baseMva,
            double overloadThreshold,
            double shiftThresholdMw,
            ConcurrentLinkedQueue<BranchCAResultRec> results) {
        for (DclfMonitoringBranch monitoringBranch : contingency.getMonitoringBranches()) {
            AclfBranch monitor = monitoringBranch.getBranch();
            DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(monitor.getId());
            double preFlowMw = dclfBranch.getDclfFlow() * baseMva;
            double shiftedFlowMw = monitoringBranch.getShiftedFlow() * baseMva;
            if (Math.abs(shiftedFlowMw) <= shiftThresholdMw) {
                continue;
            }

            BranchCAResultRec result =
                    new BranchCAResultRec(contingency, monitor, preFlowMw, shiftedFlowMw);
            if (result.calLoadingPercent() >= overloadThreshold) {
                results.add(result);
            }
        }
    }

    private static void printPerformanceComparison(
            String caseName,
            PerformanceRun sparseRun,
            PerformanceRun woodburyRun) {
        System.out.println(caseName + " parallel ca() performance: contingencies="
                + sparseRun.contingencyCount
                + ", monitors=" + sparseRun.monitorCount
                + ", parallelism=" + sparseRun.parallelism);
        System.out.println("  " + sparseRun.summary());
        System.out.println("  " + woodburyRun.summary());
        System.out.println("  totalSpeedup(Woodbury/Sparse)="
                + formatDouble(sparseRun.totalNs() / (double) woodburyRun.totalNs()));
    }

    private static AnalyzerRun measureParallelAnalyzer(
            String caseName,
            AclfNetwork net,
            List<DclfBranchOutage> contingencies,
            Set<String> monitors,
            DclfContingencySolutionMethod solutionMethod,
            double overloadThreshold,
            int parallelism) {
        long startNs = System.nanoTime();
        ConcurrentLinkedQueue<BranchCAResultRec> results =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        contingencies,
                        monitors,
                        overloadThreshold,
                        false,
                        parallelism,
                        solutionMethod);
        return new AnalyzerRun(
                caseName,
                solutionMethod,
                contingencies.size(),
                monitors.size(),
                parallelism,
                System.nanoTime() - startNs,
                results);
    }

    private static void printAnalyzerPerformanceComparison(
            AnalyzerRun sparseRun,
            AnalyzerRun woodburyRun) {
        System.out.println(sparseRun.caseName + " fast analyzer performance: contingencies="
                + sparseRun.contingencyCount
                + ", monitors=" + sparseRun.monitorCount
                + ", parallelism=" + sparseRun.parallelism);
        System.out.println("  " + sparseRun.summary());
        System.out.println("  " + woodburyRun.summary());
        System.out.println("  totalSpeedup(Woodbury/Sparse)="
                + formatDouble(sparseRun.elapsedNs / (double) woodburyRun.elapsedNs));
    }

    private static AclfNetwork importPsse(String path, IpssAdapter.PsseVersion version) throws InterpssException {
        return IpssAdapter.importAclfNet(path)
                .setFormat(PSSE)
                .setPsseVersion(version)
                .load()
                .getImportedObj();
    }

    private static List<DclfBranchOutage> firstNonRefBranchOutages(
            AclfNetwork net,
            ContingencyAnalysisAlgorithm dclfAlgo,
            int count) {
        List<DclfBranchOutage> contingencies = new ArrayList<>();

        for (AclfBranch branch : net.getBranchList()) {
            if (!branch.isActive() || branch.isConnect2RefBus()) {
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

    private static void assertSameResults(
            String context,
            ConcurrentLinkedQueue<BranchCAResultRec> cachedResults,
            ConcurrentLinkedQueue<BranchCAResultRec> parallelResults) {
        assertSameResults(context, cachedResults, parallelResults, MW_TOLERANCE);
    }

    private static void assertSameResults(
            String context,
            ConcurrentLinkedQueue<BranchCAResultRec> cachedResults,
            ConcurrentLinkedQueue<BranchCAResultRec> parallelResults,
            double mwTolerance) {
        Map<String, BranchCAResultRec> cachedByKey = toResultMap(cachedResults);
        Map<String, BranchCAResultRec> parallelByKey = toResultMap(parallelResults);

        if (!parallelByKey.keySet().equals(cachedByKey.keySet())) {
            fail(context
                    + " result keys differ; parallelRecords=" + parallelByKey.size()
                    + ", cachedRecords=" + cachedByKey.size()
                    + ", missingFromCached="
                    + parallelByKey.keySet().stream().filter(key -> !cachedByKey.containsKey(key)).count()
                    + ", extraInCached="
                    + cachedByKey.keySet().stream().filter(key -> !parallelByKey.containsKey(key)).count());
        }
        for (Map.Entry<String, BranchCAResultRec> entry : parallelByKey.entrySet()) {
            BranchCAResultRec expected = entry.getValue();
            BranchCAResultRec actual = cachedByKey.get(entry.getKey());
            assertEquals(expected.preFlowMW, actual.preFlowMW, mwTolerance, context);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, mwTolerance, context);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), mwTolerance, context);
        }
    }

    private static Set<String> monitoredBranchIds(AclfNetwork net, List<MonitoredBranchRecord> monitorRecords) {
        return monitoredBranchIds(net, monitorRecords, 0);
    }

    private static Set<String> monitoredBranchIds(
            AclfNetwork net,
            List<MonitoredBranchRecord> monitorRecords,
            int maxCount) {
        Set<String> branchIds = new LinkedHashSet<>();

        for (MonitoredBranchRecord record : monitorRecords) {
            String branchId = resolveBranchId(net, record.fromBus, record.toBus, record.ckt);
            AclfBranch branch = branchId != null ? net.getBranch(branchId) : null;
            if (branch != null && branch.isActive() && !branch.isConnect2RefBus()) {
                branchIds.add(branchId);
                if (maxCount > 0 && branchIds.size() >= maxCount) {
                    break;
                }
            }
        }

        return branchIds;
    }

    private static List<DclfMultiOutage> randomMultiOutageContingencies(
            List<DclfBranchOutage> singleOutages,
            int[] groupSizes,
            long seed) {
        int requiredOutages = 0;
        for (int groupSize : groupSizes) {
            requiredOutages += groupSize;
        }
        if (singleOutages.size() < requiredOutages) {
            throw new IllegalArgumentException("Not enough single outages to create random multi-outage groups");
        }

        List<DclfBranchOutage> shuffled = new ArrayList<>(singleOutages);
        Collections.shuffle(shuffled, new Random(seed));

        List<DclfMultiOutage> multiOutages = new ArrayList<>();
        int cursor = 0;
        for (int groupIndex = 0; groupIndex < groupSizes.length; groupIndex++) {
            int groupSize = groupSizes[groupIndex];
            DclfMultiOutage multiOutage =
                    createMultiOutageContingency(
                            "json-random-multi-" + groupSize + "-" + groupIndex,
                            ContingencyBranchOutageType.OPEN);
            for (int outageIndex = 0; outageIndex < groupSize; outageIndex++) {
                multiOutage.getOutageEquips().add(shuffled.get(cursor++).getOutageEquip());
            }
            multiOutages.add(multiOutage);
        }

        return multiOutages;
    }

    private static ConcurrentLinkedQueue<BranchCAResultRec> coreCaResults(
            AclfNetwork net,
            List<DclfMultiOutage> multiOutages,
            Set<String> monitorIds,
            DclfContingencySolutionMethod solutionMethod,
            double overloadThreshold,
            double shiftThresholdMw)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.setSolutionMethod(solutionMethod);
        dclfAlgo.calculateDclf();

        List<AclfBranch> monitors = monitorIds.stream()
                .map(net::getBranch)
                .filter(branch -> branch != null && branch.isActive())
                .collect(Collectors.toList());
        ConcurrentLinkedQueue<BranchCAResultRec> results = new ConcurrentLinkedQueue<>();
        double baseMva = net.getBaseMva();

        for (DclfMultiOutage multiOutage : multiOutages) {
            DclfMultiOutage workingContingency = createWorkingMultiOutage(dclfAlgo, multiOutage, monitors);
            dclfAlgo.ca(workingContingency);

            for (DclfMonitoringBranch monitoringBranch : workingContingency.getMonitoringBranches()) {
                AclfBranch monitor = monitoringBranch.getBranch();
                double shiftedFlowMw = monitoringBranch.getShiftedFlow() * baseMva;
                if (Math.abs(shiftedFlowMw) <= shiftThresholdMw) {
                    continue;
                }

                BranchCAResultRec result =
                        new BranchCAResultRec(
                                workingContingency,
                                monitor,
                                dclfAlgo.getDclfAlgoBranch(monitor.getId()).getDclfFlow() * baseMva,
                                shiftedFlowMw);
                if (result.calLoadingPercent() >= overloadThreshold) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    private static DclfMultiOutage createWorkingMultiOutage(
            ContingencyAnalysisAlgorithm dclfAlgo,
            DclfMultiOutage source,
            List<AclfBranch> monitors) {
        DclfMultiOutage working =
                createMultiOutageContingency(source.getId(), source.getOutageType());

        for (DclfOutageBranch outageBranch : source.getOutageEquips()) {
            DclfOutageBranch workingOutage =
                    createCaOutageBranch(
                            dclfAlgo.getDclfAlgoBranch(outageBranch.getBranch().getId()),
                            source.getOutageType());
            workingOutage.setDclfFlow(
                    dclfAlgo.getDclfAlgoBranch(outageBranch.getBranch().getId()).getDclfFlow());
            working.getOutageEquips().add(workingOutage);
        }
        for (AclfBranch monitor : monitors) {
            working.addMonitoringBranch(createCaMonitoringBranch(dclfAlgo.getDclfAlgoBranch(monitor.getId())));
        }
        return working;
    }

    private static List<DclfBranchOutage> dclfContingenciesFromJsonRecords(
            AclfNetwork net,
            ContingencyAnalysisAlgorithm dclfAlgo,
            List<BranchContingencyRecord> contingencyRecords) {
        List<DclfBranchOutage> contingencies = new ArrayList<>();

        for (BranchContingencyRecord record : contingencyRecords) {
            String branchId = resolveBranchId(net, record.fromBus, record.toBus, record.ckt);
            if (branchId == null) {
                continue;
            }
            AclfBranch branch = net.getBranch(branchId);
            if (branch == null || !branch.isActive() || branch.isConnect2RefBus()) {
                continue;
            }
            DclfBranchOutage contingency = createContingency(record.name);
            DclfOutageBranch outage =
                    createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branchId), ContingencyBranchOutageType.OPEN);
            contingency.setOutageEquip(outage);
            contingencies.add(contingency);
        }

        return contingencies;
    }

    private static String resolveBranchId(AclfNetwork net, String fromBus, String toBus, String circuit) {
        String forward = fromBus + "->" + toBus + "(" + circuit + ")";
        if (net.getBranch(forward) != null) {
            return forward;
        }

        String reverse = toBus + "->" + fromBus + "(" + circuit + ")";
        if (net.getBranch(reverse) != null) {
            return reverse;
        }

        return null;
    }

    private static List<LoadSnapshot> captureLoads(AclfNetwork net) {
        List<LoadSnapshot> loads = new ArrayList<>();
        for (AclfBus bus : net.getBusList()) {
            if (!bus.isActive()) {
                continue;
            }
            if (!bus.getContributeLoadList().isEmpty()) {
                for (AclfLoad load : bus.getContributeLoadList()) {
                    Complex loadCP = load.getLoadCP();
                    Complex loadCI = load.getLoadCI();
                    Complex loadCZ = load.getLoadCZ();
                    if (isNonZero(loadCP) || isNonZero(loadCI) || isNonZero(loadCZ)) {
                        loads.add(new LoadSnapshot(load, loadCP, loadCI, loadCZ));
                    }
                }
            } else if (Math.abs(bus.getLoadP()) > 1.0e-12 || Math.abs(bus.getLoadQ()) > 1.0e-12) {
                loads.add(new LoadSnapshot(bus, bus.getLoadP(), bus.getLoadQ()));
            }
        }
        return loads;
    }

    private static boolean isNonZero(Complex value) {
        return value != null && (Math.abs(value.getReal()) > 1.0e-12 || Math.abs(value.getImaginary()) > 1.0e-12);
    }

    private static double[][] hourlyLoadFactors(int hourCount, int loadCount, double maxVariation, long seed) {
        Random random = new Random(seed);
        double[][] factors = new double[hourCount][loadCount];

        for (int hour = 0; hour < hourCount; hour++) {
            for (int loadIndex = 0; loadIndex < loadCount; loadIndex++) {
                factors[hour][loadIndex] = 1.0 + (random.nextDouble() * 2.0 - 1.0) * maxVariation;
            }
        }

        return factors;
    }

    private static void applyBusLoads(List<LoadSnapshot> baseLoads, double[] factors) {
        for (int i = 0; i < baseLoads.size(); i++) {
            baseLoads.get(i).apply(factors[i]);
        }
    }

    private static void restoreBusLoads(List<LoadSnapshot> baseLoads) {
        for (LoadSnapshot load : baseLoads) {
            load.restore();
        }
    }

    private static Map<String, BranchCAResultRec> toResultMap(ConcurrentLinkedQueue<BranchCAResultRec> results) {
        return results.stream().collect(Collectors.toMap(
                result -> result.contingency.getId() + "|" + result.aclfBranch.getId(),
                result -> result));
    }

    private static void setDefaultRatings(AclfNetwork net) {
        net.getBranchList().forEach(branch -> {
            if (branch.getRatingMva1() <= 0.0) {
                branch.setRatingMva1(100.0);
            }
            if (branch.getRatingMva2() <= 0.0) {
                branch.setRatingMva2(branch.getRatingMva1() > 0.0 ? branch.getRatingMva1() * 1.2 : 120.0);
            }
        });
    }

    private static int performanceParallelism() {
        return intProperty(
                "interpss.performanceParallelism",
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    private static int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Math.max(1, Integer.parseInt(value.trim()));
    }

    private static double performanceOverloadThreshold() {
        return doubleProperty("interpss.performanceOverloadThreshold", 0.0);
    }

    private static double doubleProperty(String name, double defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Double.parseDouble(value.trim());
    }

    private static String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class PerformanceRun {
        private final DclfContingencySolutionMethod solutionMethod;
        private final int contingencyCount;
        private final int monitorCount;
        private final int parallelism;
        private final long setupNs;
        private final long scanNs;
        private final ConcurrentLinkedQueue<BranchCAResultRec> results;

        private PerformanceRun(
                DclfContingencySolutionMethod solutionMethod,
                int contingencyCount,
                int monitorCount,
                int parallelism,
                long setupNs,
                long scanNs,
                ConcurrentLinkedQueue<BranchCAResultRec> results) {
            this.solutionMethod = solutionMethod;
            this.contingencyCount = contingencyCount;
            this.monitorCount = monitorCount;
            this.parallelism = parallelism;
            this.setupNs = setupNs;
            this.scanNs = scanNs;
            this.results = results;
        }

        private String summary() {
            double totalMs = totalNs() / 1_000_000.0;
            double totalSeconds = totalNs() / 1_000_000_000.0;
            double scanSeconds = scanNs / 1_000_000_000.0;
            double totalContingenciesPerSecond = contingencyCount / totalSeconds;
            double scanContingenciesPerSecond = contingencyCount / scanSeconds;
            double totalMonitorChecksPerSecond = contingencyCount * (double) monitorCount / totalSeconds;

            return solutionMethod
                    + ": records=" + results.size()
                    + ", setupMs=" + formatDouble(setupNs / 1_000_000.0)
                    + ", caScanMs=" + formatDouble(scanNs / 1_000_000.0)
                    + ", totalMs=" + formatDouble(totalMs)
                    + ", totalContingenciesPerSec=" + formatDouble(totalContingenciesPerSecond)
                    + ", caScanContingenciesPerSec=" + formatDouble(scanContingenciesPerSecond)
                    + ", totalMonitorChecksPerSec=" + formatDouble(totalMonitorChecksPerSecond);
        }

        private long totalNs() {
            return setupNs + scanNs;
        }
    }

    private static final class CoreCaRun {
        private final ConcurrentLinkedQueue<BranchCAResultRec> results;
        private final long setupNs;
        private final long scanNs;
        private final int workerCount;

        private CoreCaRun(
                ConcurrentLinkedQueue<BranchCAResultRec> results,
                long setupNs,
                long scanNs,
                int workerCount) {
            this.results = results;
            this.setupNs = setupNs;
            this.scanNs = scanNs;
            this.workerCount = workerCount;
        }
    }

    private static final class AnalyzerRun {
        private final String caseName;
        private final DclfContingencySolutionMethod solutionMethod;
        private final int contingencyCount;
        private final int monitorCount;
        private final int parallelism;
        private final long elapsedNs;
        private final ConcurrentLinkedQueue<BranchCAResultRec> results;

        private AnalyzerRun(
                String caseName,
                DclfContingencySolutionMethod solutionMethod,
                int contingencyCount,
                int monitorCount,
                int parallelism,
                long elapsedNs,
                ConcurrentLinkedQueue<BranchCAResultRec> results) {
            this.caseName = caseName;
            this.solutionMethod = solutionMethod;
            this.contingencyCount = contingencyCount;
            this.monitorCount = monitorCount;
            this.parallelism = parallelism;
            this.elapsedNs = elapsedNs;
            this.results = results;
        }

        private String summary() {
            double elapsedMs = elapsedNs / 1_000_000.0;
            double elapsedSeconds = elapsedNs / 1_000_000_000.0;
            double contingenciesPerSecond = contingencyCount / elapsedSeconds;
            double monitorChecksPerSecond = contingencyCount * (double) monitorCount / elapsedSeconds;

            return solutionMethod
                    + ": records=" + results.size()
                    + ", elapsedMs=" + formatDouble(elapsedMs)
                    + ", contingenciesPerSec=" + formatDouble(contingenciesPerSecond)
                    + ", monitorChecksPerSec=" + formatDouble(monitorChecksPerSecond);
        }
    }

    private static final class LoadSnapshot {
        private final AclfBus bus;
        private final AclfLoad load;
        private final double baseP;
        private final double baseQ;
        private final Complex baseLoadCP;
        private final Complex baseLoadCI;
        private final Complex baseLoadCZ;

        private LoadSnapshot(AclfBus bus, double baseP, double baseQ) {
            this.bus = bus;
            this.load = null;
            this.baseP = baseP;
            this.baseQ = baseQ;
            this.baseLoadCP = null;
            this.baseLoadCI = null;
            this.baseLoadCZ = null;
        }

        private LoadSnapshot(AclfLoad load, Complex baseLoadCP, Complex baseLoadCI, Complex baseLoadCZ) {
            this.bus = null;
            this.load = load;
            this.baseP = 0.0;
            this.baseQ = 0.0;
            this.baseLoadCP = baseLoadCP;
            this.baseLoadCI = baseLoadCI;
            this.baseLoadCZ = baseLoadCZ;
        }

        private void apply(double factor) {
            if (load != null) {
                load.setLoadCP(scale(baseLoadCP, factor));
                load.setLoadCI(scale(baseLoadCI, factor));
                load.setLoadCZ(scale(baseLoadCZ, factor));
            } else {
                bus.setLoadP(baseP * factor);
                bus.setLoadQ(baseQ * factor);
            }
        }

        private void restore() {
            apply(1.0);
        }

        private static Complex scale(Complex value, double factor) {
            return value != null ? value.multiply(factor) : null;
        }
    }
}
