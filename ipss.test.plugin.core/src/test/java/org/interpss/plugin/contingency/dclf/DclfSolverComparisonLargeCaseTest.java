package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.interpss.plugin.contingency.util.DclfContingencyHelper;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.solver.DclfContingencySolutionMethod;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfMonitoringBranch;
import com.interpss.core.net.Branch;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;

@Tag("large")
public class DclfSolverComparisonLargeCaseTest extends CorePluginTestSetup {
    private static final double RESULT_TOLERANCE_MW = 1.0e-2;

    @Test
    public void compareCsjAndJavaKluForLargeDclfContingencyAnalysis() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.largeDclfTests"),
                "Set -Dinterpss.largeDclfTests=true to run large DCLF solver comparison");

        System.out.println("case,solver,repeat,warmup,contingencies,monitors,parallelism,"
                + "dclfSetupMs,analyzerMs,totalMs,records,parity");

        if (shouldRunCase("ACTIVSg25k")) {
            compareCase(loadActivs25k(), activs25kConfig());
        }
        if (shouldRunCase("OpenEI")) {
            compareCase(loadOpenEi(), openEiConfig());
        }
    }

    private static void compareCase(AclfNetwork net, CaseConfig config) throws Exception {
        int warmups = Math.max(0, Integer.getInteger("interpss.dclfSolverCompareWarmups", 1));
        int repeats = intProperty("interpss.dclfSolverCompareRepeats", 3);
        int parallelism = intProperty("interpss.dclfSolverCompareParallelism", 4);

        List<DclfBranchOutage> contingencies = config.contingencies(net);
        Set<String> monitors = config.monitors(net);

        assertTrue(!contingencies.isEmpty(), config.name + " contingencies");
        assertTrue(!monitors.isEmpty(), config.name + " monitors");

        RunResult reference = null;
        for (int repeat = 0; repeat < warmups + repeats; repeat++) {
            boolean warmup = repeat < warmups;

            RunResult csj = runOnce("CSJ", net, contingencies, monitors, parallelism);
            RunResult javaKlu = runOnce("JAVA_KLU", net, contingencies, monitors, parallelism);
            Parity parity = compareResults(config.name, csj.results, javaKlu.results);
            if (Boolean.getBoolean("interpss.dclfSolverCompareAssertParity")) {
                assertTrue(parity.matches, parity.message);
            }
            if (reference != null && Boolean.getBoolean("interpss.dclfSolverCompareAssertParity")) {
                Parity repeatParity = compareResults(config.name + " repeat consistency", reference.results, csj.results);
                assertTrue(repeatParity.matches, repeatParity.message);
            }
            else if (!warmup) {
                reference = csj;
            }

            int reportedRepeat = warmup ? 0 : repeat - warmups + 1;
            printRun(config.name, "CSJ", reportedRepeat, warmup, contingencies, monitors, parallelism, csj, "reference");
            printRun(config.name, "JAVA_KLU", reportedRepeat, warmup, contingencies, monitors, parallelism, javaKlu,
                    parity.matches ? "match" : "mismatch");
            if (!warmup) {
                double setupSpeedup = csj.setupNs / (double) javaKlu.setupNs;
                double totalSpeedup = csj.totalNs() / (double) javaKlu.totalNs();
                System.out.println("summary,case=" + config.name
                        + ",repeat=" + reportedRepeat
                        + ",setupSpeedupJavaKluOverCsj=" + format(setupSpeedup)
                        + ",totalSpeedupJavaKluOverCsj=" + format(totalSpeedup)
                        + ",parity=" + parity.message);
            }
        }
    }

    private static RunResult runOnce(
            String solverName,
            AclfNetwork net,
            List<DclfBranchOutage> contingencies,
            Set<String> monitors,
            int parallelism)
            throws InterpssException {
        if ("JAVA_KLU".equals(solverName)) {
            SparseEqnSolverProvider.useJavaKlu();
        }
        else {
            SparseEqnSolverProvider.useCSJ();
        }

        long setupStart = System.nanoTime();
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        DclfContingencySolutionMethod solutionMethod = solutionMethod();
        dclfAlgo.setSolutionMethod(solutionMethod);
        dclfAlgo.calculateDclf(DclfMethod.STD);
        long setupNs = System.nanoTime() - setupStart;

        List<DclfBranchOutage> working = refreshContingencies(dclfAlgo, contingencies);

        long scanStart = System.nanoTime();
        ConcurrentLinkedQueue<BranchCAResultRec> results =
                ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
                        net,
                        working,
                        monitors,
                        0.0,
                        false,
                        parallelism,
                        solutionMethod);
        long scanNs = System.nanoTime() - scanStart;

        return new RunResult(setupNs, scanNs, results);
    }

    private static DclfContingencySolutionMethod solutionMethod() {
        String configured = System.getProperty("interpss.dclfSolverCompareSolutionMethod",
                DclfContingencySolutionMethod.SparseEqnSolve.name());
        return DclfContingencySolutionMethod.valueOf(configured);
    }

    private static List<DclfBranchOutage> refreshContingencies(
            ContingencyAnalysisAlgorithm dclfAlgo,
            List<DclfBranchOutage> source)
            throws InterpssException {
        List<DclfBranchOutage> refreshed = new ArrayList<>(source.size());
        for (DclfBranchOutage contingency : source) {
            Branch branch = contingency.getOutageEquip().getBranch();
            DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branch.getId());
            DclfBranchOutage copy = createContingency(contingency.getId());
            copy.setOutageEquip(createCaOutageBranch(dclfBranch, ContingencyBranchOutageType.OPEN));
            refreshed.add(copy);
        }
        return refreshed;
    }

    private static void printRun(
            String caseName,
            String solver,
            int repeat,
            boolean warmup,
            List<DclfBranchOutage> contingencies,
            Set<String> monitors,
            int parallelism,
            RunResult result,
            String parity) {
        System.out.println(caseName + "," + solver + "," + repeat + "," + warmup
                + "," + contingencies.size()
                + "," + monitors.size()
                + "," + parallelism
                + "," + format(ms(result.setupNs))
                + "," + format(ms(result.scanNs))
                + "," + format(ms(result.totalNs()))
                + "," + result.results.size()
                + "," + parity);
    }

    private static Parity compareResults(
            String context,
            ConcurrentLinkedQueue<BranchCAResultRec> expected,
            ConcurrentLinkedQueue<BranchCAResultRec> actual) {
        Map<String, BranchCAResultRec> expectedByKey = toResultMap(expected);
        Map<String, BranchCAResultRec> actualByKey = toResultMap(actual);
        if (!expectedByKey.keySet().equals(actualByKey.keySet())) {
            int missing = 0;
            for (String key : expectedByKey.keySet()) {
                if (!actualByKey.containsKey(key)) {
                    missing++;
                }
            }
            int extra = 0;
            for (String key : actualByKey.keySet()) {
                if (!expectedByKey.containsKey(key)) {
                    extra++;
                }
            }
            return new Parity(false, context + ":missing=" + missing + ";extra=" + extra
                    + ";csjRecords=" + expectedByKey.size()
                    + ";javaKluRecords=" + actualByKey.size());
        }
        for (Map.Entry<String, BranchCAResultRec> entry : expectedByKey.entrySet()) {
            BranchCAResultRec expectedRec = entry.getValue();
            BranchCAResultRec actualRec = actualByKey.get(entry.getKey());
            double preDiff = Math.abs(expectedRec.preFlowMW - actualRec.preFlowMW);
            double shiftDiff = Math.abs(expectedRec.shiftedFlowMW - actualRec.shiftedFlowMW);
            double postDiff = Math.abs(expectedRec.getPostFlowMW() - actualRec.getPostFlowMW());
            if (preDiff > RESULT_TOLERANCE_MW
                    || shiftDiff > RESULT_TOLERANCE_MW
                    || postDiff > RESULT_TOLERANCE_MW) {
                return new Parity(false, context + ":valueDiff;key=" + entry.getKey()
                        + ";preDiff=" + format(preDiff)
                        + ";shiftDiff=" + format(shiftDiff)
                        + ";postDiff=" + format(postDiff));
            }
        }
        return new Parity(true, "match");
    }

    private static Map<String, BranchCAResultRec> toResultMap(
            ConcurrentLinkedQueue<BranchCAResultRec> results) {
        Map<String, BranchCAResultRec> byKey = new LinkedHashMap<>();
        for (BranchCAResultRec result : results) {
            byKey.put(result.contingency.getId() + "|" + result.aclfBranch.getId(), result);
        }
        return byKey;
    }

    private static AclfNetwork loadActivs25k() throws InterpssException {
        return IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
                .setFormat(PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)
                .load()
                .getImportedObj();
    }

    private static AclfNetwork loadOpenEi() throws InterpssException {
        return IpssAdapter.importAclfNet("testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW")
                .setFormat(PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)
                .load()
                .getImportedObj();
    }

    private static CaseConfig activs25kConfig() {
        return new CaseConfig(
                "ACTIVSg25k",
                intProperty("interpss.performance25kContingencies",
                        System.getProperty("interpss.performance25kContingencyJson") == null
                                ? 30 : Integer.MAX_VALUE),
                intProperty("interpss.performance25kMonitors", 60),
                System.getProperty("interpss.performance25kContingencyJson"),
                System.getProperty("interpss.performance25kMonitorJson"));
    }

    private static CaseConfig openEiConfig() {
        return new CaseConfig(
                "OpenEI",
                intProperty("interpss.performanceOpenEiContingencies", Integer.MAX_VALUE),
                intProperty("interpss.performanceOpenEiMonitors", 60),
                System.getProperty("interpss.performanceOpenEiContingencyJson",
                        "testData/psse/v33/OpenEI_filtered_contingencies.json"),
                System.getProperty("interpss.performanceOpenEiMonitorJson",
                        "testData/psse/v33/OpenEI_monitored_branches.json"));
    }

    private static boolean shouldRunCase(String name) {
        String configured = System.getProperty("interpss.dclfSolverCompareCases", "ACTIVSg25k,OpenEI");
        for (String token : configured.split(",")) {
            if (name.equalsIgnoreCase(token.trim())) {
                return true;
            }
        }
        return false;
    }

    private static int intProperty(String name, int defaultValue) {
        return Math.max(1, Integer.getInteger(name, defaultValue));
    }

    private static double ms(long ns) {
        return ns / 1_000_000.0;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.US, "%.3f", value);
    }

    private static final class RunResult {
        final long setupNs;
        final long scanNs;
        final ConcurrentLinkedQueue<BranchCAResultRec> results;

        RunResult(long setupNs, long scanNs, ConcurrentLinkedQueue<BranchCAResultRec> results) {
            this.setupNs = setupNs;
            this.scanNs = scanNs;
            this.results = results;
        }

        long totalNs() {
            return setupNs + scanNs;
        }
    }

    private static final class Parity {
        final boolean matches;
        final String message;

        Parity(boolean matches, String message) {
            this.matches = matches;
            this.message = message;
        }
    }

    private static final class CaseConfig {
        final String name;
        final int contingencyCount;
        final int monitorCount;
        final String contingencyJsonPath;
        final String monitorJsonPath;

        CaseConfig(
                String name,
                int contingencyCount,
                int monitorCount,
                String contingencyJsonPath,
                String monitorJsonPath) {
            this.name = name;
            this.contingencyCount = contingencyCount;
            this.monitorCount = monitorCount;
            this.contingencyJsonPath = contingencyJsonPath;
            this.monitorJsonPath = monitorJsonPath;
        }

        List<DclfBranchOutage> contingencies(AclfNetwork net) throws Exception {
            ContingencyAnalysisAlgorithm sourceAlgo = createContingencyAnalysisAlgorithm(net);
            sourceAlgo.calculateDclf();
            if (contingencyJsonPath != null) {
                List<BranchContingencyRecord> records =
                        ContingencyFileUtil.importContingenciesFromJson(new File(contingencyJsonPath));
                return new DclfContingencyHelper(sourceAlgo)
                        .createDclfContList(records)
                        .stream()
                        .limit(contingencyCount)
                        .collect(Collectors.toList());
            }
            return firstNonRefBranchOutages(net, sourceAlgo, contingencyCount);
        }

        Set<String> monitors(AclfNetwork net) throws Exception {
            if (monitorJsonPath != null) {
                List<MonitoredBranchRecord> records =
                        ContingencyFileUtil.importMonitoredBranchRecordsFromJson(new File(monitorJsonPath));
                return monitoredBranchIds(net, records, monitorCount);
            }
            return firstActiveBranchIds(net, monitorCount);
        }
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
            contingency.setOutageEquip(createCaOutageBranch(
                    dclfAlgo.getDclfAlgoBranch(branch.getId()),
                    ContingencyBranchOutageType.OPEN));
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

    private static Set<String> monitoredBranchIds(
            AclfNetwork net,
            List<MonitoredBranchRecord> monitorRecords,
            int maxCount) {
        Set<String> branchIds = new LinkedHashSet<>();
        for (MonitoredBranchRecord record : monitorRecords) {
            String branchId = com.interpss.common.util.NetUtilFunc.ToBranchId.f(
                    record.fromBus,
                    record.toBus,
                    record.ckt);
            AclfBranch branch = net.getBranch(branchId);
            if (branch != null && branch.isActive() && !branch.isConnect2RefBus()) {
                branchIds.add(branchId);
                if (branchIds.size() >= maxCount) {
                    break;
                }
            }
        }
        return branchIds;
    }
}
