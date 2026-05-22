package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.interpss.plugin.contingency.ParallelDclfContingencyAnalyzer;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
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
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

import org.apache.commons.math3.complex.Complex;

@Tag("large")
public class DclfTransferPanelLargeCaseTest extends CorePluginTestSetup {
    private static final double MW_TOLERANCE = 1.0e-7;

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
    public void texas2kJsonRandomMultiOutagesMatchInterpssMultiOpenAnalysis() throws Exception {
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

        ConcurrentLinkedQueue<DclfMultiOutageCAResultRec> woodburyResults =
                DclfMultiOutageContingencyAnalyzer.performContingencyAnalysis(
                        net, multiOutages, monitors, 0.0, false, 4);
        ConcurrentLinkedQueue<DclfMultiOutageCAResultRec> interpssResults =
                interpssMultiOpenResults(net, multiOutages, monitors, 0.0, 1.0);

        Map<String, DclfMultiOutageCAResultRec> woodburyByKey = toMultiResultMap(woodburyResults);
        Map<String, DclfMultiOutageCAResultRec> interpssByKey = toMultiResultMap(interpssResults);

        assertEquals(interpssByKey.keySet(), woodburyByKey.keySet());
        for (Map.Entry<String, DclfMultiOutageCAResultRec> entry : interpssByKey.entrySet()) {
            DclfMultiOutageCAResultRec expected = entry.getValue();
            DclfMultiOutageCAResultRec actual = woodburyByKey.get(entry.getKey());
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
        DclfTransferPanelCache cache = DclfTransferPanelBuilder.build(
                spec,
                PanelBuildOptions.builder().monitorChunkSize(256).build());
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                new CachedDclfContingencyAnalyzer(cache).analyzeCurrentProfile(8);
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
        DclfTransferPanelCache cache = DclfTransferPanelBuilder.build(
                spec,
                PanelBuildOptions.builder().monitorChunkSize(256).build());
        long setupElapsedMs = (System.nanoTime() - setupStartNs) / 1_000_000L;

        CachedDclfContingencyAnalyzer cachedAnalyzer = new CachedDclfContingencyAnalyzer(cache);
        long cachedScanNs = 0L;
        long parallelNs = 0L;
        int cachedRecordTotal = 0;
        int parallelRecordTotal = 0;

        for (int hour = 0; hour < hourlyLoadFactors.length; hour++) {
            applyBusLoads(baseLoads, hourlyLoadFactors[hour]);

            long cachedStartNs = System.nanoTime();
            ConcurrentLinkedQueue<BranchCAResultRec> cachedResults = cachedAnalyzer.analyzeCurrentProfile(8);
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

        DclfTransferPanelCache cache = DclfTransferPanelBuilder.build(
                spec,
                PanelBuildOptions.builder().monitorChunkSize(monitorChunkSize).build());
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                new CachedDclfContingencyAnalyzer(cache).analyzeCurrentProfile(parallelism);
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
            assertEquals(expected.preFlowMW, actual.preFlowMW, MW_TOLERANCE, context);
            assertEquals(expected.shiftedFlowMW, actual.shiftedFlowMW, MW_TOLERANCE, context);
            assertEquals(expected.getPostFlowMW(), actual.getPostFlowMW(), MW_TOLERANCE, context);
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

    private static ConcurrentLinkedQueue<DclfMultiOutageCAResultRec> interpssMultiOpenResults(
            AclfNetwork net,
            List<DclfMultiOutage> multiOutages,
            Set<String> monitorIds,
            double overloadThreshold,
            double shiftThresholdMw)
            throws InterpssException {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.calculateDclf();

        List<AclfBranch> monitors = monitorIds.stream()
                .map(net::getBranch)
                .filter(branch -> branch != null && branch.isActive())
                .collect(Collectors.toList());
        ConcurrentLinkedQueue<DclfMultiOutageCAResultRec> results = new ConcurrentLinkedQueue<>();
        double baseMva = net.getBaseMva();

        for (DclfMultiOutage multiOutage : multiOutages) {
            DclfOutageBranch[] outageBranches =
                    multiOutage.getOutageEquips().toArray(new DclfOutageBranch[0]);
            refreshOutagePreFlows(dclfAlgo, outageBranches);
            dclfAlgo.multiOpenOutageAnalysis(outageBranches);

            for (AclfBranch monitor : monitors) {
                DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(monitor.getId());
                double shiftedFlowMw = dclfBranch.getShiftedFlow() * baseMva;
                if (Math.abs(shiftedFlowMw) <= shiftThresholdMw) {
                    continue;
                }

                DclfMultiOutageCAResultRec result =
                        new DclfMultiOutageCAResultRec(
                                multiOutage,
                                monitor,
                                dclfBranch.getDclfFlow() * baseMva,
                                shiftedFlowMw);
                if (result.calLoadingPercent() >= overloadThreshold) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    private static void refreshOutagePreFlows(
            ContingencyAnalysisAlgorithm dclfAlgo,
            DclfOutageBranch[] outageBranches) {
        for (DclfOutageBranch outageBranch : outageBranches) {
            DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(outageBranch.getBranch().getId());
            outageBranch.setDclfFlow(dclfBranch != null ? dclfBranch.getDclfFlow() : 0.0);
        }
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

    private static Map<String, DclfMultiOutageCAResultRec> toMultiResultMap(
            ConcurrentLinkedQueue<DclfMultiOutageCAResultRec> results) {
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
