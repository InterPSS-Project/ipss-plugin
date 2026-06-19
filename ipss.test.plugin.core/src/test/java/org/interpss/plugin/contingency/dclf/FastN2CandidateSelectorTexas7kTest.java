package org.interpss.plugin.contingency.dclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.fastn2.FastN2CandidateRequest;
import com.interpss.core.algo.dclf.fastn2.FastN2CandidateResult;
import com.interpss.core.algo.dclf.fastn2.FastN2CandidateSelector;
import com.interpss.core.algo.dclf.fastn2.FastN2CandidatePair;
import com.interpss.core.algo.dclf.fastn2.FastN2LodfStats;
import com.interpss.core.algo.dclf.fastn2.FastN2MonitorScreeningStats;
import com.interpss.core.algo.dclf.fastn2.FastN2Pruner;
import com.interpss.core.algo.dclf.fastn2.FastN2PruningOptions;
import com.interpss.core.algo.dclf.fastn2.FastN2PruningResult;
import com.interpss.core.algo.dclf.fastn2.FastN2RankingMode;
import com.interpss.core.algo.dclf.fastn2.FastN2ScreeningOptions;
import com.interpss.core.algo.dclf.fastn2.FastN2StudyInventory;

@Tag("large")
public class FastN2CandidateSelectorTexas7kTest extends CorePluginTestSetup {

