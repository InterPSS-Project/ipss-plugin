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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
import com.interpss.core.algo.dclf.fastn2.FastN2Pruner;
import com.interpss.core.algo.dclf.fastn2.FastN2PruningResult;
import com.interpss.core.algo.dclf.fastn2.FastN2ScreeningOptions;

@Tag("large")
public class FastN2CandidateSelectorTexas7kTest extends CorePluginTestSetup {

	private static final Path TEXAS7K_DIR = resolveTexas7kDir();
	private static final Path TEXAS7K_RAW = TEXAS7K_DIR.resolve("Texas7k_20210804.RAW");
	private static final Path TEXAS7K_CONTINGENCIES = TEXAS7K_DIR.resolve("Texas7k_20210804_filtered_contingencies.json");
	private static final Path TEXAS7K_MONITORS = TEXAS7K_DIR.resolve("Texas7k_20210804_monitored_branches.json");
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
		FastN2PruningResult pruning = new FastN2Pruner().prune(prunerRequest);
		long prunerElapsedMillis = elapsedMillis(prunerStartedNanos);
		long memoryAfterPrunerBytes = usedMemoryBytes();
		long survivorPairs = pruning.finalPairCount();
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

		List<PairKey> sampledPrunedAwayPairs = sampledPrunedAwayPairs(pruning, FULL_SET_VALIDATION_SAMPLE_SIZE);
		SampleValidationResult sampleValidation = validateExactPairSamples(
				studySet,
				sampledPrunedAwayPairs);
		SurvivorValidationResult survivorValidation = new SurvivorValidationResult(
				survivorPairs,
				pruned.stats().exactEvaluatedPairCount(),
				exactDangerousPairCount,
				pruned.stats().exactEvaluatedPairCount() == survivorPairs);

