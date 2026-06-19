package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfContingencyHelper;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.DclfContingencyLimitStudy;
import com.interpss.core.algo.dclf.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.FlowgateDclfAnalyzer;
import com.interpss.core.algo.dclf.ParallelDclfContingencyAnalyzer;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.check.DclfLimitCheckCompileContext;
import com.interpss.core.algo.dclf.check.DclfLimitCheckContext;
import com.interpss.core.algo.dclf.check.DclfMwLimitViolationResult;
import com.interpss.core.algo.dclf.check.MonitoringExceptionRecord;
import com.interpss.core.algo.dclf.check.MonitoringExceptionPolicy;
import com.interpss.core.algo.dclf.check.MonitoringExceptionStatus;
import com.interpss.core.algo.dclf.check.MonitoringObjectType;
import com.interpss.core.algo.dclf.check.NomogramMwBoundaryCheck;
import com.interpss.core.algo.dclf.definition.FlowgateConstraintRecord;
import com.interpss.core.algo.dclf.definition.FlowgateContingencyRef;
import com.interpss.core.algo.dclf.definition.FlowgateLimitSet;
import com.interpss.core.algo.dclf.definition.MonitoredBranchRecord;
import com.interpss.core.algo.dclf.definition.MonitoredInterfaceRecord;
import com.interpss.core.algo.dclf.definition.NomogramConstraintRecord;
import com.interpss.core.algo.dclf.definition.NomogramRecord;
import com.interpss.core.algo.dclf.result.DclfMonitoredConstraintResult;
import com.interpss.core.algo.dclf.result.FlowgateViolationResult;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;

@Tag("large")
public class DclfViolationCheckLargeCasePerformanceTest extends CorePluginTestSetup {
    private static final double NO_VIOLATION_LIMIT_MW = 1.0e9;