	private static final Path TEXAS7K_DIR = resolveTexas7kDir();
	private static final Path TEXAS7K_RAW = TEXAS7K_DIR.resolve("Texas7k_20210804.RAW");
	private static final Path TEXAS7K_CONTINGENCIES = TEXAS7K_DIR.resolve("Texas7k_20210804_filtered_contingencies.json");
	private static final Path TEXAS7K_MONITORS = TEXAS7K_DIR.resolve("Texas7k_20210804_monitored_branches.json");
	private static final Path ACTIVS25K_DIR = resolveActivs25kDir();
	private static final Path ACTIVS25K_RAW = ACTIVS25K_DIR.resolve("ACTIVSg25k.RAW");
	private static final Path ACTIVS25K_CONTINGENCIES = ACTIVS25K_DIR.resolve("25k_above100kV_filtered_contingencies.json");
	private static final Path ACTIVS25K_MONITORS = ACTIVS25K_DIR.resolve("25k_above138kV_monitored_branches.json");
	private static final int OUTAGE_CANDIDATE_LIMIT =
			Integer.getInteger("interpss.fastN2Texas7kOutageLimit", 300);
	private static final int MONITORED_BRANCH_LIMIT =
			Integer.getInteger("interpss.fastN2Texas7kMonitorLimit", 600);
	private static final int FULL_SET_VALIDATION_SAMPLE_SIZE =
			Integer.getInteger("interpss.fastN2Texas7kValidationSamples", 100);
	private static final int FULL_SET_MAX_SURVIVOR_VALIDATION_PAIRS =
			Integer.getInteger("interpss.fastN2Texas7kMaxSurvivorValidationPairs", 250_000);
	private static final int FULL_SET_MAX_RETURNED_CANDIDATES =
			Integer.getInteger("interpss.fastN2Texas7kMaxReturnedCandidates", 10_000);
	private static final int FULL_SET_EXACT_EVALUATION_CHUNK_SIZE =
			Integer.getInteger("interpss.fastN2Texas7kExactEvaluationChunkSize",
					FastN2ScreeningOptions.DEFAULT_EXACT_EVALUATION_CHUNK_SIZE);
	private static final int FULL_SET_EXACT_EVALUATION_THREADS =
			Integer.getInteger("interpss.fastN2Texas7kExactEvaluationThreads", 1);
	private static final boolean FULL_SET_EXACT_MONITOR_PRUNING =
			Boolean.getBoolean("interpss.fastN2Texas7kExactMonitorPruning");
	private static final boolean FAIL_ON_SAMPLE_DANGEROUS =
			Boolean.getBoolean("interpss.fastN2Texas7kFailOnSampleDangerous");
	private static final double THERMAL_LIMIT_PERCENT = 100.0;
	private static final double FALLBACK_RATING_BASE_FLOW_MULTIPLIER = 1.20;

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
				Path.of("/Users/ipssdev/github/ipss-desktop/examples/25k"));
		return candidates.stream()
				.filter(path -> Files.isRegularFile(path.resolve("ACTIVSg25k.RAW")))
				.findFirst()
				.orElse(candidates.get(0));
	}

	@BeforeEach
	public void requireLargeTestsEnabled() {
		assumeTrue(Boolean.getBoolean("interpss.largeDclfTests"),
				"Set -Dinterpss.largeDclfTests=true to run Texas 7k Fast N-2 benchmarks");
	}

	@Test
	public void texas7kSubsetPerformanceReport() throws Exception {
		assumeTrue(Files.isRegularFile(TEXAS7K_RAW), "Texas 7k RAW fixture is not available: " + TEXAS7K_RAW);
		assumeTrue(Files.isRegularFile(TEXAS7K_CONTINGENCIES),
				"Texas 7k contingency fixture is not available: " + TEXAS7K_CONTINGENCIES);
		assumeTrue(Files.isRegularFile(TEXAS7K_MONITORS),
				"Texas 7k monitor fixture is not available: " + TEXAS7K_MONITORS);

		AclfNetwork baselineNet = importPsse(TEXAS7K_RAW);
		int zeroOrMissingRatings = fillMissingRatingsFromBaseDclfFlow(
				baselineNet,
				FALLBACK_RATING_BASE_FLOW_MULTIPLIER);
		Texas7kStudySet studySet = texas7kStudySet(baselineNet);
		FastN2CandidateRequest baselineRequest = new FastN2CandidateRequest(
				baselineNet,
				DclfMethod.STD,
				studySet.monitoredBranchIds(),
				studySet.outageCandidateBranchIds(),
				new FastN2ScreeningOptions(THERMAL_LIMIT_PERCENT, 0.0, 1.0e-8, 0, false));

		long baselineStartedNanos = System.nanoTime();
		FastN2CandidateResult baseline = new FastN2CandidateSelector().selectCandidates(baselineRequest);
		long baselineElapsedMillis = elapsedMillis(baselineStartedNanos);

		AclfNetwork prunedNet = importPsse(TEXAS7K_RAW);
		fillMissingRatingsFromBaseDclfFlow(prunedNet, FALLBACK_RATING_BASE_FLOW_MULTIPLIER);
		FastN2CandidateRequest prunedRequest = new FastN2CandidateRequest(
				prunedNet,
				DclfMethod.STD,
				studySet.monitoredBranchIds(),
				studySet.outageCandidateBranchIds(),
				new FastN2ScreeningOptions(
						THERMAL_LIMIT_PERCENT,
						0.0,
						1.0e-8,
						0,
						false,
						true,
						false,
						FastN2ScreeningOptions.DEFAULT_MINIMUM_RISK_GRAPH_SCORE,
						FastN2ScreeningOptions.DEFAULT_MINIMUM_OUTAGE_INTERACTION_LODF));

		long prunedStartedNanos = System.nanoTime();
		FastN2CandidateResult pruned = new FastN2CandidateSelector().selectCandidates(prunedRequest);
		long prunedElapsedMillis = elapsedMillis(prunedStartedNanos);

		Set<PairKey> baselineKeys = candidateKeys(baseline);
		Set<PairKey> prunedKeys = candidateKeys(pruned);
		assertEquals(baselineKeys, prunedKeys, "Upper-bound pruning should preserve Texas 7k dangerous pairs");
		assertTrue(pruned.stats().exactEvaluatedPairCount() <= baseline.stats().exactEvaluatedPairCount());

		writeTexas7kReport(new Texas7kReport(
				studySet.totalContingencyCount(),
				studySet.totalMonitorCount(),
				studySet.outageCandidateBranchIds().size(),
				studySet.monitoredBranchIds().size(),
				pairCount(studySet.outageCandidateBranchIds().size()),
				zeroOrMissingRatings,
				baseline.candidates().size(),
				baseline.stats().exactEvaluatedPairCount(),
				baselineElapsedMillis,
				baseline.stats().lodfStats(),
				pruned.candidates().size(),
				pruned.stats().exactEvaluatedPairCount(),
				pruned.stats().upperBoundPruningSkippedPairCount(),
				prunedElapsedMillis,
				pruned.stats().lodfStats()));
	}

	@Test
	public void texas7kFullSetPrunedOnlyBenchmark() throws Exception {
		assumeTrue(Boolean.getBoolean("interpss.fastN2Texas7kFullSet"),
				"Set -Dinterpss.fastN2Texas7kFullSet=true to run the full Texas 7k Fast N-2 benchmark");
		assumeTrue(Files.isRegularFile(TEXAS7K_RAW), "Texas 7k RAW fixture is not available: " + TEXAS7K_RAW);
		assumeTrue(Files.isRegularFile(TEXAS7K_CONTINGENCIES),
				"Texas 7k contingency fixture is not available: " + TEXAS7K_CONTINGENCIES);
		assumeTrue(Files.isRegularFile(TEXAS7K_MONITORS),
				"Texas 7k monitor fixture is not available: " + TEXAS7K_MONITORS);

		AclfNetwork prunerNet = importPsse(TEXAS7K_RAW);
		int zeroOrMissingRatings = fillMissingRatingsFromBaseDclfFlow(
				prunerNet,
				FALLBACK_RATING_BASE_FLOW_MULTIPLIER);
		Texas7kStudySet studySet = texas7kStudySet(prunerNet, 0, 0);
		FastN2ScreeningOptions pruningOptions = fullSetPruningOptions();
		FastN2CandidateRequest prunerRequest = new FastN2CandidateRequest(
				prunerNet,
				DclfMethod.STD,
				studySet.monitoredBranchIds(),
				studySet.outageCandidateBranchIds(),
				pruningOptions);

		long memoryBeforeBytes = usedMemoryBytes();
		long prunerStartedNanos = System.nanoTime();
		FastN2PruningResult pruning = new FastN2Pruner().prune(
				prunerRequest,
				FastN2PruningOptions.defaults().withPairPruningBoundsCaptured());
		long prunerElapsedMillis = elapsedMillis(prunerStartedNanos);
		long memoryAfterPrunerBytes = usedMemoryBytes();
		long survivorPairs = pruning.finalPairCount();
		Map<String, Double> baseFlowMagnitudeByBranchId = baseFlowMagnitudesMw(prunerNet);
		writeFullSetPrunerCheckpoint(new Texas7kFullSetPrunerCheckpoint(
				studySet.totalContingencyCount(),
				studySet.totalMonitorCount(),
				studySet.outageCandidateBranchIds().size(),
				studySet.monitoredBranchIds().size(),
				pruning.originalPairCount(),
				zeroOrMissingRatings,
				pruning.finalPairCount(),
				pruning.originalPairCount() - pruning.finalPairCount(),
				pruning.islandingPairCount(),
				prunerElapsedMillis,
				memoryBeforeBytes,
				memoryAfterPrunerBytes));
		System.out.printf(
				"Texas 7k full-set pruning complete: survivors=%d, pruned=%d, elapsed=%d ms%n",
				pruning.finalPairCount(),
				pruning.originalPairCount() - pruning.finalPairCount(),
				prunerElapsedMillis);
		assumeTrue(survivorPairs <= FULL_SET_MAX_SURVIVOR_VALIDATION_PAIRS,
				"Survivor pair count " + survivorPairs + " exceeds validation guard "
						+ FULL_SET_MAX_SURVIVOR_VALIDATION_PAIRS);

		AclfNetwork selectorNet = importPsse(TEXAS7K_RAW);
		fillMissingRatingsFromBaseDclfFlow(selectorNet, FALLBACK_RATING_BASE_FLOW_MULTIPLIER);
		FastN2CandidateRequest selectorRequest = new FastN2CandidateRequest(
				selectorNet,
				DclfMethod.STD,
				studySet.monitoredBranchIds(),
				studySet.outageCandidateBranchIds(),
				fullSetSelectorOptions());
		long selectorStartedNanos = System.nanoTime();
		FastN2CandidateResult pruned = new FastN2CandidateSelector().selectCandidates(selectorRequest);
		long selectorElapsedMillis = elapsedMillis(selectorStartedNanos);
		long memoryAfterSelectorBytes = usedMemoryBytes();
		long exactDangerousPairCount = pruned.stats().originalPairCount() - pruned.stats().prunedPairCount();
		System.out.printf(
				"Texas 7k full-set selector complete: dangerous=%d, returned=%d, exactEvaluated=%d, elapsed=%d ms%n",
				exactDangerousPairCount,
				pruned.candidates().size(),
				pruned.stats().exactEvaluatedPairCount(),
				selectorElapsedMillis);

		List<SampledPair> sampledPrunedAwayPairs = sampledPrunedAwayPairs(
				pruning,
				baseFlowMagnitudeByBranchId,
				FULL_SET_VALIDATION_SAMPLE_SIZE);
		SampleValidationResult sampleValidation = validateExactPairSamples(
				studySet,
				sampledPrunedAwayPairs);
		ReturnedCandidateValidationResult returnedValidation = validateReturnedCandidates(pruned);
		SurvivorValidationResult survivorValidation = new SurvivorValidationResult(
				survivorPairs,
				pruned.stats().exactEvaluatedPairCount(),
				exactDangerousPairCount,
				pruned.stats().exactEvaluatedPairCount() == survivorPairs);

		writeDangerousPairCsv(pruned);
		writeDangerousPairJson(pruned);
		writePrunedAwayDiagnosticsCsv(sampleValidation);
		writePlanningReviewMarkdown(pruned, sampleValidation, returnedValidation);
		writeFullSetReport(new Texas7kFullSetReport(
				studySet.totalContingencyCount(),
				studySet.totalMonitorCount(),
				studySet.outageCandidateBranchIds().size(),
				studySet.monitoredBranchIds().size(),
				pruning.originalPairCount(),
				zeroOrMissingRatings,
				pruning.finalPairCount(),
				pruning.originalPairCount() - pruning.finalPairCount(),
				pruning.islandingPairCount(),
				prunerElapsedMillis,
				exactDangerousPairCount,
				pruned.candidates().size(),
				pruned.stats().truncatedByMaxCandidatePairs(),
				FULL_SET_MAX_RETURNED_CANDIDATES,
				FULL_SET_EXACT_EVALUATION_CHUNK_SIZE,
				pruned.stats().exactEvaluatedPairCount(),
				pruned.stats().upperBoundPruningSkippedPairCount(),
				selectorElapsedMillis,
				pruned.stats().lodfStats(),
				pruned.stats().monitorScreeningStats(),
				top20RankingSummary(pruned.candidates()),
				sampleValidation,
				returnedValidation,
				survivorValidation,
				memoryBeforeBytes,
				memoryAfterPrunerBytes,
				memoryAfterSelectorBytes));
		if (FAIL_ON_SAMPLE_DANGEROUS) {
			assertEquals(0, sampleValidation.dangerousPairCount(),
					"Sampled pruned-away pairs should not contain exact dangerous pairs");
		}
	}

	@Test
	public void activs25kInventoryReport() throws Exception {
		assumeTrue(Boolean.getBoolean("interpss.fastN2Activs25kInventory"),
				"Set -Dinterpss.fastN2Activs25kInventory=true to run the ACTIVSg25k Fast N-2 inventory");
		assumeTrue(Files.isRegularFile(ACTIVS25K_RAW), "ACTIVSg25k RAW fixture is not available: " + ACTIVS25K_RAW);
		assumeTrue(Files.isRegularFile(ACTIVS25K_CONTINGENCIES),
				"ACTIVSg25k contingency fixture is not available: " + ACTIVS25K_CONTINGENCIES);
		assumeTrue(Files.isRegularFile(ACTIVS25K_MONITORS),
				"ACTIVSg25k monitor fixture is not available: " + ACTIVS25K_MONITORS);

		AclfNetwork net = importPsse(ACTIVS25K_RAW);
		List<BranchContingencyRecord> contingencies =
				ContingencyFileUtil.importContingenciesFromJson(ACTIVS25K_CONTINGENCIES.toFile());
		List<MonitoredBranchRecord> monitors =
				ContingencyFileUtil.importMonitoredBranchRecordsFromJson(ACTIVS25K_MONITORS.toFile());
		List<String> outageCandidateIds = contingencies.stream()
				.map(record -> record.name)
				.toList();
		List<String> monitorIds = monitors.stream()
				.map(MonitoredBranchRecord::getBranchId)
				.toList();
		FastN2StudyInventory inventory = FastN2StudyInventory.analyze(new FastN2CandidateRequest(
				net,
				DclfMethod.STD,
				monitorIds,
				outageCandidateIds,
				FastN2ScreeningOptions.defaults()));

		writeActivs25kInventoryReport(new Activs25kInventoryReport(
				net.getBusList().size(),
				net.getBranchList().size(),
				contingencies.size(),
				monitors.size(),
				inventory));
		assertTrue(inventory.finalOutageCandidateCount() > 0);
		assertTrue(inventory.monitoredBranchCount() > 0);
	}

	private static Texas7kStudySet texas7kStudySet(AclfNetwork net) throws Exception {
		return texas7kStudySet(net, OUTAGE_CANDIDATE_LIMIT, MONITORED_BRANCH_LIMIT);
	}

	private static Texas7kStudySet texas7kStudySet(
			AclfNetwork net,
			int outageCandidateLimit,
			int monitoredBranchLimit) throws Exception {
		List<BranchContingencyRecord> contingencies =
				ContingencyFileUtil.importContingenciesFromJson(TEXAS7K_CONTINGENCIES.toFile());
		List<MonitoredBranchRecord> monitors =
				ContingencyFileUtil.importMonitoredBranchRecordsFromJson(TEXAS7K_MONITORS.toFile());

		List<String> outageCandidateIds = contingencies.stream()
				.map(record -> record.name)
				.filter(branchId -> isActiveBranch(net, branchId))
				.distinct()
				.limit(limitOrAll(outageCandidateLimit))
				.toList();
		Set<String> outageSet = new HashSet<>(outageCandidateIds);
		boolean excludeOutageBranchesFromMonitors = outageCandidateLimit > 0;
		List<String> monitorIds = monitors.stream()
				.map(MonitoredBranchRecord::getBranchId)
				.filter(branchId -> isActiveBranch(net, branchId))
				.filter(branchId -> !excludeOutageBranchesFromMonitors || !outageSet.contains(branchId))
				.collect(Collectors.toCollection(LinkedHashSet::new))
				.stream()
				.limit(limitOrAll(monitoredBranchLimit))
				.toList();

		return new Texas7kStudySet(
				contingencies.size(),
				monitors.size(),
				outageCandidateIds,
				monitorIds);
	}

	private static long limitOrAll(int limit) {
		return limit > 0 ? limit : Long.MAX_VALUE;
	}

	private static FastN2ScreeningOptions fullSetPruningOptions() {
		return new FastN2ScreeningOptions(
				THERMAL_LIMIT_PERCENT,
				0.0,
				1.0e-8,
				0,
				false,
				true,
				false,
				FastN2ScreeningOptions.DEFAULT_MINIMUM_RISK_GRAPH_SCORE,
				FastN2ScreeningOptions.DEFAULT_MINIMUM_OUTAGE_INTERACTION_LODF);
	}

	private static FastN2ScreeningOptions fullSetSelectorOptions() {
		FastN2ScreeningOptions options = new FastN2ScreeningOptions(
				THERMAL_LIMIT_PERCENT,
				0.0,
				1.0e-8,
				FULL_SET_MAX_RETURNED_CANDIDATES,
				false,
				true,
				false,
				FastN2ScreeningOptions.DEFAULT_MINIMUM_RISK_GRAPH_SCORE,
				FastN2ScreeningOptions.DEFAULT_MINIMUM_OUTAGE_INTERACTION_LODF,
				FULL_SET_EXACT_EVALUATION_CHUNK_SIZE)
						.withExactEvaluationThreadCount(FULL_SET_EXACT_EVALUATION_THREADS);
		if (FULL_SET_EXACT_MONITOR_PRUNING) {
			options = options.withExactMonitorPruningEnabled();
		}
		return options;
	}

	private static boolean isActiveBranch(AclfNetwork net, String branchId) {
		AclfBranch branch = net.getBranch(branchId);
		return branch != null && branch.isActive() && !branch.isConnect2RefBus();
	}

	private static AclfNetwork importPsse(Path path) throws InterpssException {
		return IpssAdapter.importAclfNet(path.toString())
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)
				.load()
				.getImportedObj();
	}

	private static int fillMissingRatingsFromBaseDclfFlow(AclfNetwork net, double baseFlowMultiplier) {
		ContingencyAnalysisAlgorithm dclfAlgo =
				com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		if (!dclfAlgo.calculateDclf(DclfMethod.STD)) {
			throw new IllegalStateException("Could not calculate DCLF for Texas 7k rating fallback.");
		}
		int changed = 0;
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			AclfBranch branch = dclfBranch.getBranch();
			if (branch == null || !dclfBranch.isActive() || branch.getRatingMvaB() > 0.0) {
				continue;
			}
			double baseFlowMw = Math.abs(dclfBranch.getDclfFlow() * net.getBaseMva());
			branch.setRatingMva2(Math.max(1.0, baseFlowMw * baseFlowMultiplier));
			changed++;
		}
		return changed;
	}

	private static Map<String, Double> baseFlowMagnitudesMw(AclfNetwork net) {
		ContingencyAnalysisAlgorithm dclfAlgo =
				com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		if (!dclfAlgo.calculateDclf(DclfMethod.STD)) {
			throw new IllegalStateException("Could not calculate DCLF for Texas 7k base flow diagnostics.");
		}
		Map<String, Double> flows = new java.util.HashMap<>();
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			AclfBranch branch = dclfBranch.getBranch();
			if (branch != null && dclfBranch.isActive()) {
				flows.put(branch.getId(), Math.abs(dclfBranch.getDclfFlow() * net.getBaseMva()));
			}
		}
		return flows;
	}

	private static Set<PairKey> candidateKeys(FastN2CandidateResult result) {
		return result.candidates().stream()
				.map(candidate -> PairKey.of(candidate.outageBranchId1(), candidate.outageBranchId2()))
				.collect(Collectors.toSet());
	}

	private static void writeTexas7kReport(Texas7kReport report) throws Exception {
		Path reportPath = Path.of("target", "fast-n2-texas7k-performance.txt");
		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, report.toText(), StandardCharsets.UTF_8);
	}

	private static void writeFullSetReport(Texas7kFullSetReport report) throws Exception {
		Path reportPath = Path.of("target", "fast-n2-texas7k-fullset-pruned-report.txt");
		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, report.toText(), StandardCharsets.UTF_8);
	}

	private static void writeFullSetPrunerCheckpoint(Texas7kFullSetPrunerCheckpoint report) throws Exception {
		Path reportPath = Path.of("target", "fast-n2-texas7k-fullset-pruner-checkpoint.txt");
		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, report.toText(), StandardCharsets.UTF_8);
	}

	private static void writeActivs25kInventoryReport(Activs25kInventoryReport report) throws Exception {
		Path reportPath = Path.of("target", "fast-n2-activsg25k-inventory.txt");
		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, report.toText(), StandardCharsets.UTF_8);
	}

	private static void writePrunedAwayDiagnosticsCsv(SampleValidationResult result) throws Exception {
		Path csvPath = Path.of("target", "fast-n2-texas7k-pruned-away-diagnostics.csv");
		Files.createDirectories(csvPath.getParent());
		StringBuilder csv = new StringBuilder();
		csv.append("bucket,outageBranchId1,outageBranchId2,pairBaseFlowRiskMw,")
				.append("dangerous,boundingMonitorBranchId,violationCount,upperBoundLoadingPercent,")
				.append("totalOverloadMw,totalNormalizedOverload,maxOverloadPercent,maxOverloadMw,")
				.append("severityScore,pairPruningBound,pairPruningIteration,")
				.append("pruningDecision,survivorMask\n");
		for (PrunedAwayPairDiagnostic diagnostic : result.diagnostics()) {
			csv.append(diagnostic.bucket()).append(',')
					.append(csv(diagnostic.pair().branchId1())).append(',')
					.append(csv(diagnostic.pair().branchId2())).append(',')
					.append(diagnostic.pairBaseFlowRiskMw()).append(',')
					.append(diagnostic.dangerous()).append(',')
					.append(csv(diagnostic.boundingMonitorBranchId())).append(',')
					.append(diagnostic.violationCount()).append(',')
					.append(diagnostic.upperBoundLoadingPercent()).append(',')
					.append(diagnostic.totalOverloadMw()).append(',')
					.append(diagnostic.totalNormalizedOverload()).append(',')
					.append(diagnostic.maxOverloadPercent()).append(',')
					.append(diagnostic.maxOverloadMw()).append(',')
					.append(diagnostic.severityScore()).append(',')
					.append(diagnostic.pairPruningBound()).append(',')
					.append(diagnostic.pairPruningIteration()).append(',')
					.append("PRUNED_BY_UPPER_BOUND_HEURISTIC,false\n");
		}
		Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);
	}

	private static void writePlanningReviewMarkdown(
			FastN2CandidateResult result,
			SampleValidationResult sampleValidation,
			ReturnedCandidateValidationResult returnedValidation) throws Exception {
		Path reportPath = Path.of("target", "fast-n2-texas7k-planning-review.md");
		Files.createDirectories(reportPath.getParent());
		Files.writeString(
				reportPath,
				planningReviewMarkdown(result, sampleValidation, returnedValidation),
				StandardCharsets.UTF_8);
	}

	private static String planningReviewMarkdown(
			FastN2CandidateResult result,
			SampleValidationResult sampleValidation,
			ReturnedCandidateValidationResult returnedValidation) {
		return """
				# Fast N-2 Texas 7K Planning Review

				## Completeness Status

				- Pruning safety classification: %s
				- Sampled pruned-away pairs: %d
				- Exact dangerous pairs in pruned-away sample: %d
				- Returned top-K false positives: %d

				## Monitor-Side Exact Evaluation

				%s

				## Ranking Overlap

				%s

				## Top Repeated Outage Branches

				%s

				## Top Repeated Overloaded Monitored Branches

				%s

				## Top Repeated Bounding Monitors

				%s
				""".formatted(
				sampleValidation.dangerousPairCount() == 0
						? "HEURISTIC_SAMPLE_CLEAN_NOT_CERTIFIED"
						: "HEURISTIC_UNSAFE_SAMPLE_MISSES",
				sampleValidation.sampledPrunedAwayPairCount(),
				sampleValidation.dangerousPairCount(),
				returnedValidation.falsePositiveCount(),
				monitorScreeningMarkdown(
						result.stats().exactEvaluationThreadCount(),
						result.stats().monitorScreeningStats()),
				rankingOverlapMarkdown(result.candidates(), 20),
				repeatedOutageBranchMarkdown(result.candidates(), 20),
				repeatedViolationMonitorMarkdown(result.candidates(), 20),
				repeatedBoundingMonitorMarkdown(result.candidates(), 20));
	}

	private static String monitorScreeningMarkdown(int exactEvaluationThreadCount, FastN2MonitorScreeningStats stats) {
		return """
				| Metric | Value |
				|---|---:|
				| Exact evaluation threads | %d |
				| Exact monitor pruning enabled | %s |
				| Total monitor visits | %d |
				| Exact monitor evaluations | %d |
				| Pruned monitor evaluations | %d |
				| Monitor evaluations at or above 90%% loading | %d |
				| Monitor evaluations at or above 95%% loading | %d |
				| Monitor evaluations at or above 98%% loading | %d |
				| Violating monitor evaluations | %d |
				| Exact pairs with violations | %d |
				| Distinct violating monitors | %d |
				| Max violations on one outage pair | %d |
				| Max loading percent | %.6f |
				| Estimated reduction if only >=90%% monitors needed exact scoring | %.2f%% |
				| Estimated reduction if only >=95%% monitors needed exact scoring | %.2f%% |
				| Estimated reduction if only >=98%% monitors needed exact scoring | %.2f%% |
				""".formatted(
				exactEvaluationThreadCount,
				FULL_SET_EXACT_MONITOR_PRUNING,
				stats.totalMonitorVisitCount(),
				stats.exactMonitorEvaluationCount(),
				stats.prunedMonitorEvaluationCount(),
				stats.near90PercentEvaluationCount(),
				stats.near95PercentEvaluationCount(),
				stats.near98PercentEvaluationCount(),
				stats.violatingMonitorEvaluationCount(),
				stats.exactEvaluatedPairsWithViolations(),
				stats.distinctViolatingMonitorCount(),
				stats.maxViolationCountPerPair(),
				stats.maxLoadingPercent(),
				100.0 * stats.monitorEvaluationReductionIfOnlyNear90Percent(),
				100.0 * stats.monitorEvaluationReductionIfOnlyNear95Percent(),
				100.0 * stats.monitorEvaluationReductionIfOnlyNear98Percent());
	}

	private static void writeDangerousPairCsv(FastN2CandidateResult result) throws Exception {
		Path csvPath = Path.of("target", "fast-n2-texas7k-dangerous-pairs.csv");
		Files.createDirectories(csvPath.getParent());
		RankColumns ranks = rankColumns(result.candidates());
		StringBuilder csv = new StringBuilder();
		csv.append("outageBranchId1,outageBranchId2,boundingMonitorBranchId,violationCount,")
				.append("upperBoundLoadingPercent,totalOverloadMw,totalNormalizedOverload,")
				.append("maxOverloadPercent,maxOverloadMw,severityScore,")
				.append("rankByTotalNormalizedOverload,rankByViolationCount,")
				.append("rankByMaxOverloadPercent,rankByCompositeSeverity,")
				.append("singularOrIslandingRisk\n");
		result.candidates().stream()
				.sorted(candidateComparator())
				.forEach(candidate -> csv.append(csv(candidate.outageBranchId1())).append(',')
						.append(csv(candidate.outageBranchId2())).append(',')
						.append(csv(candidate.boundingMonitorBranchId())).append(',')
						.append(candidate.violationCount()).append(',')
						.append(candidate.upperBoundLoadingPercent()).append(',')
						.append(candidate.totalOverloadMw()).append(',')
						.append(candidate.totalNormalizedOverload()).append(',')
						.append(candidate.maxOverloadPercent()).append(',')
						.append(candidate.maxOverloadMw()).append(',')
						.append(candidate.severityScore()).append(',')
						.append(ranks.rank(FastN2RankingMode.TOTAL_NORMALIZED_OVERLOAD, candidate)).append(',')
						.append(ranks.rank(FastN2RankingMode.VIOLATION_COUNT, candidate)).append(',')
						.append(ranks.rank(FastN2RankingMode.MAX_OVERLOAD_PERCENT, candidate)).append(',')
						.append(ranks.rank(FastN2RankingMode.COMPOSITE_SEVERITY, candidate)).append(',')
						.append(candidate.singularOrIslandingRisk()).append('\n'));
		Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);
	}

	private static void writeDangerousPairJson(FastN2CandidateResult result) throws Exception {
		Path jsonPath = Path.of("target", "fast-n2-texas7k-dangerous-pairs.json");
		Files.createDirectories(jsonPath.getParent());
		RankColumns ranks = rankColumns(result.candidates());
		StringBuilder json = new StringBuilder();
		json.append("[\n");
		List<FastN2CandidatePair> candidates = result.candidates().stream()
				.sorted(candidateComparator())
				.toList();
		for (int i = 0; i < candidates.size(); i++) {
			FastN2CandidatePair candidate = candidates.get(i);
			json.append("  {")
					.append("\"outageBranchId1\":\"").append(json(candidate.outageBranchId1())).append("\",")
					.append("\"outageBranchId2\":\"").append(json(candidate.outageBranchId2())).append("\",")
					.append("\"boundingMonitorBranchId\":\"").append(json(candidate.boundingMonitorBranchId())).append("\",")
					.append("\"violationCount\":").append(candidate.violationCount()).append(',')
					.append("\"upperBoundLoadingPercent\":").append(candidate.upperBoundLoadingPercent()).append(',')
					.append("\"totalOverloadMw\":").append(candidate.totalOverloadMw()).append(',')
					.append("\"totalNormalizedOverload\":").append(candidate.totalNormalizedOverload()).append(',')
					.append("\"maxOverloadPercent\":").append(candidate.maxOverloadPercent()).append(',')
					.append("\"maxOverloadMw\":").append(candidate.maxOverloadMw()).append(',')
					.append("\"severityScore\":").append(candidate.severityScore()).append(',')
					.append("\"rankByTotalNormalizedOverload\":")
					.append(ranks.rank(FastN2RankingMode.TOTAL_NORMALIZED_OVERLOAD, candidate)).append(',')
					.append("\"rankByViolationCount\":")
					.append(ranks.rank(FastN2RankingMode.VIOLATION_COUNT, candidate)).append(',')
					.append("\"rankByMaxOverloadPercent\":")
					.append(ranks.rank(FastN2RankingMode.MAX_OVERLOAD_PERCENT, candidate)).append(',')
					.append("\"rankByCompositeSeverity\":")
					.append(ranks.rank(FastN2RankingMode.COMPOSITE_SEVERITY, candidate)).append(',')
					.append("\"singularOrIslandingRisk\":").append(candidate.singularOrIslandingRisk())
					.append("}");
			if (i + 1 < candidates.size()) {
				json.append(',');
			}
			json.append('\n');
		}
		json.append("]\n");
		Files.writeString(jsonPath, json.toString(), StandardCharsets.UTF_8);
	}

	private static Comparator<FastN2CandidatePair> candidateComparator() {
		return FastN2RankingMode.TOTAL_NORMALIZED_OVERLOAD.comparator();
	}

	private static RankColumns rankColumns(List<FastN2CandidatePair> candidates) {
		EnumMap<FastN2RankingMode, Map<PairKey, Integer>> ranks = new EnumMap<>(FastN2RankingMode.class);
		for (FastN2RankingMode mode : List.of(
				FastN2RankingMode.TOTAL_NORMALIZED_OVERLOAD,
				FastN2RankingMode.VIOLATION_COUNT,
				FastN2RankingMode.MAX_OVERLOAD_PERCENT,
				FastN2RankingMode.COMPOSITE_SEVERITY)) {
			List<FastN2CandidatePair> sorted = candidates.stream()
					.sorted(mode.comparator())
					.toList();
			Map<PairKey, Integer> modeRanks = new java.util.HashMap<>();
			for (int index = 0; index < sorted.size(); index++) {
				FastN2CandidatePair candidate = sorted.get(index);
				modeRanks.put(PairKey.of(candidate.outageBranchId1(), candidate.outageBranchId2()), index + 1);
			}
			ranks.put(mode, modeRanks);
		}
		return new RankColumns(ranks);
	}

	private static String rankingOverlapMarkdown(List<FastN2CandidatePair> candidates, int topN) {
		List<FastN2RankingMode> modes = List.of(
				FastN2RankingMode.TOTAL_NORMALIZED_OVERLOAD,
				FastN2RankingMode.VIOLATION_COUNT,
				FastN2RankingMode.MAX_OVERLOAD_PERCENT,
				FastN2RankingMode.COMPOSITE_SEVERITY);
		Map<FastN2RankingMode, Set<PairKey>> topPairs = new EnumMap<>(FastN2RankingMode.class);
		for (FastN2RankingMode mode : modes) {
			topPairs.put(mode, candidates.stream()
					.sorted(mode.comparator())
					.limit(topN)
					.map(candidate -> PairKey.of(candidate.outageBranchId1(), candidate.outageBranchId2()))
					.collect(Collectors.toSet()));
		}
		StringBuilder text = new StringBuilder();
		text.append("| Ranking A | Ranking B | Shared Top ").append(topN).append(" |\n");
		text.append("|---|---|---:|\n");
		for (int i = 0; i < modes.size(); i++) {
			for (int j = i + 1; j < modes.size(); j++) {
				Set<PairKey> overlap = new HashSet<>(topPairs.get(modes.get(i)));
				overlap.retainAll(topPairs.get(modes.get(j)));
				text.append("| ")
						.append(modes.get(i))
						.append(" | ")
						.append(modes.get(j))
						.append(" | ")
						.append(overlap.size())
						.append(" |\n");
			}
		}
		return text.toString();
	}

	private static String repeatedOutageBranchMarkdown(List<FastN2CandidatePair> candidates, int limit) {
		Map<String, Long> counts = new java.util.HashMap<>();
		for (FastN2CandidatePair candidate : candidates) {
			counts.merge(candidate.outageBranchId1(), 1L, Long::sum);
			counts.merge(candidate.outageBranchId2(), 1L, Long::sum);
		}
		return countTableMarkdown("Outage Branch", "Top-K Appearances", counts, limit);
	}

	private static String repeatedViolationMonitorMarkdown(List<FastN2CandidatePair> candidates, int limit) {
		Map<String, Long> counts = new java.util.HashMap<>();
		for (FastN2CandidatePair candidate : candidates) {
			candidate.violations().forEach(violation -> counts.merge(violation.monitorBranchId(), 1L, Long::sum));
		}
		return countTableMarkdown("Monitored Branch", "Violation Appearances", counts, limit);
	}

	private static String repeatedBoundingMonitorMarkdown(List<FastN2CandidatePair> candidates, int limit) {
		Map<String, Long> counts = new java.util.HashMap<>();
		for (FastN2CandidatePair candidate : candidates) {
			if (candidate.boundingMonitorBranchId() != null && !candidate.boundingMonitorBranchId().isBlank()) {
				counts.merge(candidate.boundingMonitorBranchId(), 1L, Long::sum);
			}
		}
		return countTableMarkdown("Bounding Monitor", "Top-K Appearances", counts, limit);
	}

	private static String countTableMarkdown(
			String labelColumn,
			String countColumn,
			Map<String, Long> counts,
			int limit) {
		StringBuilder text = new StringBuilder();
		text.append("| ").append(labelColumn).append(" | ").append(countColumn).append(" |\n");
		text.append("|---|---:|\n");
		counts.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed()
						.thenComparing(Map.Entry.comparingByKey()))
				.limit(limit)
				.forEach(entry -> text.append("| ")
						.append(entry.getKey())
						.append(" | ")
						.append(entry.getValue())
						.append(" |\n"));
		return text.toString();
	}

	private static String top20RankingSummary(List<FastN2CandidatePair> candidates) {
		StringBuilder summary = new StringBuilder();
		for (FastN2RankingMode mode : List.of(
				FastN2RankingMode.TOTAL_NORMALIZED_OVERLOAD,
				FastN2RankingMode.VIOLATION_COUNT,
				FastN2RankingMode.MAX_OVERLOAD_PERCENT,
				FastN2RankingMode.COMPOSITE_SEVERITY)) {
			summary.append("- ").append(mode).append('\n');
			List<FastN2CandidatePair> top = candidates.stream()
					.sorted(mode.comparator())
					.limit(20)
					.toList();
			for (int index = 0; index < top.size(); index++) {
				FastN2CandidatePair candidate = top.get(index);
				summary.append("  ")
						.append(index + 1)
						.append(". ")
						.append(candidate.outageBranchId1())
						.append(" + ")
						.append(candidate.outageBranchId2())
						.append(" | score=")
						.append(mode.score(candidate))
						.append(" | violations=")
						.append(candidate.violationCount())
						.append(" | totalNorm=")
						.append(candidate.totalNormalizedOverload())
						.append(" | maxPct=")
						.append(candidate.maxOverloadPercent())
						.append(" | maxMW=")
						.append(candidate.maxOverloadMw())
						.append(" | composite=")
						.append(candidate.severityScore())
						.append('\n');
			}
		}
		return summary.toString();
	}

	private static List<SampledPair> sampledPrunedAwayPairs(
			FastN2PruningResult pruning,
			Map<String, Double> baseFlowMagnitudeByBranchId,
			int sampleSize) {
		if (sampleSize <= 0) {
			return List.of();
		}
		long prunedPairCount = pruning.originalPairCount() - pruning.finalPairCount();
		if (prunedPairCount <= 0L) {
			return List.of();
		}
		double maxPairRiskMw = maxPrunedPairBaseFlowRiskMw(pruning, baseFlowMagnitudeByBranchId);
		int[] bucketCounts = prunedPairBucketCounts(pruning, baseFlowMagnitudeByBranchId, maxPairRiskMw);
		int perBucketTarget = Math.max(1, (int) Math.ceil(sampleSize / 3.0));
		int[] selectedByBucket = new int[3];
		long[] seenByBucket = new long[3];
		long[] nextOrdinalByBucket = new long[3];
		long[] strideByBucket = new long[3];
		for (int bucket = 0; bucket < strideByBucket.length; bucket++) {
			strideByBucket[bucket] = Math.max(1L, bucketCounts[bucket] / Math.max(1, perBucketTarget));
		}
		List<SampledPair> samples = new ArrayList<>();
		boolean[][] mask = pruning.survivorPairMask();
		for (int x = 0; x < mask.length && samples.size() < sampleSize; x++) {
			for (int y = x + 1; y < mask[x].length && samples.size() < sampleSize; y++) {
				if (mask[x][y]) {
					continue;
				}
				String branchId1 = pruning.outageBranchIds().get(x);
				String branchId2 = pruning.outageBranchIds().get(y);
				double risk = pairBaseFlowRiskMw(branchId1, branchId2, baseFlowMagnitudeByBranchId);
				int bucket = riskBucket(risk, maxPairRiskMw);
				if (selectedByBucket[bucket] < perBucketTarget
						&& seenByBucket[bucket] >= nextOrdinalByBucket[bucket]) {
					samples.add(new SampledPair(
							PairKey.of(branchId1, branchId2),
							x,
							y,
							RiskBucket.values()[bucket],
							risk,
							pruning.pairPruningBound(x, y),
							pruning.pairPruningIteration(x, y)));
					selectedByBucket[bucket]++;
					nextOrdinalByBucket[bucket] += strideByBucket[bucket];
				}
				seenByBucket[bucket]++;
			}
		}
		return samples;
	}

	private static int[] prunedPairBucketCounts(
			FastN2PruningResult pruning,
			Map<String, Double> baseFlowMagnitudeByBranchId,
			double maxPairRiskMw) {
		int[] counts = new int[3];
		boolean[][] mask = pruning.survivorPairMask();
		for (int x = 0; x < mask.length; x++) {
			for (int y = x + 1; y < mask[x].length; y++) {
				if (!mask[x][y]) {
					String branchId1 = pruning.outageBranchIds().get(x);
					String branchId2 = pruning.outageBranchIds().get(y);
					double risk = pairBaseFlowRiskMw(branchId1, branchId2, baseFlowMagnitudeByBranchId);
					counts[riskBucket(risk, maxPairRiskMw)]++;
				}
			}
		}
		return counts;
	}

	private static double maxPrunedPairBaseFlowRiskMw(
			FastN2PruningResult pruning,
			Map<String, Double> baseFlowMagnitudeByBranchId) {
		double max = 0.0;
		boolean[][] mask = pruning.survivorPairMask();
		for (int x = 0; x < mask.length; x++) {
			for (int y = x + 1; y < mask[x].length; y++) {
				if (!mask[x][y]) {
					String branchId1 = pruning.outageBranchIds().get(x);
					String branchId2 = pruning.outageBranchIds().get(y);
					max = Math.max(max, pairBaseFlowRiskMw(branchId1, branchId2, baseFlowMagnitudeByBranchId));
				}
			}
		}
		return max;
	}

	private static int riskBucket(double pairBaseFlowRiskMw, double maxPairRiskMw) {
		if (maxPairRiskMw <= 0.0) {
			return RiskBucket.LOW.ordinal();
		}
		double normalized = pairBaseFlowRiskMw / maxPairRiskMw;
		if (normalized >= 2.0 / 3.0) {
			return RiskBucket.HIGH.ordinal();
		}
		if (normalized >= 1.0 / 3.0) {
			return RiskBucket.MEDIUM.ordinal();
		}
		return RiskBucket.LOW.ordinal();
	}

	private static double pairBaseFlowRiskMw(
			String branchId1,
			String branchId2,
			Map<String, Double> baseFlowMagnitudeByBranchId) {
		return baseFlowMagnitudeByBranchId.getOrDefault(branchId1, 0.0)
				+ baseFlowMagnitudeByBranchId.getOrDefault(branchId2, 0.0);
	}

	private static SampleValidationResult validateExactPairSamples(
			Texas7kStudySet studySet,
			List<SampledPair> sampledPairs) throws Exception {
		List<PrunedAwayPairDiagnostic> diagnostics = new ArrayList<>();
		long startedNanos = System.nanoTime();
		for (SampledPair sampledPair : sampledPairs) {
			PairKey pair = sampledPair.pair();
			AclfNetwork net = importPsse(TEXAS7K_RAW);
			fillMissingRatingsFromBaseDclfFlow(net, FALLBACK_RATING_BASE_FLOW_MULTIPLIER);
			FastN2CandidateResult result = new FastN2CandidateSelector().selectCandidates(
					new FastN2CandidateRequest(
							net,
							DclfMethod.STD,
							studySet.monitoredBranchIds(),
							List.of(pair.branchId1(), pair.branchId2()),
							new FastN2ScreeningOptions(THERMAL_LIMIT_PERCENT, 0.0, 1.0e-8, 0, false)));
			FastN2CandidatePair candidate = result.candidates().isEmpty() ? null : result.candidates().get(0);
			diagnostics.add(PrunedAwayPairDiagnostic.of(sampledPair, candidate));
		}
		return new SampleValidationResult(diagnostics, elapsedMillis(startedNanos));
	}

	private static ReturnedCandidateValidationResult validateReturnedCandidates(FastN2CandidateResult result) {
		int falsePositiveCount = 0;
		for (FastN2CandidatePair candidate : result.candidates()) {
			if (!candidate.singularOrIslandingRisk() && candidate.violationCount() == 0) {
				falsePositiveCount++;
			}
		}
		return new ReturnedCandidateValidationResult(result.candidates().size(), falsePositiveCount);
	}

	private static long usedMemoryBytes() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static String csv(String value) {
		if (value == null) {
			return "";
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static String json(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static double bytesToMb(long bytes) {
		return bytes / 1024.0 / 1024.0;
	}

	private static long pairCount(int size) {
		return size < 2 ? 0L : (long) size * (size - 1L) / 2L;
	}

	private static long elapsedMillis(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	private record Texas7kStudySet(
			int totalContingencyCount,
			int totalMonitorCount,
			List<String> outageCandidateBranchIds,
			List<String> monitoredBranchIds) {
	}

	private record PairKey(String branchId1, String branchId2) {
		static PairKey of(String branchId1, String branchId2) {
			return branchId1.compareTo(branchId2) <= 0
					? new PairKey(branchId1, branchId2)
					: new PairKey(branchId2, branchId1);
		}
	}

	private record RankColumns(EnumMap<FastN2RankingMode, Map<PairKey, Integer>> ranks) {
		int rank(FastN2RankingMode mode, FastN2CandidatePair candidate) {
			return ranks.get(mode).get(PairKey.of(candidate.outageBranchId1(), candidate.outageBranchId2()));
		}
	}

	private enum RiskBucket {
		LOW,
		MEDIUM,
		HIGH
	}

	private record SampledPair(
			PairKey pair,
			int outageIndex1,
			int outageIndex2,
			RiskBucket bucket,
			double pairBaseFlowRiskMw,
			double pairPruningBound,
			int pairPruningIteration) {
	}

	private record PrunedAwayPairDiagnostic(
			PairKey pair,
			RiskBucket bucket,
			double pairBaseFlowRiskMw,
			boolean dangerous,
			String boundingMonitorBranchId,
			int violationCount,
			double upperBoundLoadingPercent,
			double totalOverloadMw,
			double totalNormalizedOverload,
			double maxOverloadPercent,
			double maxOverloadMw,
			double severityScore,
			double pairPruningBound,
			int pairPruningIteration) {

		static PrunedAwayPairDiagnostic of(SampledPair sampledPair, FastN2CandidatePair candidate) {
			if (candidate == null) {
				return new PrunedAwayPairDiagnostic(
						sampledPair.pair(),
						sampledPair.bucket(),
						sampledPair.pairBaseFlowRiskMw(),
						false,
						null,
						0,
						0.0,
						0.0,
						0.0,
						0.0,
						0.0,
						0.0,
						sampledPair.pairPruningBound(),
						sampledPair.pairPruningIteration());
			}
			return new PrunedAwayPairDiagnostic(
					sampledPair.pair(),
					sampledPair.bucket(),
					sampledPair.pairBaseFlowRiskMw(),
					true,
					candidate.boundingMonitorBranchId(),
					candidate.violationCount(),
					candidate.upperBoundLoadingPercent(),
					candidate.totalOverloadMw(),
					candidate.totalNormalizedOverload(),
					candidate.maxOverloadPercent(),
					candidate.maxOverloadMw(),
					candidate.severityScore(),
					sampledPair.pairPruningBound(),
					sampledPair.pairPruningIteration());
		}
	}

	private record SampleValidationResult(
			List<PrunedAwayPairDiagnostic> diagnostics,
			long elapsedMillis) {

		int sampledPrunedAwayPairCount() {
			return diagnostics.size();
		}

		int dangerousPairCount() {
			return (int) diagnostics.stream().filter(PrunedAwayPairDiagnostic::dangerous).count();
		}

		double worstMissedLoadingPercent() {
			return diagnostics.stream()
					.filter(PrunedAwayPairDiagnostic::dangerous)
					.mapToDouble(PrunedAwayPairDiagnostic::upperBoundLoadingPercent)
					.max()
					.orElse(0.0);
		}

		double worstMissedSeverityScore() {
			return diagnostics.stream()
					.filter(PrunedAwayPairDiagnostic::dangerous)
					.mapToDouble(PrunedAwayPairDiagnostic::severityScore)
					.max()
					.orElse(0.0);
		}

		int sampledCount(RiskBucket bucket) {
			return (int) diagnostics.stream().filter(diagnostic -> diagnostic.bucket() == bucket).count();
		}

		int dangerousCount(RiskBucket bucket) {
			return (int) diagnostics.stream()
					.filter(diagnostic -> diagnostic.bucket() == bucket && diagnostic.dangerous())
					.count();
		}
	}

	private record ReturnedCandidateValidationResult(
			int returnedCandidateCount,
			int falsePositiveCount) {

		String status() {
			return falsePositiveCount == 0 ? "PASS" : "FAILED - returned candidates without exact violations";
		}
	}

	private record SurvivorValidationResult(
			long survivorPairCount,
			long exactEvaluatedPairCount,
			long dangerousPairCount,
			boolean selectorEvaluatedAllSurvivors) {
	}

	private record Activs25kInventoryReport(
			int busCount,
			int branchCount,
			int contingencyJsonCount,
			int monitorJsonCount,
			FastN2StudyInventory inventory) {

		String toText() {
			return """
					Fast N-2 ACTIVSg25k inventory
					=============================
					Input files:
					- RAW: %s
					- contingencies: %s
					- monitors: %s

					Network:
					- buses: %d
					- branches: %d
					- active branches: %d
					- active branches missing RateB: %d

					Outage candidates:
					- contingency records in JSON: %d
					- distinct requested outage IDs: %d
					- active requested outage branches: %d
					- topology-filter removals: %d
					- final outage candidates: %d
					- raw N-2 pair count before topology filter: %d
					- final N-2 pair count after topology filter: %d

					Monitored branches:
					- monitor records in JSON: %d
					- distinct requested monitor IDs: %d
					- active requested monitor branches: %d
					- active requested monitors missing RateB: %d
					- final monitored branches with usable rating: %d
					""".formatted(
					ACTIVS25K_RAW,
					ACTIVS25K_CONTINGENCIES,
					ACTIVS25K_MONITORS,
					busCount,
					branchCount,
					inventory.activeBranchCount(),
					inventory.activeBranchMissingRatingCount(),
					contingencyJsonCount,
					inventory.requestedOutageCount(),
					inventory.activeRequestedOutageCount(),
					inventory.topologyFilteredOutageCount(),
					inventory.finalOutageCandidateCount(),
					inventory.rawPairCount(),
					inventory.finalCandidatePairCount(),
					monitorJsonCount,
					inventory.requestedMonitorCount(),
					inventory.activeRequestedMonitorCount(),
					inventory.activeRequestedMonitorMissingRatingCount(),
					inventory.monitoredBranchCount());
		}
	}

	private record Texas7kFullSetPrunerCheckpoint(
			int totalContingencyCount,
			int totalMonitorCount,
			int outageCandidateCount,
			int monitoredBranchCount,
			long candidatePairCount,
			int fallbackRatingCount,
			long survivorPairCount,
			long prunedPairCount,
			long islandingPairCount,
			long prunerElapsedMillis,
			long memoryBeforeBytes,
			long memoryAfterPrunerBytes) {

		String toText() {
			double pruningRatio = candidatePairCount > 0L
					? 100.0 * (double) prunedPairCount / (double) candidatePairCount
					: 0.0;
			return """
					Fast N-2 Texas 7k full-set pruner checkpoint
					============================================
					Input files:
					- RAW: %s
					- contingencies: %s
					- monitors: %s

					Study scope:
					- total filtered contingencies in JSON: %d
					- total monitored branches in JSON: %d
					- outage candidates used: %d
					- monitored branches used: %d
					- candidate pairs: %d
					- fallback ratings assigned for missing RateB: %d

					Upper-bound pruning:
					- survivor pairs: %d
					- pruned-away pairs: %d
					- pruning ratio: %.2f%%
					- islanding/near-singular pairs: %d
					- pruner elapsed: %d ms

					Memory:
					- before: %.2f MB
					- after pruning: %.2f MB
					""".formatted(
					TEXAS7K_RAW,
					TEXAS7K_CONTINGENCIES,
					TEXAS7K_MONITORS,
					totalContingencyCount,
					totalMonitorCount,
					outageCandidateCount,
					monitoredBranchCount,
					candidatePairCount,
					fallbackRatingCount,
					survivorPairCount,
					prunedPairCount,
					pruningRatio,
					islandingPairCount,
					prunerElapsedMillis,
					bytesToMb(memoryBeforeBytes),
					bytesToMb(memoryAfterPrunerBytes));
		}
	}

	private record Texas7kFullSetReport(
			int totalContingencyCount,
			int totalMonitorCount,
			int outageCandidateCount,
			int monitoredBranchCount,
			long candidatePairCount,
			int fallbackRatingCount,
			long survivorPairCount,
			long prunedPairCount,
			long islandingPairCount,
			long prunerElapsedMillis,
			long dangerousPairCount,
			long returnedCandidateCount,
			boolean truncatedByMaxCandidatePairs,
			int maxReturnedCandidatePairs,
			int exactEvaluationChunkSize,
			long exactEvaluatedPairCount,
			long selectorPrunedPairCount,
			long selectorElapsedMillis,
			FastN2LodfStats selectorLodfStats,
			FastN2MonitorScreeningStats monitorScreeningStats,
			String top20RankingSummary,
			SampleValidationResult sampleValidation,
			ReturnedCandidateValidationResult returnedValidation,
			SurvivorValidationResult survivorValidation,
			long memoryBeforeBytes,
			long memoryAfterPrunerBytes,
			long memoryAfterSelectorBytes) {

		String toText() {
			double pruningRatio = candidatePairCount > 0L
					? 100.0 * (double) prunedPairCount / (double) candidatePairCount
					: 0.0;
			return """
					Fast N-2 Texas 7k full-set pruned report
					========================================
					Input files:
					- RAW: %s
					- contingencies: %s
					- monitors: %s

					Study scope:
					- total filtered contingencies in JSON: %d
					- total monitored branches in JSON: %d
					- outage candidates used: %d
					- monitored branches used: %d
					- candidate pairs: %d
					- fallback ratings assigned for missing RateB: %d

					Upper-bound pruning:
					- survivor pairs: %d
					- pruned-away pairs: %d
					- pruning ratio: %.2f%%
					- islanding/near-singular pairs: %d
					- pruner elapsed: %d ms

					Exact survivor evaluation:
					- dangerous pairs: %d
					- returned/exported candidate pairs: %d
					- top-K candidate cap: %s
					- truncated by top-K cap: %s
					- exact evaluation chunk size: %d outage rows
					- exact evaluation threads: %d
					- exact monitor pruning enabled: %s
					- exact evaluated pairs: %d
					- selector pruned-away pairs: %d
					- selector elapsed: %d ms
					- selector evaluated all pruner survivors: %s
					- LODF kernel elapsed: %d ms
					- monitor/outage LODFs computed: %d
					- outage/outage LODFs computed: %d
					- outage LODF vectors computed: %d

					Monitor-side exact evaluation diagnostics:
					- total monitor visits: %d
					- exact monitor evaluations: %d
					- pruned monitor evaluations: %d
					- monitor evaluations at or above 90%% loading: %d (%.4f%%)
					- monitor evaluations at or above 95%% loading: %d (%.4f%%)
					- monitor evaluations at or above 98%% loading: %d (%.4f%%)
					- violating monitor evaluations: %d (%.4f%%)
					- exact evaluated pairs with violations: %d
					- distinct violating monitors: %d
					- max violations on one outage pair: %d
					- max loading percent: %.6f
					- estimated monitor evaluation reduction if only >=90%% monitors need exact scoring: %.2f%%
					- estimated monitor evaluation reduction if only >=95%% monitors need exact scoring: %.2f%%
					- estimated monitor evaluation reduction if only >=98%% monitors need exact scoring: %.2f%%

					Top 20 Returned Candidate Ranking Matrix:
					%s

					Validation sampling:
					- pruning safety classification: %s
					- sampled pruned-away pairs: %d
					- sampled pruned-away pairs by risk bucket: low=%d, medium=%d, high=%d
					- exact dangerous pairs in sample: %d
					- exact dangerous pairs by risk bucket: low=%d, medium=%d, high=%d
					- worst missed loading percent: %.6f
					- worst missed severity score: %.6f
					- pruning validation status: %s
					- sample validation elapsed: %d ms

					Returned top-K validation:
					- returned candidate pairs checked: %d
					- returned false positives: %d
					- returned validation status: %s

					Memory:
					- before: %.2f MB
					- after pruning: %.2f MB
					- after selector: %.2f MB

					Exports:
					- target/fast-n2-texas7k-dangerous-pairs.csv
					- target/fast-n2-texas7k-dangerous-pairs.json
					- target/fast-n2-texas7k-pruned-away-diagnostics.csv
					- target/fast-n2-texas7k-planning-review.md
					""".formatted(
					TEXAS7K_RAW,
					TEXAS7K_CONTINGENCIES,
					TEXAS7K_MONITORS,
					totalContingencyCount,
					totalMonitorCount,
					outageCandidateCount,
					monitoredBranchCount,
					candidatePairCount,
					fallbackRatingCount,
					survivorPairCount,
					prunedPairCount,
					pruningRatio,
					islandingPairCount,
					prunerElapsedMillis,
					dangerousPairCount,
					returnedCandidateCount,
					maxReturnedCandidatePairs > 0 ? Integer.toString(maxReturnedCandidatePairs) : "unbounded",
					truncatedByMaxCandidatePairs,
					exactEvaluationChunkSize,
					FULL_SET_EXACT_EVALUATION_THREADS,
					FULL_SET_EXACT_MONITOR_PRUNING,
					exactEvaluatedPairCount,
					selectorPrunedPairCount,
					selectorElapsedMillis,
					survivorValidation.selectorEvaluatedAllSurvivors,
					selectorLodfStats.lineOutageElapsedMillis(),
					selectorLodfStats.monitorOutageComputedCount(),
					selectorLodfStats.outagePairComputedCount(),
					selectorLodfStats.outageVectorComputedCount(),
					monitorScreeningStats.totalMonitorVisitCount(),
					monitorScreeningStats.exactMonitorEvaluationCount(),
					monitorScreeningStats.prunedMonitorEvaluationCount(),
					monitorScreeningStats.near90PercentEvaluationCount(),
					100.0 * monitorScreeningStats.near90PercentEvaluationRatio(),
					monitorScreeningStats.near95PercentEvaluationCount(),
					100.0 * monitorScreeningStats.near95PercentEvaluationRatio(),
					monitorScreeningStats.near98PercentEvaluationCount(),
					100.0 * monitorScreeningStats.near98PercentEvaluationRatio(),
					monitorScreeningStats.violatingMonitorEvaluationCount(),
					100.0 * monitorScreeningStats.violatingMonitorEvaluationRatio(),
					monitorScreeningStats.exactEvaluatedPairsWithViolations(),
					monitorScreeningStats.distinctViolatingMonitorCount(),
					monitorScreeningStats.maxViolationCountPerPair(),
					monitorScreeningStats.maxLoadingPercent(),
					100.0 * monitorScreeningStats.monitorEvaluationReductionIfOnlyNear90Percent(),
					100.0 * monitorScreeningStats.monitorEvaluationReductionIfOnlyNear95Percent(),
					100.0 * monitorScreeningStats.monitorEvaluationReductionIfOnlyNear98Percent(),
					top20RankingSummary,
					sampleValidation.dangerousPairCount() == 0
							? "HEURISTIC_SAMPLE_CLEAN_NOT_CERTIFIED"
							: "HEURISTIC_UNSAFE_SAMPLE_MISSES",
					sampleValidation.sampledPrunedAwayPairCount(),
					sampleValidation.sampledCount(RiskBucket.LOW),
					sampleValidation.sampledCount(RiskBucket.MEDIUM),
					sampleValidation.sampledCount(RiskBucket.HIGH),
					sampleValidation.dangerousPairCount(),
					sampleValidation.dangerousCount(RiskBucket.LOW),
					sampleValidation.dangerousCount(RiskBucket.MEDIUM),
					sampleValidation.dangerousCount(RiskBucket.HIGH),
					sampleValidation.worstMissedLoadingPercent(),
					sampleValidation.worstMissedSeverityScore(),
					sampleValidation.dangerousPairCount() == 0 ? "PASS" : "FAILED - pruned-away sample contains dangerous pairs",
					sampleValidation.elapsedMillis(),
					returnedValidation.returnedCandidateCount(),
					returnedValidation.falsePositiveCount(),
					returnedValidation.status(),
					bytesToMb(memoryBeforeBytes),
					bytesToMb(memoryAfterPrunerBytes),
					bytesToMb(memoryAfterSelectorBytes));
		}

	}

	private record Texas7kReport(
			int totalContingencyCount,
			int totalMonitorCount,
			int outageCandidateCount,
			int monitoredBranchCount,
			long candidatePairCount,
			int fallbackRatingCount,
			long baselineDangerousPairCount,
			long baselineExactEvaluatedPairCount,
			long baselineElapsedMillis,
			FastN2LodfStats baselineLodfStats,
			long prunedDangerousPairCount,
			long prunedExactEvaluatedPairCount,
			long prunedSkippedPairCount,
			long prunedElapsedMillis,
			FastN2LodfStats prunedLodfStats) {

		String toText() {
			double exactEvaluationReduction = candidatePairCount > 0L
					? 100.0 * (double) prunedSkippedPairCount / (double) candidatePairCount
					: 0.0;
			double speedup = prunedElapsedMillis > 0L
					? (double) baselineElapsedMillis / (double) prunedElapsedMillis
					: Double.POSITIVE_INFINITY;
			return """
					Fast N-2 Texas 7k performance report
					====================================
					Input files:
					- RAW: %s
					- contingencies: %s
					- monitors: %s

					Study scope:
					- total filtered contingencies in JSON: %d
					- total monitored branches in JSON: %d
					- outage candidate cap: %d
					- monitored branch cap: %d
					- outage candidates used: %d
					- monitored branches used: %d
					- candidate pairs: %d
					- fallback ratings assigned for missing RateB: %d

					Baseline exhaustive selector:
					- dangerous pairs: %d
					- exact evaluated pairs: %d
					- elapsed: %d ms
					- LODF kernel elapsed: %d ms
					- monitor/outage LODFs computed: %d
					- outage/outage LODFs computed: %d
					- outage/outage rows reused from monitor matrix: %d
					- outage LODF vectors computed: %d
					- LODF cache hits: monitor/outage=%d, outage/outage=%d

					Upper-bound pruning prescreen selector:
					- dangerous pairs: %d
					- exact evaluated pairs: %d
					- upper-bound pruned pairs: %d
					- exact pair evaluation reduction: %.2f%%
					- elapsed: %d ms
					- measured selector speedup: %.2fx
					- LODF kernel elapsed: %d ms
					- monitor/outage LODFs computed: %d
					- outage/outage LODFs computed: %d
					- outage/outage rows reused from monitor matrix: %d
					- outage LODF vectors computed: %d
					- LODF cache hits: monitor/outage=%d, outage/outage=%d

					Correctness:
					- upper-bound pruning dangerous pair set matched baseline exactly
					""".formatted(
					TEXAS7K_RAW,
					TEXAS7K_CONTINGENCIES,
					TEXAS7K_MONITORS,
					totalContingencyCount,
					totalMonitorCount,
					OUTAGE_CANDIDATE_LIMIT,
					MONITORED_BRANCH_LIMIT,
					outageCandidateCount,
					monitoredBranchCount,
					candidatePairCount,
					fallbackRatingCount,
					baselineDangerousPairCount,
					baselineExactEvaluatedPairCount,
					baselineElapsedMillis,
					baselineLodfStats.lineOutageElapsedMillis(),
					baselineLodfStats.monitorOutageComputedCount(),
					baselineLodfStats.outagePairComputedCount(),
					baselineLodfStats.outagePairMonitorReuseCount(),
					baselineLodfStats.outageVectorComputedCount(),
					baselineLodfStats.monitorOutageCacheHitCount(),
					baselineLodfStats.outagePairCacheHitCount(),
					prunedDangerousPairCount,
					prunedExactEvaluatedPairCount,
					prunedSkippedPairCount,
					exactEvaluationReduction,
					prunedElapsedMillis,
					speedup,
					prunedLodfStats.lineOutageElapsedMillis(),
					prunedLodfStats.monitorOutageComputedCount(),
					prunedLodfStats.outagePairComputedCount(),
					prunedLodfStats.outagePairMonitorReuseCount(),
					prunedLodfStats.outageVectorComputedCount(),
					prunedLodfStats.monitorOutageCacheHitCount(),
					prunedLodfStats.outagePairCacheHitCount());
		}
	}
}