		writeDangerousPairCsv(pruned);
		writeDangerousPairJson(pruned);
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
				sampleValidation,
				survivorValidation,
				memoryBeforeBytes,
				memoryAfterPrunerBytes,
				memoryAfterSelectorBytes));
		if (FAIL_ON_SAMPLE_DANGEROUS) {
			assertEquals(0, sampleValidation.dangerousPairCount(),
					"Sampled pruned-away pairs should not contain exact dangerous pairs");
		}
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
		return new FastN2ScreeningOptions(
				THERMAL_LIMIT_PERCENT,
				0.0,
				1.0e-8,
				FULL_SET_MAX_RETURNED_CANDIDATES,
				false,
				true,
				false,
				FastN2ScreeningOptions.DEFAULT_MINIMUM_RISK_GRAPH_SCORE,
				FastN2ScreeningOptions.DEFAULT_MINIMUM_OUTAGE_INTERACTION_LODF,
				FULL_SET_EXACT_EVALUATION_CHUNK_SIZE);
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

	private static void writeDangerousPairCsv(FastN2CandidateResult result) throws Exception {
		Path csvPath = Path.of("target", "fast-n2-texas7k-dangerous-pairs.csv");
		Files.createDirectories(csvPath.getParent());
		StringBuilder csv = new StringBuilder();
		csv.append("outageBranchId1,outageBranchId2,boundingMonitorBranchId,violationCount,")
				.append("upperBoundLoadingPercent,totalOverloadMw,totalNormalizedOverload,maxOverloadPercent,")
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
						.append(candidate.singularOrIslandingRisk()).append('\n'));
		Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);
	}

	private static void writeDangerousPairJson(FastN2CandidateResult result) throws Exception {
		Path jsonPath = Path.of("target", "fast-n2-texas7k-dangerous-pairs.json");
		Files.createDirectories(jsonPath.getParent());
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
		return Comparator.comparingDouble(FastN2CandidatePair::totalNormalizedOverload).reversed()
				.thenComparing(Comparator.comparingDouble(FastN2CandidatePair::upperBoundLoadingPercent).reversed())
				.thenComparing(FastN2CandidatePair::outageBranchId1)
				.thenComparing(FastN2CandidatePair::outageBranchId2);
	}

	private static List<PairKey> sampledPrunedAwayPairs(
			FastN2PruningResult pruning,
			int sampleSize) {
		if (sampleSize <= 0) {
			return List.of();
		}
		long prunedPairCount = pruning.originalPairCount() - pruning.finalPairCount();
		if (prunedPairCount <= 0L) {
			return List.of();
		}
		List<PairKey> samples = new ArrayList<>();
		long stride = Math.max(1L, prunedPairCount / sampleSize);
		long nextOrdinal = 0L;
		long prunedOrdinal = 0L;
		boolean[][] mask = pruning.survivorPairMask();
		for (int x = 0; x < mask.length && samples.size() < sampleSize; x++) {
			for (int y = x + 1; y < mask[x].length && samples.size() < sampleSize; y++) {
				if (mask[x][y]) {
					continue;
				}
				if (prunedOrdinal >= nextOrdinal) {
					samples.add(PairKey.of(pruning.outageBranchIds().get(x), pruning.outageBranchIds().get(y)));
					nextOrdinal += stride;
				}
				prunedOrdinal++;
			}
		}
		return samples;
	}

	private static SampleValidationResult validateExactPairSamples(
			Texas7kStudySet studySet,
			List<PairKey> sampledPairs) throws Exception {
		int dangerous = 0;
		long startedNanos = System.nanoTime();
		for (PairKey pair : sampledPairs) {
			AclfNetwork net = importPsse(TEXAS7K_RAW);
			fillMissingRatingsFromBaseDclfFlow(net, FALLBACK_RATING_BASE_FLOW_MULTIPLIER);
			FastN2CandidateResult result = new FastN2CandidateSelector().selectCandidates(
					new FastN2CandidateRequest(
							net,
							DclfMethod.STD,
							studySet.monitoredBranchIds(),
							List.of(pair.branchId1(), pair.branchId2()),
							new FastN2ScreeningOptions(THERMAL_LIMIT_PERCENT, 0.0, 1.0e-8, 0, false)));
			if (!result.candidates().isEmpty()) {
				dangerous++;
			}
		}
		return new SampleValidationResult(sampledPairs.size(), dangerous, elapsedMillis(startedNanos));
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

	private record SampleValidationResult(
			int sampledPrunedAwayPairCount,
			int dangerousPairCount,
			long elapsedMillis) {
	}

	private record SurvivorValidationResult(
			long survivorPairCount,
			long exactEvaluatedPairCount,
			long dangerousPairCount,
			boolean selectorEvaluatedAllSurvivors) {
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
			SampleValidationResult sampleValidation,
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
					- exact evaluated pairs: %d
					- selector pruned-away pairs: %d
					- selector elapsed: %d ms
					- selector evaluated all pruner survivors: %s
					- LODF kernel elapsed: %d ms
					- monitor/outage LODFs computed: %d
					- outage/outage LODFs computed: %d
					- outage LODF vectors computed: %d

					Validation sampling:
					- sampled pruned-away pairs: %d
					- exact dangerous pairs in sample: %d
					- pruning validation status: %s
					- sample validation elapsed: %d ms

					Memory:
					- before: %.2f MB
					- after pruning: %.2f MB
					- after selector: %.2f MB

					Exports:
					- target/fast-n2-texas7k-dangerous-pairs.csv
					- target/fast-n2-texas7k-dangerous-pairs.json
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
					exactEvaluatedPairCount,
					selectorPrunedPairCount,
					selectorElapsedMillis,
					survivorValidation.selectorEvaluatedAllSurvivors,
					selectorLodfStats.lineOutageElapsedMillis(),
					selectorLodfStats.monitorOutageComputedCount(),
					selectorLodfStats.outagePairComputedCount(),
					selectorLodfStats.outageVectorComputedCount(),
					sampleValidation.sampledPrunedAwayPairCount,
					sampleValidation.dangerousPairCount,
					sampleValidation.dangerousPairCount == 0 ? "PASS" : "FAILED - pruned-away sample contains dangerous pairs",
					sampleValidation.elapsedMillis,
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
