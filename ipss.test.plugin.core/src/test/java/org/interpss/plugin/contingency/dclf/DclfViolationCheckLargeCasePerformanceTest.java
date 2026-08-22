package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import com.interpss.core.contingency.definition.ContingencyDefinition;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfContingencyHelper;
import org.interpss.plugin.contingency.util.DclfMultiOutageContingencyHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.DclfContingencyLimitStudy;
import com.interpss.core.algo.dclf.DclfIslandingTreatment;
import com.interpss.core.algo.dclf.solver.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.solver.FlowgateDclfAnalyzer;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;
import com.interpss.monitor.check.DclfLimitCheckCompileContext;
import com.interpss.monitor.check.DclfLimitCheckContext;
import com.interpss.monitor.check.DclfMwLimitViolationResult;
import com.interpss.monitor.check.MonitoringExceptionPolicy;
import com.interpss.monitor.check.MonitoringExceptionRecord;
import com.interpss.monitor.check.MonitoringExceptionStatus;
import com.interpss.monitor.check.MonitoringObjectType;
import com.interpss.monitor.check.NomogramMwBoundaryCheck;
import com.interpss.monitor.definition.FlowgateConstraintRecord;
import com.interpss.monitor.definition.FlowgateContingencyRef;
import com.interpss.monitor.definition.FlowgateLimitSet;
import com.interpss.monitor.definition.MonitoredBranchRecord;
import com.interpss.monitor.definition.MonitoredInterfaceRecord;
import com.interpss.monitor.definition.NomogramConstraintRecord;
import com.interpss.monitor.definition.NomogramRecord;
import com.interpss.monitor.result.DclfMonitoredConstraintResult;
import com.interpss.monitor.result.FlowgateViolationResult;

