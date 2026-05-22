package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
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
import org.interpss.plugin.contingency.ParallelDclfContingencyAnalyzer;
import org.interpss.plugin.optadj.IEEE14_SensHelper_Test;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
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

        DclfTransferPanelCache cache = DclfTransferPanelBuilder.build(spec);
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

        DclfTransferPanelCache cache = DclfTransferPanelBuilder.build(spec);
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                new CachedDclfContingencyAnalyzer(cache).analyzeCurrentProfile();
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

        DclfTransferPanelCache fullCache = DclfTransferPanelBuilder.build(spec);
        DclfTransferPanelCache chunkedCache = DclfTransferPanelBuilder.build(
                spec,
                PanelBuildOptions.builder().monitorChunkSize(2).build());

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
                toResultMap(new CachedDclfContingencyAnalyzer(fullCache).analyzeCurrentProfile());
        Map<String, BranchCAResultRec> chunkedResults =
                toResultMap(new CachedDclfContingencyAnalyzer(chunkedCache).analyzeCurrentProfile());

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

        DclfTransferPanelCache chunkedCache = DclfTransferPanelBuilder.build(
                spec,
                PanelBuildOptions.builder().monitorChunkSize(8).build());
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                new CachedDclfContingencyAnalyzer(chunkedCache).analyzeCurrentProfile();
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

        DclfTransferPanelCache chunkedCache = DclfTransferPanelBuilder.build(
                spec,
                PanelBuildOptions.builder().monitorChunkSize(16).build());
        ConcurrentLinkedQueue<BranchCAResultRec> cachedResults =
                new CachedDclfContingencyAnalyzer(chunkedCache).analyzeCurrentProfile();
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

        DclfWoodburyOutageSolver solver = new DclfWoodburyOutageSolver(dclfAlgo);
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

        DclfWoodburyOutageSolver.MultiOpenResult result =
                new DclfWoodburyOutageSolver(dclfAlgo).solveMultiOpen(outages, monitors);

        assertEquals(originalSortNumber, outages[0].getBranch().getSortNumber());
        assertEquals(outages.length, result.getOutageCount());
        assertEquals(monitors.length, result.getMonitorCount());

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

    private static Map<String, BranchCAResultRec> toResultMap(ConcurrentLinkedQueue<BranchCAResultRec> results) {
        return results.stream().collect(Collectors.toMap(
                result -> result.contingency.getId() + "|" + result.aclfBranch.getId(),
                result -> result));
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