    @Test
    public void compareViolationCheckOverheadOnLargeCases() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.largeViolationCheckPerf"),
                "Set -Dinterpss.largeViolationCheckPerf=true to run large violation-check performance tests");

        SparseEqnSolverProvider.useJavaKlu();

        System.out.println("case,repeat,warmup,contingencies,monitors,parallelism,"
                + "oldMs,newNoExceptionMs,newSparseExceptionMs,"
                + "newNoExceptionOverOldPct,newSparseExceptionOverOldPct,"
                + "oldResults,newNoExceptionResults,newSparseExceptionResults");
        System.out.println("case,checkType,repeat,warmup,contingencies,monitors,checkCount,parallelism,elapsedMs,results");

        runCase("ACTIVSg25k",
                Path.of(property("interpss.violationCheckPerf25k",
                        "testData/psse/v33/ACTIVSg25k.RAW")),
                optionalPath("interpss.violationCheckPerf25kContJson", null),
                IpssAdapter.PsseVersion.PSSE_33,
                true);

        runCase("ACTIVSg70k",
                Path.of(property("interpss.violationCheckPerf70k",
                        "/Users/ipssdev/Downloads/ACTIVSg70k/ACTIVSg70k.RAW")),
                optionalPath("interpss.violationCheckPerf70kContJson",
                        "/Users/ipssdev/Downloads/ACTIVSg70k/ACTIVSg70k_filtered_contingencies.json"),
                IpssAdapter.PsseVersion.PSSE_33,
                Boolean.getBoolean("interpss.violationCheckPerfInclude70k"));
    }

    private static void runCase(
            String caseName,
            Path rawPath,
            Path contingencyJsonPath,
            IpssAdapter.PsseVersion psseVersion,
            boolean enabled) throws Exception {
        if (!enabled) {
            System.out.println(caseName + ",skipped,set include property to true");
            return;
        }
        assumeTrue(Files.isRegularFile(rawPath), "Case file not found: " + rawPath);

        AclfNetwork net = IpssAdapter.importAclfNet(rawPath.toString())
                .setFormat(PSSE)
                .setPsseVersion(psseVersion)
                .load()
                .getImportedObj();
        int maxContingencies = intProperty("interpss.violationCheckPerfMaxCont", 300);
        int maxMonitors = intProperty("interpss.violationCheckPerfMaxMon", 1000);
        int warmups = intProperty("interpss.violationCheckPerfWarmups", 1);
        int repeats = intProperty("interpss.violationCheckPerfRepeats", 3);
        int parallelism = intProperty("interpss.violationCheckPerfParallelism", 4);
        int rhsBatchSize = intProperty("interpss.violationCheckPerfRhsBatchSize", 64);
        int maxFlowgates = intProperty("interpss.violationCheckPerfMaxFlowgates", 500);
        int maxFlowgateContingencies = intProperty("interpss.violationCheckPerfMaxFlowgateContingencies", 50);
        int maxNomograms = intProperty("interpss.violationCheckPerfMaxNomograms", 1000);

        PreparedStudy study = prepareStudy(net, maxContingencies, maxMonitors);
        if (contingencyJsonPath != null) {
            study = new PreparedStudy(
                    loadContingenciesFromJson(net, contingencyJsonPath, maxContingencies),
                    study.monitoredExpressions,
                    List.of(),
                    List.of());
        }
        study = study.withSupplementalChecks(
                buildFlowgates(study.contingencies, study.monitoredExpressions, maxFlowgates, maxFlowgateContingencies),
                buildNomograms(study.monitoredExpressions, maxNomograms));
        assumeTrue(!study.contingencies.isEmpty(), caseName + " contingencies");
        assumeTrue(!study.monitoredExpressions.isEmpty(), caseName + " monitored expressions");

        List<MonitoringExceptionRecord> sparseExceptions = List.of(new MonitoringExceptionRecord(
                study.contingencies.get(0).getId(),
                MonitoringObjectType.INTERFACE,
                study.monitoredExpressions.get(0).getId(),
                MonitoringExceptionStatus.EXCLUDE));

        for (int repeat = 0; repeat < warmups + repeats; repeat++) {
            boolean warmup = repeat < warmups;
            RunResult oldPath = runOldPath(net, study, parallelism, rhsBatchSize);
            RunResult newNoException = runNewPath(net, study, List.of(), parallelism, rhsBatchSize);
            RunResult newSparseException = runNewPath(net, study, sparseExceptions, parallelism, rhsBatchSize);
            GenericRunResult flowgatePath = runFlowgatePath(net, study, parallelism, rhsBatchSize);
            GenericRunResult nomogramBasePath = runNomogramBasePath(net, study);

            assertEquals(oldPath.results.size(), newNoException.results.size(),
                    caseName + " no-exception wrapper result parity");

            if (!warmup) {
                double newNoExceptionOverhead = overheadPct(newNoException.elapsedNs, oldPath.elapsedNs);
                double sparseExceptionOverhead = overheadPct(newSparseException.elapsedNs, oldPath.elapsedNs);
                System.out.println(caseName + "," + (repeat - warmups + 1) + "," + false
                        + "," + study.contingencies.size()
                        + "," + study.monitoredExpressions.size()
                        + "," + parallelism
                        + "," + format(ms(oldPath.elapsedNs))
                        + "," + format(ms(newNoException.elapsedNs))
                        + "," + format(ms(newSparseException.elapsedNs))
                        + "," + format(newNoExceptionOverhead)
                        + "," + format(sparseExceptionOverhead)
                        + "," + oldPath.results.size()
                        + "," + newNoException.results.size()
                        + "," + newSparseException.results.size());
                printSupplementalResult(caseName, "flowgate", repeat - warmups + 1, false,
                        study, study.flowgates.size(), parallelism, flowgatePath);
                printSupplementalResult(caseName, "nomogram-base", repeat - warmups + 1, false,
                        study, study.nomograms.size(), parallelism, nomogramBasePath);
            }
        }
    }

    private static PreparedStudy prepareStudy(
            AclfNetwork net,
            int maxContingencies,
            int maxMonitors) throws Exception {
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.calculateDclf();

        List<DclfBranchOutage> contingencies = new ArrayList<>();
        List<MonitoredInterfaceRecord> monitoredExpressions = new ArrayList<>();
        for (com.interpss.core.net.Branch rawBranch : net.getBranchList()) {
            if (!(rawBranch instanceof AclfBranch)) {
                continue;
            }
            AclfBranch branch = (AclfBranch) rawBranch;
            if (!isUsableLine(branch)) {
                continue;
            }
            if (contingencies.size() < maxContingencies) {
                DclfBranchOutage contingency = createContingency("cont:" + branch.getId());
                DclfOutageBranch outage = createCaOutageBranch(
                        dclfAlgo.getDclfAlgoBranch(branch.getId()),
                        ContingencyBranchOutageType.OPEN);
                outage.setDclfFlow(dclfAlgo.getDclfAlgoBranch(branch.getId()).getDclfFlow());
                contingency.setOutageEquip(outage);
                contingencies.add(contingency);
            }
            if (monitoredExpressions.size() < maxMonitors) {
                MonitoredInterfaceRecord expression = new MonitoredInterfaceRecord(
                        "mon:" + branch.getId(),
                        NO_VIOLATION_LIMIT_MW);
                expression.addBranch(new MonitoredBranchRecord(branch.getId(), 1.0));
                monitoredExpressions.add(expression);
            }
            if (contingencies.size() >= maxContingencies
                    && monitoredExpressions.size() >= maxMonitors) {
                break;
            }
        }
        return new PreparedStudy(contingencies, monitoredExpressions, List.of(), List.of());
    }

    private static List<FlowgateConstraintRecord> buildFlowgates(
            List<DclfBranchOutage> contingencies,
            List<MonitoredInterfaceRecord> monitoredExpressions,
            int maxFlowgates,
            int maxFlowgateContingencies) {
        int count = Math.min(Math.max(0, maxFlowgates), monitoredExpressions.size());
        int contingencyCount = Math.min(
                Math.max(1, maxFlowgateContingencies),
                contingencies.size());
        List<FlowgateConstraintRecord> flowgates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            DclfOutageBranch outage = contingencies.get(i % contingencyCount).getOutageEquip();
            if (outage == null || outage.getBranch() == null) {
                continue;
            }
            FlowgateConstraintRecord flowgate = FlowgateConstraintRecord.of(
                    "fg:" + i,
                    FlowgateContingencyRef.singleBranchOpen(outage.getBranch().getId()),
                    FlowgateLimitSet.realtime(NO_VIOLATION_LIMIT_MW));
            for (MonitoredBranchRecord branch : monitoredExpressions.get(i).getBranches()) {
                flowgate.addBranch(new MonitoredBranchRecord(branch.getBranchId(), branch.getCoefficient()));
            }
            flowgates.add(flowgate);
        }
        return flowgates;
    }

    private static List<NomogramRecord> buildNomograms(
            List<MonitoredInterfaceRecord> monitoredExpressions,
            int maxNomograms) {
        int count = Math.min(Math.max(0, maxNomograms), Math.max(0, monitoredExpressions.size() - 1));
        List<NomogramRecord> nomograms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nomograms.add(new NomogramRecord(
                    "nom:" + i,
                    monitoredExpressions.get(i),
                    monitoredExpressions.get(i + 1),
                    List.of(new NomogramConstraintRecord(
                            "limit:0",
                            0.6,
                            0.4,
                            NO_VIOLATION_LIMIT_MW))));
        }
        return nomograms;
    }

    private static List<DclfBranchOutage> loadContingenciesFromJson(
            AclfNetwork net,
            Path contingencyJsonPath,
            int maxContingencies) throws Exception {
        assumeTrue(Files.isRegularFile(contingencyJsonPath),
                "Contingency JSON file not found: " + contingencyJsonPath);
        List<BranchContingencyRecord> records =
                ContingencyFileUtil.importContingenciesFromJson(contingencyJsonPath.toFile());
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.calculateDclf();
        List<DclfBranchOutage> contingencies =
                new DclfContingencyHelper(dclfAlgo).createDclfContList(records);
        if (contingencies.size() <= 5000) {
            System.out.println("jsonContingenciesWarning,path=" + contingencyJsonPath
                    + ",mapped=" + contingencies.size()
                    + ",expectedMoreThan=5000");
        }
        if (maxContingencies > 0 && contingencies.size() > maxContingencies) {
            return new ArrayList<>(contingencies.subList(0, maxContingencies));
        }
        return contingencies;
    }

    private static boolean isUsableLine(AclfBranch branch) {
        return branch.isActive()
                && branch.isLine()
                && !branch.isConnect2RefBus()
                && branch.getAdjustedZ() != null
                && Math.abs(branch.getAdjustedZ().getImaginary()) > 1.0e-12;
    }

    private static RunResult runOldPath(
            AclfNetwork net,
            PreparedStudy study,
            int parallelism,
            int rhsBatchSize) {
        long start = System.nanoTime();
        ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results =
                ParallelDclfContingencyAnalyzer.performMonitoredConstraintAnalysis(
                        net,
                        study.contingencies,
                        study.monitoredExpressions,
                        100.0,
                        false,
                        parallelism,
                        DclfContingencySolutionMethod.SparseEqnSolve,
                        rhsBatchSize);
        return new RunResult(System.nanoTime() - start, results);
    }

    private static RunResult runNewPath(
            AclfNetwork net,
            PreparedStudy study,
            List<MonitoringExceptionRecord> exceptions,
            int parallelism,
            int rhsBatchSize) {
        long start = System.nanoTime();
        ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results =
                DclfContingencyLimitStudy.performMonitoredExpressionAnalysis(
                        net,
                        study.contingencies,
                        study.monitoredExpressions,
                        exceptions,
                        100.0,
                        false,
                        parallelism,
                        DclfContingencySolutionMethod.SparseEqnSolve,
                        rhsBatchSize);
        return new RunResult(System.nanoTime() - start, results);
    }

    private static GenericRunResult runFlowgatePath(
            AclfNetwork net,
            PreparedStudy study,
            int parallelism,
            int rhsBatchSize) {
        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setDclfInclLoss(false);
        config.setOverloadThreshold(100.0);
        config.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
        config.setKluEndpointRhsBatchSize(rhsBatchSize);

        long start = System.nanoTime();
        ConcurrentLinkedQueue<FlowgateViolationResult> results =
                FlowgateDclfAnalyzer.executeFlowgateAnalysis(
                        net,
                        study.flowgates,
                        config,
                        parallelism);
        return new GenericRunResult(System.nanoTime() - start, results.size());
    }

    private static GenericRunResult runNomogramBasePath(
            AclfNetwork net,
            PreparedStudy study) throws Exception {
        long start = System.nanoTime();
        Collection<DclfMwLimitViolationResult> results = new ConcurrentLinkedQueue<>();
        if (!study.nomograms.isEmpty()) {
            ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
            dclfAlgo.calculateDclf();
            DclfContingencyConfig config = new DclfContingencyConfig();
            config.setDclfInclLoss(false);
            config.setOverloadThreshold(100.0);
            List<String> branchIds = branchIds(study.monitoredExpressions);
            Map<String, Integer> branchIndexById = branchIndexById(branchIds);
            double[] preFlowMw = preFlowMw(dclfAlgo, branchIds);

            NomogramMwBoundaryCheck check = new NomogramMwBoundaryCheck(study.nomograms, 100.0);
            check.compile(new DclfLimitCheckCompileContext(
                    net,
                    dclfAlgo,
                    branchIndexById,
                    net.getBaseMva(),
                    config));
            check.evaluateBase(new DclfLimitCheckContext(
                    net,
                    dclfAlgo,
                    null,
                    "BASE",
                    preFlowMw,
                    preFlowMw,
                    net.getBaseMva(),
                    config,
                    MonitoringExceptionPolicy.empty()), results);
        }
        return new GenericRunResult(System.nanoTime() - start, results.size());
    }

    private static List<String> branchIds(List<MonitoredInterfaceRecord> monitoredExpressions) {
        Map<String, Boolean> branchIds = new LinkedHashMap<>();
        for (MonitoredInterfaceRecord expression : monitoredExpressions) {
            for (MonitoredBranchRecord branch : expression.getBranches()) {
                branchIds.put(branch.getBranchId(), Boolean.TRUE);
            }
        }
        return new ArrayList<>(branchIds.keySet());
    }

    private static Map<String, Integer> branchIndexById(List<String> branchIds) {
        Map<String, Integer> branchIndexById = new LinkedHashMap<>();
        for (int i = 0; i < branchIds.size(); i++) {
            branchIndexById.put(branchIds.get(i), i);
        }
        return branchIndexById;
    }

    private static double[] preFlowMw(
            ContingencyAnalysisAlgorithm dclfAlgo,
            List<String> branchIds) {
        double[] preFlowMw = new double[branchIds.size()];
        double baseMva = dclfAlgo.getAclfNet().getBaseMva();
        for (int i = 0; i < branchIds.size(); i++) {
            DclfAlgoBranch branch = dclfAlgo.getDclfAlgoBranch(branchIds.get(i));
            if (branch == null || !branch.isActive()) {
                throw new IllegalArgumentException("Nomogram monitored branch is not active: " + branchIds.get(i));
            }
            preFlowMw[i] = branch.getDclfFlow() * baseMva;
        }
        return preFlowMw;
    }

    private static void printSupplementalResult(
            String caseName,
            String checkType,
            int repeat,
            boolean warmup,
            PreparedStudy study,
            int checkCount,
            int parallelism,
            GenericRunResult result) {
        System.out.println(caseName
                + "," + checkType
                + "," + repeat
                + "," + warmup
                + "," + study.contingencies.size()
                + "," + study.monitoredExpressions.size()
                + "," + checkCount
                + "," + parallelism
                + "," + format(ms(result.elapsedNs))
                + "," + result.resultCount);
    }

    private static String property(String name, String defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Path optionalPath(String name, String defaultValue) {
        String value = property(name, defaultValue);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static int intProperty(String name, int defaultValue) {
        return Integer.getInteger(name, defaultValue);
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double overheadPct(long actualNs, long baselineNs) {
        return baselineNs <= 0L ? 0.0 : 100.0 * (actualNs - baselineNs) / baselineNs;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static final class PreparedStudy {
        private final List<DclfBranchOutage> contingencies;
        private final List<MonitoredInterfaceRecord> monitoredExpressions;
        private final List<FlowgateConstraintRecord> flowgates;
        private final List<NomogramRecord> nomograms;

        private PreparedStudy(
                List<DclfBranchOutage> contingencies,
                List<MonitoredInterfaceRecord> monitoredExpressions,
                List<FlowgateConstraintRecord> flowgates,
                List<NomogramRecord> nomograms) {
            this.contingencies = contingencies;
            this.monitoredExpressions = monitoredExpressions;
            this.flowgates = flowgates;
            this.nomograms = nomograms;
        }

        private PreparedStudy withSupplementalChecks(
                List<FlowgateConstraintRecord> flowgates,
                List<NomogramRecord> nomograms) {
            return new PreparedStudy(contingencies, monitoredExpressions, flowgates, nomograms);
        }
    }

    private static final class RunResult {
        private final long elapsedNs;
        private final ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results;

        private RunResult(
                long elapsedNs,
                ConcurrentLinkedQueue<DclfMonitoredConstraintResult> results) {
            this.elapsedNs = elapsedNs;
            this.results = results;
        }
    }

    private static final class GenericRunResult {
        private final long elapsedNs;
        private final int resultCount;

        private GenericRunResult(long elapsedNs, int resultCount) {
            this.elapsedNs = elapsedNs;
            this.resultCount = resultCount;
        }
    }
}
