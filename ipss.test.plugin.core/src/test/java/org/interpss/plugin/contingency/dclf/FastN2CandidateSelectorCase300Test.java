package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.fastn2.FastN2CandidatePair;
import com.interpss.core.algo.dclf.fastn2.FastN2CandidateRequest;
import com.interpss.core.algo.dclf.fastn2.FastN2CandidateResult;
import com.interpss.core.algo.dclf.fastn2.FastN2CandidateSelector;
import com.interpss.core.algo.dclf.fastn2.FastN2Pruner;
import com.interpss.core.algo.dclf.fastn2.FastN2PruningResult;
import com.interpss.core.algo.dclf.fastn2.FastN2ScreeningOptions;
import com.interpss.core.algo.dclf.fastn2.N2ViolationEstimate;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class FastN2CandidateSelectorCase300Test extends CorePluginTestSetup {

	private static final String CASE300_FILE = "testData/adpter/matpower/case300.m";
	private static final double POST_FLOW_TOLERANCE_MW = 1.0e-5;
	private static final double THERMAL_LIMIT_PERCENT = 100.0;
	private static final double PERFORMANCE_RATING_BASE_FLOW_MULTIPLIER = 1.10;
	private static final String CASE300_REFERENCE_README =
			"pekap/N_2_contingency_analysis README case300: fast 1 candidate from 51,558 potential pairs, "
					+ "0.07487977 sec; brute force 51,842 pairs, 14.20059 sec.";
	private static final List<String> CASE300_OUTAGE_CANDIDATES = List.of(
			"Bus137->Bus140(1)",
			"Bus3->Bus4(1)",
			"Bus1->Bus5(1)",
			"Bus2->Bus6(1)",
			"Bus5->Bus9(1)");

	@Test
	public void selectedCase300PairsMatchExistingMultiOpenAnalysis() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE300_FILE);
		assignScreeningRatingsFromBaseDclfFlow(net);

		FastN2CandidateResult result = new FastN2CandidateSelector().selectCandidates(
				new FastN2CandidateRequest(
						net,
						DclfMethod.STD,
						List.of(),
						CASE300_OUTAGE_CANDIDATES,
						new FastN2ScreeningOptions(100.0, 0.0, 1.0e-8, 5, false)));

		assertTrue(result.stats().monitoredBranchCount() > 0);
		assertEquals(CASE300_OUTAGE_CANDIDATES.size(), result.stats().outageCandidateCount());
		assertFalse(result.candidates().isEmpty());
		assertTrue(result.stats().truncatedByMaxCandidatePairs());

		for (FastN2CandidatePair candidate : result.candidates()) {
			assertFalse(candidate.singularOrIslandingRisk());
			assertFalse(candidate.violations().isEmpty());
			assertCandidateMatchesExistingMultiOpenAnalysis(net, candidate);
		}
	}

	@Test
	public void case300FastSelectorMatchesBruteForceOnBenchmarkSubset() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE300_FILE);
		assignScreeningRatingsFromBaseDclfFlow(net);

		List<String> outageCandidateIds = CASE300_OUTAGE_CANDIDATES;
		Set<String> outageCandidateIdSet = new HashSet<>(outageCandidateIds);
		List<String> monitoredBranchIds = net.getBranchList().stream()
				.filter(AclfBranch::isActive)
				.map(AclfBranch::getId)
				.filter(branchId -> !outageCandidateIdSet.contains(branchId))
				.toList();

		long fastStartedNanos = System.nanoTime();
		FastN2CandidateRequest request = new FastN2CandidateRequest(
				net,
				DclfMethod.STD,
				monitoredBranchIds,
				outageCandidateIds,
				new FastN2ScreeningOptions(THERMAL_LIMIT_PERCENT, 0.0, 1.0e-8, 0, false));
		FastN2PruningResult pruningResult = new FastN2Pruner().prune(request);
		FastN2CandidateResult fastResult = new FastN2CandidateSelector().selectCandidates(
				request);
		long fastElapsedMillis = elapsedMillis(fastStartedNanos);

		long bruteStartedNanos = System.nanoTime();
		Map<PairKey, BruteForcePairResult> bruteForceResults =
				bruteForceN2Analysis(net, monitoredBranchIds, outageCandidateIds, THERMAL_LIMIT_PERCENT);
		long bruteElapsedMillis = elapsedMillis(bruteStartedNanos);

		Map<PairKey, FastN2CandidatePair> fastPairs = fastResult.candidates().stream()
				.collect(Collectors.toMap(
						candidate -> PairKey.of(candidate.outageBranchId1(), candidate.outageBranchId2()),
						candidate -> candidate));

		assertEquals(bruteForceResults.keySet(), fastPairs.keySet(), "Fast selector should match brute-force dangerous pairs");
		for (Map.Entry<PairKey, BruteForcePairResult> entry : bruteForceResults.entrySet()) {
			FastN2CandidatePair fastPair = fastPairs.get(entry.getKey());
			BruteForcePairResult brutePair = entry.getValue();
			assertEquals(brutePair.violationCount(), fastPair.violationCount(), "Violation count mismatch for " + entry.getKey());
			assertEquals(brutePair.upperBoundLoadingPercent(), fastPair.upperBoundLoadingPercent(), 1.0e-7,
					"Upper-bound loading mismatch for " + entry.getKey());
			assertEquals(brutePair.totalNormalizedOverload(), fastPair.totalNormalizedOverload(), 1.0e-7,
					"Total normalized overload mismatch for " + entry.getKey());
		}

		writeComparisonReport(new Case300ComparisonReport(
				outageCandidateIds.size(),
				monitoredBranchIds.size(),
				pairCount(outageCandidateIds.size()),
				pruningResult.finalPairCount(),
				pruningResult.elapsedMillis(),
				pruningResult.stages().stream()
						.map(stage -> String.format("  iteration %d: potential=%d, B=%d, islanding=%d",
								stage.iteration(),
								stage.potentialPairCount(),
								stage.boundEntryCount(),
								stage.islandingPairCount()))
						.collect(Collectors.joining(System.lineSeparator())),
				fastResult.candidates().size(),
				fastResult.stats().prunedPairCount(),
				fastElapsedMillis,
				bruteForceResults.size(),
				bruteElapsedMillis));

		assertTrue(pruningResult.finalPairCount() <= pruningResult.originalPairCount());
	}

	@Test
	public void case300FullInterpssPruningReport() throws Exception {
		AclfNetwork net = loadMatpowerCase(CASE300_FILE);
		assignScreeningRatingsFromBaseDclfFlow(net);

		FastN2PruningResult pruningResult = new FastN2Pruner().prune(
				new FastN2CandidateRequest(
						net,
						DclfMethod.STD,
						List.of(),
						List.of(),
						new FastN2ScreeningOptions(THERMAL_LIMIT_PERCENT, 0.0, 1.0e-8, 0, false)));

		writeFullPruningReport(pruningResult);
		assertTrue(pruningResult.originalPairCount() > 0);
		assertTrue(pruningResult.finalPairCount() <= pruningResult.originalPairCount());
	}

	@Test
	public void case300RiskGraphPrescreenPerformanceReport() throws Exception {
		AclfNetwork baselineNet = loadMatpowerCase(CASE300_FILE);
		assignScreeningRatingsFromBaseDclfFlow(baselineNet, PERFORMANCE_RATING_BASE_FLOW_MULTIPLIER);
		FastN2CandidateRequest baselineRequest = new FastN2CandidateRequest(
				baselineNet,
				DclfMethod.STD,
				List.of(),
				List.of(),
				new FastN2ScreeningOptions(THERMAL_LIMIT_PERCENT, 0.0, 1.0e-8, 0, false));

		long baselineStartedNanos = System.nanoTime();
		FastN2CandidateResult baseline = new FastN2CandidateSelector().selectCandidates(baselineRequest);
		long baselineElapsedMillis = elapsedMillis(baselineStartedNanos);

		AclfNetwork prunedNet = loadMatpowerCase(CASE300_FILE);
		assignScreeningRatingsFromBaseDclfFlow(prunedNet, PERFORMANCE_RATING_BASE_FLOW_MULTIPLIER);
		FastN2ScreeningOptions prunedOptions = new FastN2ScreeningOptions(
				THERMAL_LIMIT_PERCENT,
				0.0,
				1.0e-8,
				0,
				false,
				true,
				false,
				FastN2ScreeningOptions.DEFAULT_MINIMUM_RISK_GRAPH_SCORE,
				FastN2ScreeningOptions.DEFAULT_MINIMUM_OUTAGE_INTERACTION_LODF);
		FastN2CandidateRequest prunedRequest = new FastN2CandidateRequest(
				prunedNet,
				DclfMethod.STD,
				List.of(),
				List.of(),
				prunedOptions);

		long prunedStartedNanos = System.nanoTime();
		FastN2CandidateResult pruned = new FastN2CandidateSelector().selectCandidates(prunedRequest);
		long prunedElapsedMillis = elapsedMillis(prunedStartedNanos);

		AclfNetwork riskGraphNet = loadMatpowerCase(CASE300_FILE);
		assignScreeningRatingsFromBaseDclfFlow(riskGraphNet, PERFORMANCE_RATING_BASE_FLOW_MULTIPLIER);
		FastN2ScreeningOptions riskGraphOptions = new FastN2ScreeningOptions(
				THERMAL_LIMIT_PERCENT,
				0.0,
				1.0e-8,
				0,
				false,
				false,
				true,
				0.001,
				0.001);
		FastN2CandidateRequest riskGraphRequest = new FastN2CandidateRequest(
				riskGraphNet,
				DclfMethod.STD,
				List.of(),
				List.of(),
				riskGraphOptions);

		long riskGraphStartedNanos = System.nanoTime();
		FastN2CandidateResult riskGraph = new FastN2CandidateSelector().selectCandidates(riskGraphRequest);
		long riskGraphElapsedMillis = elapsedMillis(riskGraphStartedNanos);

		Set<PairKey> baselineKeys = candidateKeys(baseline);
		Set<PairKey> prunedKeys = candidateKeys(pruned);
		Set<PairKey> prunedMissedPairs = new HashSet<>(baselineKeys);
		prunedMissedPairs.removeAll(prunedKeys);
		Set<PairKey> prunedExtraPairs = new HashSet<>(prunedKeys);
		prunedExtraPairs.removeAll(baselineKeys);
		Set<PairKey> riskGraphKeys = candidateKeys(riskGraph);
		Set<PairKey> missedPairs = new HashSet<>(baselineKeys);
		missedPairs.removeAll(riskGraphKeys);
		Set<PairKey> extraPairs = new HashSet<>(riskGraphKeys);
		extraPairs.removeAll(baselineKeys);
		assertEquals(baselineKeys, prunedKeys, "Upper-bound pruning should preserve baseline dangerous pairs");
		assertTrue(pruned.stats().upperBoundPruningSkippedPairCount() > 0L);
		assertTrue(pruned.stats().exactEvaluatedPairCount() < baseline.stats().exactEvaluatedPairCount());
		assertTrue(riskGraph.stats().riskGraphSkippedPairCount() > 0L);
		assertTrue(riskGraph.stats().exactEvaluatedPairCount() < baseline.stats().exactEvaluatedPairCount());

		writeRiskGraphPerformanceReport(new Case300RiskGraphReport(
				baseline.stats().outageCandidateCount(),
				baseline.stats().monitoredBranchCount(),
				baseline.stats().originalPairCount(),
				baseline.candidates().size(),
				baseline.stats().exactEvaluatedPairCount(),
				baseline.stats().riskGraphSkippedPairCount(),
				baselineElapsedMillis,
				pruned.candidates().size(),
				pruned.stats().exactEvaluatedPairCount(),
				pruned.stats().upperBoundPruningSkippedPairCount(),
				prunedElapsedMillis,
				riskGraph.candidates().size(),
				riskGraph.stats().exactEvaluatedPairCount(),
				riskGraph.stats().riskGraphSkippedPairCount(),
				riskGraphElapsedMillis,
				riskGraphOptions.minimumRiskGraphScore(),
				riskGraphOptions.minimumOutageInteractionLodf(),
				PERFORMANCE_RATING_BASE_FLOW_MULTIPLIER,
				prunedMissedPairs.size(),
				prunedExtraPairs.size(),
				missedPairs.size(),
				extraPairs.size()));
	}

	private static Set<PairKey> candidateKeys(FastN2CandidateResult result) {
		return result.candidates().stream()
				.map(candidate -> PairKey.of(candidate.outageBranchId1(), candidate.outageBranchId2()))
				.collect(Collectors.toSet());
	}

	private static AclfNetwork loadMatpowerCase(String path) throws Exception {
		return CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.MATPOWER)
				.load(path)
				.getAclfNet();
	}

	private static void assignScreeningRatingsFromBaseDclfFlow(AclfNetwork net) {
		assignScreeningRatingsFromBaseDclfFlow(net, 0.90);
	}

	private static void assignScreeningRatingsFromBaseDclfFlow(AclfNetwork net, double baseFlowMultiplier) {
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		assertTrue(dclfAlgo.calculateDclf(DclfMethod.STD));
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			AclfBranch branch = dclfBranch.getBranch();
			if (branch == null || !dclfBranch.isActive()) {
				continue;
			}
			double baseFlowMw = Math.abs(dclfBranch.getDclfFlow() * net.getBaseMva());
			branch.setRatingMva2(Math.max(1.0, baseFlowMw * baseFlowMultiplier));
		}
	}

	private static Map<PairKey, BruteForcePairResult> bruteForceN2Analysis(
			AclfNetwork net,
			List<String> monitoredBranchIds,
			List<String> outageCandidateIds,
			double thermalLimitPercent) throws Exception {
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		assertTrue(dclfAlgo.calculateDclf(DclfMethod.STD));

		Map<PairKey, BruteForcePairResult> dangerousPairs = new HashMap<>();
		for (int i = 0; i < outageCandidateIds.size(); i++) {
			for (int j = i + 1; j < outageCandidateIds.size(); j++) {
				String outageId1 = outageCandidateIds.get(i);
				String outageId2 = outageCandidateIds.get(j);
				DclfOutageBranch outage1 = outageBranch(dclfAlgo, outageId1);
				DclfOutageBranch outage2 = outageBranch(dclfAlgo, outageId2);
				dclfAlgo.multiOpenOutageAnalysis(new DclfOutageBranch[] {outage1, outage2});

				List<BruteForceViolation> violations = new ArrayList<>();
				double upperBoundLoadingPercent = 0.0;
				double totalNormalizedOverload = 0.0;
				for (String monitorBranchId : monitoredBranchIds) {
					DclfAlgoBranch monitor = dclfAlgo.getDclfAlgoBranch(monitorBranchId);
					AclfBranch branch = monitor.getBranch();
					double ratingMva = branch.getRatingMvaB();
					if (ratingMva <= 0.0) {
						continue;
					}
					double postFlowMw = monitor.calPostFlow() * net.getBaseMva();
					double loadingPercent = 100.0 * Math.abs(postFlowMw) / ratingMva;
					upperBoundLoadingPercent = Math.max(upperBoundLoadingPercent, loadingPercent);
					if (loadingPercent >= thermalLimitPercent) {
						double overloadMw = Math.max(0.0,
								Math.abs(postFlowMw) - ratingMva * thermalLimitPercent / 100.0);
						double normalizedOverload = overloadMw / ratingMva;
						totalNormalizedOverload += normalizedOverload;
						violations.add(new BruteForceViolation(monitorBranchId, postFlowMw, loadingPercent));
					}
				}
				if (!violations.isEmpty()) {
					dangerousPairs.put(PairKey.of(outageId1, outageId2),
							new BruteForcePairResult(upperBoundLoadingPercent, totalNormalizedOverload, violations));
				}
			}
		}
		return dangerousPairs;
	}

	private static void assertCandidateMatchesExistingMultiOpenAnalysis(
			AclfNetwork net,
			FastN2CandidatePair candidate) throws Exception {
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		assertTrue(dclfAlgo.calculateDclf(DclfMethod.STD));

		DclfOutageBranch outage1 = outageBranch(dclfAlgo, candidate.outageBranchId1());
		DclfOutageBranch outage2 = outageBranch(dclfAlgo, candidate.outageBranchId2());
		dclfAlgo.multiOpenOutageAnalysis(new DclfOutageBranch[] {outage1, outage2});

		for (N2ViolationEstimate violation : candidate.violations()) {
			DclfAlgoBranch monitor = dclfAlgo.getDclfAlgoBranch(violation.monitorBranchId());
			assertNotNull(monitor, "Missing monitor branch " + violation.monitorBranchId());
			double existingPostFlowMw = monitor.calPostFlow() * net.getBaseMva();
			assertEquals(existingPostFlowMw, violation.postFlowMw(), POST_FLOW_TOLERANCE_MW,
					"Post-flow mismatch for monitor " + violation.monitorBranchId()
							+ " under outages " + candidate.outageBranchId1()
							+ ", " + candidate.outageBranchId2());
		}

		N2ViolationEstimate boundingViolation = candidate.violations().stream()
				.max(Comparator.comparingDouble(N2ViolationEstimate::loadingPercent))
				.orElseThrow();
		assertEquals(boundingViolation.monitorBranchId(), candidate.boundingMonitorBranchId());
		assertEquals(boundingViolation.loadingPercent(), candidate.upperBoundLoadingPercent(), 1.0e-10);
	}

	private static DclfOutageBranch outageBranch(ContingencyAnalysisAlgorithm dclfAlgo, String branchId) {
		DclfAlgoBranch branch = dclfAlgo.getDclfAlgoBranch(branchId);
		assertNotNull(branch, "Missing outage branch " + branchId);
		DclfOutageBranch outage = createCaOutageBranch(branch, ContingencyBranchOutageType.OPEN);
		outage.setDclfFlow(branch.getDclfFlow());
		return outage;
	}

	private static void writeComparisonReport(Case300ComparisonReport report) throws Exception {
		Path reportPath = Path.of("target", "fast-n2-case300-comparison.txt");
		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, report.toText(), StandardCharsets.UTF_8);
	}

	private static void writeFullPruningReport(FastN2PruningResult pruningResult) throws Exception {
		Path reportPath = Path.of("target", "fast-n2-case300-full-pruning.txt");
		Files.createDirectories(reportPath.getParent());
		String stages = pruningResult.stages().stream()
				.map(stage -> String.format("  iteration %d: potential=%d, B=%d, islanding=%d",
						stage.iteration(),
						stage.potentialPairCount(),
						stage.boundEntryCount(),
						stage.islandingPairCount()))
				.collect(Collectors.joining(System.lineSeparator()));
		String text = """
				Fast N-2 case300 full InterPSS pruning report
				============================================
				Reference paper/repo headline:
				%s

				InterPSS raw imported universe:
				- MATPOWER file: %s
				- outage candidates: %d
				- monitored branches: %d
				- original pairs: %d
				- final survivor pairs: %d
				- islanding pairs: %d
				- pruning elapsed: %d ms

				Pruning stages:
				%s

				Note:
				The reference README count is after the upstream MATLAB workflow applies DC OPF,
				parallel-line removal, leaf aggregation, and other case reductions. This report
				uses the raw InterPSS MATPOWER import with test-only RateB values. The InterPSS
				core selector only removes zero-injection islanding stubs; leaf or islanding
				branches connected to load or generation remain outage candidates because their
				contingencies can shift transfer or require explicit remedial handling.
				""".formatted(
				CASE300_REFERENCE_README,
				CASE300_FILE,
				pruningResult.outageBranchIds().size(),
				pruningResult.monitoredBranchIds().size(),
				pruningResult.originalPairCount(),
				pruningResult.finalPairCount(),
				pruningResult.islandingPairCount(),
				pruningResult.elapsedMillis(),
				stages);
		Files.writeString(reportPath, text, StandardCharsets.UTF_8);
	}

	private static void writeRiskGraphPerformanceReport(Case300RiskGraphReport report) throws Exception {
		Path reportPath = Path.of("target", "fast-n2-case300-risk-graph-performance.txt");
		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, report.toText(), StandardCharsets.UTF_8);
	}

	private static long pairCount(int size) {
		return size < 2 ? 0L : (long) size * (size - 1L) / 2L;
	}

	private static long elapsedMillis(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	private record PairKey(String branchId1, String branchId2) {
		static PairKey of(String branchId1, String branchId2) {
			return branchId1.compareTo(branchId2) <= 0
					? new PairKey(branchId1, branchId2)
					: new PairKey(branchId2, branchId1);
		}
	}

	private record BruteForceViolation(String monitorBranchId, double postFlowMw, double loadingPercent) {
	}

	private record BruteForcePairResult(
			double upperBoundLoadingPercent,
			double totalNormalizedOverload,
			List<BruteForceViolation> violations) {

		int violationCount() {
			return violations.size();
		}
	}

	private record Case300ComparisonReport(
			int outageCandidateCount,
			int monitoredBranchCount,
			long originalPairCount,
			long pruningFinalPairCount,
			long pruningElapsedMillis,
			String pruningStages,
			long fastDangerousPairCount,
			long fastPrunedPairCount,
			long fastElapsedMillis,
			long bruteForceDangerousPairCount,
			long bruteForceElapsedMillis) {

		String toText() {
			double speedup = fastElapsedMillis > 0L
					? (double) bruteForceElapsedMillis / (double) fastElapsedMillis
					: Double.POSITIVE_INFINITY;
			return """
					Fast N-2 case300 InterPSS comparison
					====================================
					Reference paper/repo headline:
					%s

					InterPSS benchmark scope:
					- MATPOWER file: %s
					- outage candidates: %d
					- monitored branches: %d
					- candidate pairs: %d
					- ratings: test-only RateB values set to max(1.0, 0.90 * abs(base DCLF MW flow))
					- note: this is an InterPSS apples-to-apples fast-vs-brute comparison, not an exact reproduction of the reference repo preprocessing.

					Pruning:
					%s
					- final pruning survivor pairs: %d
					- pruning elapsed: %d ms

					Results:
					- fast selector dangerous pairs: %d
					- brute-force dangerous pairs: %d
					- fast selector pruned pairs: %d
					- fast selector elapsed: %d ms
					- brute-force elapsed: %d ms
					- measured speedup: %.2fx

					Correctness:
					- dangerous pair set matched brute force exactly
					- violation counts matched for every dangerous pair
					- upper-bound loading and total normalized overload matched within test tolerance
					""".formatted(
					CASE300_REFERENCE_README,
					CASE300_FILE,
					outageCandidateCount,
					monitoredBranchCount,
					originalPairCount,
					pruningStages,
					pruningFinalPairCount,
					pruningElapsedMillis,
					fastDangerousPairCount,
					bruteForceDangerousPairCount,
					fastPrunedPairCount,
					fastElapsedMillis,
					bruteForceElapsedMillis,
					speedup);
		}
	}

	private record Case300RiskGraphReport(
			int outageCandidateCount,
			int monitoredBranchCount,
			long originalPairCount,
			long baselineDangerousPairCount,
			long baselineExactEvaluatedPairCount,
			long baselineRiskGraphSkippedPairCount,
			long baselineElapsedMillis,
			long prunedDangerousPairCount,
			long prunedExactEvaluatedPairCount,
			long prunedSkippedPairCount,
			long prunedElapsedMillis,
			long riskGraphDangerousPairCount,
			long riskGraphExactEvaluatedPairCount,
			long riskGraphSkippedPairCount,
			long riskGraphElapsedMillis,
			double minimumRiskGraphScore,
			double minimumOutageInteractionLodf,
			double ratingBaseFlowMultiplier,
			int prunedMissedBaselinePairCount,
			int prunedExtraPairCount,
			int missedBaselinePairCount,
			int extraRiskGraphPairCount) {

		String toText() {
			double pruningExactEvaluationReduction = originalPairCount > 0L
					? 100.0 * (double) prunedSkippedPairCount / (double) originalPairCount
					: 0.0;
			double exactEvaluationReduction = originalPairCount > 0L
					? 100.0 * (double) riskGraphSkippedPairCount / (double) originalPairCount
					: 0.0;
			double pruningSpeedup = prunedElapsedMillis > 0L
					? (double) baselineElapsedMillis / (double) prunedElapsedMillis
					: Double.POSITIVE_INFINITY;
			double speedup = riskGraphElapsedMillis > 0L
					? (double) baselineElapsedMillis / (double) riskGraphElapsedMillis
					: Double.POSITIVE_INFINITY;
			return """
					Fast N-2 case300 risk-graph performance report
					================================================
					Reference paper/repo headline:
					%s

					InterPSS benchmark scope:
					- MATPOWER file: %s
					- outage candidates: %d
					- monitored branches: %d
					- candidate pairs: %d
					- ratings: test-only RateB values set to max(1.0, %.2f * abs(base DCLF MW flow))

					Risk graph thresholds:
					- minimum monitor risk score: %.4f
					- minimum outage interaction LODF: %.4f

					Baseline exhaustive selector:
					- dangerous pairs: %d
					- exact evaluated pairs: %d
					- risk-graph skipped pairs: %d
					- elapsed: %d ms

					Upper-bound pruning prescreen selector:
					- dangerous pairs: %d
					- exact evaluated pairs: %d
					- upper-bound pruned pairs: %d
					- exact pair evaluation reduction: %.2f%%
					- elapsed: %d ms
					- measured selector speedup: %.2fx

					Risk-graph prescreen selector:
					- dangerous pairs: %d
					- exact evaluated pairs: %d
					- risk-graph skipped pairs: %d
					- exact pair evaluation reduction: %.2f%%
					- elapsed: %d ms
					- measured selector speedup: %.2fx

					Correctness:
					- upper-bound pruning missed baseline dangerous pairs: %d
					- upper-bound pruning extra dangerous pairs: %d
					- missed baseline dangerous pairs: %d
					- extra risk-graph dangerous pairs: %d
					- interpretation: upper-bound pruning must stay at zero missed pairs; risk graph is a heuristic accelerator unless missed pairs are zero
					""".formatted(
					CASE300_REFERENCE_README,
					CASE300_FILE,
					outageCandidateCount,
					monitoredBranchCount,
					originalPairCount,
					ratingBaseFlowMultiplier,
					minimumRiskGraphScore,
					minimumOutageInteractionLodf,
					baselineDangerousPairCount,
					baselineExactEvaluatedPairCount,
					baselineRiskGraphSkippedPairCount,
					baselineElapsedMillis,
					prunedDangerousPairCount,
					prunedExactEvaluatedPairCount,
					prunedSkippedPairCount,
					pruningExactEvaluationReduction,
					prunedElapsedMillis,
					pruningSpeedup,
					riskGraphDangerousPairCount,
					riskGraphExactEvaluatedPairCount,
					riskGraphSkippedPairCount,
					exactEvaluationReduction,
					riskGraphElapsedMillis,
					speedup,
					prunedMissedBaselinePairCount,
					prunedExtraPairCount,
					missedBaselinePairCount,
					extraRiskGraphPairCount);
		}
	}
}