import org.interpss.fadapter.psse.PSSEDirectParser;
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
                true);

        runCase("ACTIVSg70k",
                Path.of(property("interpss.violationCheckPerf70k",
                        "/Users/ipssdev/Downloads/ACTIVSg70k/ACTIVSg70k.RAW")),
                optionalPath("interpss.violationCheckPerf70kContJson",
                        "/Users/ipssdev/Downloads/ACTIVSg70k/ACTIVSg70k_filtered_contingencies.json"),
                Boolean.getBoolean("interpss.violationCheckPerfInclude70k"));
    }

    @Test
    @Tag("large")
    public void compareActivs70kSingleVsMultiLineOutageTiming() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.activs70kSingleVsMultiTiming"),
                "Set -Dinterpss.activs70kSingleVsMultiTiming=true to run ACTIVSg70k single vs multi outage timing");

        SparseEqnSolverProvider.useJavaKlu();

        Path rawPath = Path.of(property("interpss.activs70kRaw",
                "/Users/ipssdev/Downloads/ACTIVSg70k/ACTIVSg70k.RAW"));
        Path multiJsonPath = Path.of(property("interpss.activs70kMultiJson",
                "testData/psse/v33/ACTIVSg70k_random_multiline_outages_10000_n3_n4.json"));
        assumeTrue(Files.isRegularFile(rawPath), "ACTIVSg70k RAW not found: " + rawPath);
        assumeTrue(Files.isRegularFile(multiJsonPath), "ACTIVSg70k multi-outage JSON not found: " + multiJsonPath);

        int contingencyCount = intProperty("interpss.activs70kTimingContingencies", 1000);
        int monitorCount = intProperty("interpss.activs70kTimingMonitors", 1000);
        int warmups = intProperty("interpss.activs70kTimingWarmups", 0);
        int repeats = intProperty("interpss.activs70kTimingRepeats", 1);
        int parallelism = intProperty("interpss.activs70kTimingParallelism", 4);
        int rhsBatchSize = intProperty("interpss.activs70kTimingRhsBatchSize", 64);
        DclfContingencySolutionMethod solutionMethod =
                DclfContingencySolutionMethod.valueOf(property(
                        "interpss.activs70kTimingSolutionMethod",
                        DclfContingencySolutionMethod.SparseEqnSolve.name()));

        AclfNetwork net = new PSSEDirectParser().parse(rawPath.toString());
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.calculateDclf();

        List<DclfBranchOutage> singleOutages = singleLineOutages(net, dclfAlgo, contingencyCount);
        List<DclfMultiOutage> multiOutages = loadMultiOutages(dclfAlgo, multiJsonPath, contingencyCount);
        Set<String> monitorIds = monitoredBranchIds(net, monitorCount);
        assumeTrue(singleOutages.size() == contingencyCount,
                "single outages available=" + singleOutages.size() + ", requested=" + contingencyCount);
        assumeTrue(multiOutages.size() == contingencyCount,
                "multi outages available=" + multiOutages.size() + ", requested=" + contingencyCount);
        assumeTrue(monitorIds.size() == monitorCount,
                "monitors available=" + monitorIds.size() + ", requested=" + monitorCount);

        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setOverloadThreshold(0.0);
        config.setDclfInclLoss(false);
        config.setSolutionMethod(solutionMethod);
        config.setKluEndpointRhsBatchSize(rhsBatchSize);
        config.setIslandingTreatment(DclfIslandingTreatment.valueOf(property(
                "interpss.activs70kTimingIslandingTreatment",
                DclfIslandingTreatment.FULL_DCLF_REPLAY.name())));

        System.out.println("case,studyType,solutionMethod,repeat,warmup,contingencies,monitors,parallelism,rhsBatchSize,elapsedMs,msPerContingency,results");
        for (int repeat = 0; repeat < warmups + repeats; repeat++) {
            boolean warmup = repeat < warmups;
            BranchRunResult single = runBranchCa(net, singleOutages, monitorIds, config, parallelism);
            int reportedRepeat = warmup ? repeat + 1 : repeat - warmups + 1;
            printBranchTiming("ACTIVSg70k", "single-line", solutionMethod, reportedRepeat, warmup,
                    singleOutages.size(), monitorIds.size(), parallelism, rhsBatchSize, single);
            BranchRunResult multi = runBranchCa(net, multiOutages, monitorIds, config, parallelism);
            printBranchTiming("ACTIVSg70k", "multi-line-3-4-full-replay", solutionMethod, reportedRepeat, warmup,
                    multiOutages.size(), monitorIds.size(), parallelism, rhsBatchSize, multi);
            if (!warmup) {
                double ratio = single.elapsedNs <= 0L ? 0.0 : (double) multi.elapsedNs / (double) single.elapsedNs;
                System.out.println("ACTIVSg70k,timingRatio,multiOverSingle,"
                        + reportedRepeat + "," + format(ratio));
            }
        }
    }

    @Test
    @Tag("large")
    public void benchmarkActivs70kMultiBranchOutageAnalysis() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.activs70kMultiBranchTiming"),
                "Set -Dinterpss.activs70kMultiBranchTiming=true to run ACTIVSg70k multiBranchOutageAnalysis timing");

        SparseEqnSolverProvider.useJavaKlu();

        Path rawPath = Path.of(property("interpss.activs70kRaw",
                "/Users/ipssdev/Downloads/ACTIVSg70k/ACTIVSg70k.RAW"));
        Path multiJsonPath = Path.of(property("interpss.activs70kMultiJson",
                "testData/psse/v33/ACTIVSg70k_random_multiline_outages_10000_n3_n4.json"));
        assumeTrue(Files.isRegularFile(rawPath), "ACTIVSg70k RAW not found: " + rawPath);
        assumeTrue(Files.isRegularFile(multiJsonPath), "ACTIVSg70k multi-outage JSON not found: " + multiJsonPath);

        int contingencyCount = intProperty("interpss.activs70kMultiBranchContingencies", 100);
        int warmups = intProperty("interpss.activs70kMultiBranchWarmups", 1);
        int repeats = intProperty("interpss.activs70kMultiBranchRepeats", 3);
        int candidateMultiplier = intProperty("interpss.activs70kMultiBranchCandidateMultiplier", 4);
        boolean requireSolvable = Boolean.parseBoolean(
                property("interpss.activs70kMultiBranchRequireSolvable", "true"));
        boolean requireFullRank = Boolean.parseBoolean(
                property("interpss.activs70kMultiBranchRequireFullRank", "true"));

        AclfNetwork net = new PSSEDirectParser().parse(rawPath.toString());
        ContingencyAnalysisAlgorithm dclfAlgo =
                createContingencyAnalysisAlgorithm(net, CacheType.SenCached, true);
        dclfAlgo.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
        dclfAlgo.calculateDclf();

        String definitionToken = property("interpss.activs70kMultiBranchDefinitionToken", "RANDOM");
        List<DclfMultiOutage> multiOutages =
                loadMultiOutagesByNameToken(
                        dclfAlgo,
                        multiJsonPath,
                        contingencyCount * Math.max(1, candidateMultiplier),
                        definitionToken);
        if (requireSolvable) {
            multiOutages = solvableMultiOutages(dclfAlgo, multiOutages, contingencyCount, requireFullRank);
        } else if (multiOutages.size() > contingencyCount) {
            multiOutages = new ArrayList<>(multiOutages.subList(0, contingencyCount));
        }
        assumeTrue(multiOutages.size() == contingencyCount,
                "multi outages available=" + multiOutages.size() + ", requested=" + contingencyCount);

        System.out.println("case,studyType,definitionToken,solutionMethod,repeat,warmup,contingencies,successes,failures,elapsedMs,msPerSuccess");
        for (int repeat = 0; repeat < warmups + repeats; repeat++) {
            boolean warmup = repeat < warmups;
            MultiBranchRunResult result = runMultiBranchOutageAnalysis(dclfAlgo, multiOutages);
            int reportedRepeat = warmup ? repeat + 1 : repeat - warmups + 1;
            System.out.println("ACTIVSg70k"
                    + ",multiBranchOutageAnalysis"
                    + "," + definitionToken
                    + "," + DclfContingencySolutionMethod.SparseEqnSolve
                    + "," + reportedRepeat
                    + "," + warmup
                    + "," + multiOutages.size()
                    + "," + result.successCount
                    + "," + result.failureCount
                    + "," + format(ms(result.elapsedNs))
                    + "," + format(ms(result.elapsedNs) / Math.max(1, result.successCount)));
        }
    }

    @Test
    @Tag("large")
    public void profileActivs70kFullReplayOneBusIslandContingencies() throws Exception {
        assumeTrue(Boolean.getBoolean("interpss.activs70kFullReplayProfile"),
                "Set -Dinterpss.activs70kFullReplayProfile=true to run ACTIVSg70k full replay profiling");

        SparseEqnSolverProvider.useJavaKlu();

        Path rawPath = Path.of(property("interpss.activs70kRaw",
                "/Users/ipssdev/Downloads/ACTIVSg70k/ACTIVSg70k.RAW"));
        Path multiJsonPath = Path.of(property("interpss.activs70kMultiJson",
                "testData/psse/v33/ACTIVSg70k_random_multiline_outages_10000_n3_n4.json"));
        assumeTrue(Files.isRegularFile(rawPath), "ACTIVSg70k RAW not found: " + rawPath);
        assumeTrue(Files.isRegularFile(multiJsonPath), "ACTIVSg70k multi-outage JSON not found: " + multiJsonPath);

        int contingencyCount = intProperty("interpss.activs70kFullReplayContingencies", 50);
        int monitorCount = intProperty("interpss.activs70kFullReplayMonitors", 1000);
        int warmups = intProperty("interpss.activs70kFullReplayWarmups", 1);
        int repeats = intProperty("interpss.activs70kFullReplayRepeats", 3);
        int parallelism = intProperty("interpss.activs70kFullReplayParallelism", 1);
        int rhsBatchSize = intProperty("interpss.activs70kFullReplayRhsBatchSize", 64);
        String definitionToken = System.getProperty("interpss.activs70kFullReplayDefinitionToken");

        AclfNetwork net = new PSSEDirectParser().parse(rawPath.toString());
        ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
        dclfAlgo.calculateDclf();

        List<DclfMultiOutage> contingencies = definitionToken == null || definitionToken.isBlank()
                ? loadMultiOutages(dclfAlgo, multiJsonPath, contingencyCount)
                : loadMultiOutagesByNameToken(dclfAlgo, multiJsonPath, contingencyCount, definitionToken);
        Set<String> monitorIds = monitoredBranchIds(net, monitorCount);
        assumeTrue(contingencies.size() == contingencyCount,
                (definitionToken == null ? "mixed replay" : definitionToken) + " contingencies available=" + contingencies.size()
                        + ", requested=" + contingencyCount);
        assumeTrue(monitorIds.size() == monitorCount,
                "monitors available=" + monitorIds.size() + ", requested=" + monitorCount);

        DclfContingencyConfig config = new DclfContingencyConfig();
        config.setOverloadThreshold(0.0);
        config.setDclfInclLoss(false);
        config.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
        config.setKluEndpointRhsBatchSize(rhsBatchSize);
        DclfIslandingTreatment islandingTreatment = DclfIslandingTreatment.valueOf(property(
                "interpss.activs70kFullReplayIslandingTreatment",
                DclfIslandingTreatment.FULL_DCLF_REPLAY.name()));
        config.setIslandingTreatment(islandingTreatment);

        System.out.println("case,studyType,solutionMethod,repeat,warmup,contingencies,monitors,parallelism,rhsBatchSize,elapsedMs,msPerContingency,results");
        for (int repeat = 0; repeat < warmups + repeats; repeat++) {
            boolean warmup = repeat < warmups;
            BranchRunResult result = runBranchCa(net, contingencies, monitorIds, config, parallelism);
            int reportedRepeat = warmup ? repeat + 1 : repeat - warmups + 1;
            printBranchTiming("ACTIVSg70k", "full-replay-" + islandingTreatment + "-"
                            + (definitionToken == null ? "mixed-json" : definitionToken),
                    DclfContingencySolutionMethod.SparseEqnSolve,
                    reportedRepeat, warmup, contingencies.size(), monitorIds.size(), parallelism, rhsBatchSize, result);
        }
    }

    private static void runCase(
            String caseName,
            Path rawPath,
            Path contingencyJsonPath,
            boolean enabled) throws Exception {
        if (!enabled) {
            System.out.println(caseName + ",skipped,set include property to true");
            return;
        }
        assumeTrue(Files.isRegularFile(rawPath), "Case file not found: " + rawPath);

        AclfNetwork net = new PSSEDirectParser().parse(rawPath.toString());
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

    private static List<DclfBranchOutage> singleLineOutages(
            AclfNetwork net,
            ContingencyAnalysisAlgorithm dclfAlgo,
            int count) {
        List<DclfBranchOutage> contingencies = new ArrayList<>(count);
        for (AclfBranch branch : net.getBranchList()) {
            if (!isUsableLine(branch)) {
                continue;
            }
            DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branch.getId());
            if (dclfBranch == null || !dclfBranch.isActive()) {
                continue;
            }
            DclfBranchOutage contingency = createContingency("single:" + branch.getId());
            DclfOutageBranch outage = createCaOutageBranch(dclfBranch, ContingencyBranchOutageType.OPEN);
            outage.setDclfFlow(dclfBranch.getDclfFlow());
            contingency.setOutageEquip(outage);
            contingencies.add(contingency);
            if (contingencies.size() >= count) {
                break;
            }
        }
        return contingencies;
    }

    private static List<DclfMultiOutage> loadMultiOutages(
            ContingencyAnalysisAlgorithm dclfAlgo,
            Path multiJsonPath,
            int count) throws Exception {
        List<ContingencyDefinition> definitions =
                ContingencyFileUtil.importContingencyDefinitionsFromJson(multiJsonPath.toFile());
        definitions = selectMixedMultiOutageDefinitions(definitions, count);
        return new DclfMultiOutageContingencyHelper(dclfAlgo)
                .createDclfMultiOutageContListFromDefinitions(definitions);
    }

    private static List<DclfMultiOutage> loadMultiOutagesByNameToken(
            ContingencyAnalysisAlgorithm dclfAlgo,
            Path multiJsonPath,
            int count,
            String token) throws Exception {
        List<ContingencyDefinition> definitions =
                ContingencyFileUtil.importContingencyDefinitionsFromJson(multiJsonPath.toFile());
        List<ContingencyDefinition> selected = new ArrayList<>(count);
        appendDefinitionsByName(selected, definitions, token, count);
        return new DclfMultiOutageContingencyHelper(dclfAlgo)
                .createDclfMultiOutageContListFromDefinitions(selected);
    }

    private static List<ContingencyDefinition> selectMixedMultiOutageDefinitions(
            List<ContingencyDefinition> definitions,
            int count) {
        if (definitions.size() <= count) {
            return definitions;
        }

        int singularTarget = Math.min(count, Math.max(0, (int) Math.round(count * 0.30)));
        int randomTarget = count - singularTarget;
        List<ContingencyDefinition> selected = new ArrayList<>(count);
        appendDefinitionsByName(selected, definitions, "SINGULAR", singularTarget);
        appendDefinitionsByName(selected, definitions, "RANDOM", randomTarget);

        for (ContingencyDefinition definition : definitions) {
            if (selected.size() >= count) {
                break;
            }
            if (!selected.contains(definition)) {
                selected.add(definition);
            }
        }
        return selected;
    }

    private static void appendDefinitionsByName(
            List<ContingencyDefinition> selected,
            List<ContingencyDefinition> definitions,
            String token,
            int targetCount) {
        for (ContingencyDefinition definition : definitions) {
            if (targetCount <= 0) {
                return;
            }
            String name = definition.getName();
            if (name != null && name.contains(token)) {
                selected.add(definition);
                targetCount--;
            }
        }
    }

    private static Set<String> monitoredBranchIds(AclfNetwork net, int count) {
        Set<String> monitorIds = new LinkedHashSet<>();
        for (AclfBranch branch : net.getBranchList()) {
            if (isUsableLine(branch)) {
                monitorIds.add(branch.getId());
                if (monitorIds.size() >= count) {
                    break;
                }
            }
        }
        return monitorIds;
    }

    private static List<DclfMultiOutage> oneBusIslandMultiOutages(
            AclfNetwork net,
            ContingencyAnalysisAlgorithm dclfAlgo,
            int count) {
        List<DclfMultiOutage> contingencies = new ArrayList<>(count);
        Set<String> usedBranchSets = new LinkedHashSet<>();
        for (Object rawBus : net.getBusList()) {
            if (!(rawBus instanceof Bus)) {
                continue;
            }
            Bus bus = (Bus) rawBus;
            if (!bus.isActive() || net.isRefBus(bus) || net.getRefBusIdSet().contains(bus.getId())) {
                continue;
            }
            List<AclfBranch> incidentBranches = activeIncidentDclfBranches(bus);
            if (incidentBranches.size() < 2 || incidentBranches.size() > 4) {
                continue;
            }
            String key = branchSetKey(incidentBranches);
            if (!usedBranchSets.add(key)) {
                continue;
            }

            DclfMultiOutage contingency =
                    createMultiOutageContingency(
                            "one-bus-island:" + bus.getId(),
                            ContingencyBranchOutageType.OPEN);
            boolean complete = true;
            for (AclfBranch branch : incidentBranches) {
                DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branch.getId());
                if (dclfBranch == null || !dclfBranch.isActive()) {
                    complete = false;
                    break;
                }
                DclfOutageBranch outage =
                        createCaOutageBranch(dclfBranch, ContingencyBranchOutageType.OPEN);
                outage.setDclfFlow(dclfBranch.getDclfFlow());
                contingency.getOutageEquips().add(outage);
            }
            if (complete && contingency.getOutageEquips().size() == incidentBranches.size()) {
                contingencies.add(contingency);
                if (contingencies.size() >= count) {
                    break;
                }
            }
        }
        return contingencies;
    }

    private static List<AclfBranch> activeIncidentDclfBranches(Bus bus) {
        List<AclfBranch> branches = new ArrayList<>(4);
        for (Branch rawBranch : bus.getBranchIterable()) {
            if (rawBranch instanceof AclfBranch) {
                AclfBranch branch = (AclfBranch) rawBranch;
                if (isDclfSwitchableBranch(branch)) {
                    branches.add(branch);
                }
            }
        }
        return branches;
    }

    private static boolean isDclfSwitchableBranch(AclfBranch branch) {
        return branch.isActive()
                && branch.getAdjustedZ() != null
                && Math.abs(branch.getAdjustedZ().getImaginary()) > 1.0e-12;
    }

    private static String branchSetKey(List<AclfBranch> branches) {
        List<String> ids = new ArrayList<>(branches.size());
        for (AclfBranch branch : branches) {
            ids.add(branch.getId());
        }
        ids.sort(String::compareTo);
        return String.join("|", ids);
    }

    private static BranchRunResult runBranchCa(
            AclfNetwork net,
            List<? extends com.interpss.core.contingency.BaseContingency<com.interpss.core.contingency.dclf.DclfMonitoringBranch>> contingencies,
            Set<String> monitorIds,
            DclfContingencyConfig config,
            int parallelism) {
        long start = System.nanoTime();
        ConcurrentLinkedQueue<BranchCAResultRec> results =
                ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
                        net,
                        contingencies,
                        monitorIds,
                        config,
                        parallelism);
        return new BranchRunResult(System.nanoTime() - start, results.size());
    }

    private static MultiBranchRunResult runMultiBranchOutageAnalysis(
            ContingencyAnalysisAlgorithm dclfAlgo,
            List<DclfMultiOutage> contingencies) {
        int successes = 0;
        int failures = 0;
        long start = System.nanoTime();
        for (DclfMultiOutage contingency : contingencies) {
            try {
                DclfOutageBranch[] outages =
                        contingency.getOutageEquips().toArray(new DclfOutageBranch[0]);
                dclfAlgo.multiOpenOutageAnalysis(outages);
                successes++;
            } catch (Exception e) {
                failures++;
            }
        }
        return new MultiBranchRunResult(System.nanoTime() - start, successes, failures);
    }

    private static List<DclfMultiOutage> solvableMultiOutages(
            ContingencyAnalysisAlgorithm dclfAlgo,
            List<DclfMultiOutage> candidates,
            int count,
            boolean requireFullRank) {
        List<DclfMultiOutage> selected = new ArrayList<>(count);
        for (DclfMultiOutage contingency : candidates) {
            if (selected.size() >= count) {
                break;
            }
            try {
                DclfOutageBranch[] outages =
                        contingency.getOutageEquips().toArray(new DclfOutageBranch[0]);
                dclfAlgo.multiOpenOutageAnalysis(outages);
                if (!requireFullRank || allOutageRowsRetained(outages)) {
                    selected.add(contingency);
                }
            } catch (Exception e) {
                // Skip singular/islanding cases when measuring the successful sparse sensitivity kernel.
            }
        }
        return selected;
    }

    private static boolean allOutageRowsRetained(DclfOutageBranch[] outages) {
        for (DclfOutageBranch outage : outages) {
            if (outage.getBranch().getSortNumber() < 0) {
                return false;
            }
        }
        return true;
    }

    private static void printBranchTiming(
            String caseName,
            String studyType,
            DclfContingencySolutionMethod solutionMethod,
            int repeat,
            boolean warmup,
            int contingencyCount,
            int monitorCount,
            int parallelism,
            int rhsBatchSize,
            BranchRunResult result) {
        System.out.println(caseName
                + "," + studyType
                + "," + solutionMethod
                + "," + repeat
                + "," + warmup
                + "," + contingencyCount
                + "," + monitorCount
                + "," + parallelism
                + "," + rhsBatchSize
                + "," + format(ms(result.elapsedNs))
                + "," + format(ms(result.elapsedNs) / Math.max(1, contingencyCount))
                + "," + result.resultCount);
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

    private static final class BranchRunResult {
        private final long elapsedNs;
        private final int resultCount;

        private BranchRunResult(long elapsedNs, int resultCount) {
            this.elapsedNs = elapsedNs;
            this.resultCount = resultCount;
        }
    }

    private static final class MultiBranchRunResult {
        private final long elapsedNs;
        private final int successCount;
        private final int failureCount;

        private MultiBranchRunResult(
                long elapsedNs,
                int successCount,
                int failureCount) {
            this.elapsedNs = elapsedNs;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }
    }
}
