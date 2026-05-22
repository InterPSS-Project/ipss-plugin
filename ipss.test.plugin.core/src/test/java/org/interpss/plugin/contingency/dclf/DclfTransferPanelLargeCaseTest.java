package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

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
                new CachedDclfContingencyAnalyzer(cache).analyzeCurrentProfile();
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
                new CachedDclfContingencyAnalyzer(cache).analyzeCurrentProfile();
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

    private static Set<String> monitoredBranchIds(AclfNetwork net, List<MonitoredBranchRecord> monitorRecords) {
        Set<String> branchIds = new LinkedHashSet<>();

        for (MonitoredBranchRecord record : monitorRecords) {
            String branchId = resolveBranchId(net, record.fromBus, record.toBus, record.ckt);
            AclfBranch branch = branchId != null ? net.getBranch(branchId) : null;
            if (branch != null && branch.isActive() && !branch.isConnect2RefBus()) {
                branchIds.add(branchId);
            }
        }

        return branchIds;
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
}
