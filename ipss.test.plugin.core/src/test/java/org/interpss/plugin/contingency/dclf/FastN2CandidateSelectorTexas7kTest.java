package org.interpss.plugin.contingency.dclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.interpss.core.algo.dclf.fastn2.FastN2LodfStats;
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

	private static Texas7kStudySet texas7kStudySet(AclfNetwork net) throws Exception {
		List<BranchContingencyRecord> contingencies =
				ContingencyFileUtil.importContingenciesFromJson(TEXAS7K_CONTINGENCIES.toFile());
		List<MonitoredBranchRecord> monitors =
				ContingencyFileUtil.importMonitoredBranchRecordsFromJson(TEXAS7K_MONITORS.toFile());

		List<String> outageCandidateIds = contingencies.stream()
				.map(record -> record.name)
				.filter(branchId -> isActiveBranch(net, branchId))
				.distinct()
				.limit(OUTAGE_CANDIDATE_LIMIT)
				.toList();
		Set<String> outageSet = new HashSet<>(outageCandidateIds);
		List<String> monitorIds = monitors.stream()
				.map(MonitoredBranchRecord::getBranchId)
				.filter(branchId -> isActiveBranch(net, branchId))
				.filter(branchId -> !outageSet.contains(branchId))
				.collect(Collectors.toCollection(LinkedHashSet::new))
				.stream()
				.limit(MONITORED_BRANCH_LIMIT)
				.toList();

		return new Texas7kStudySet(
				contingencies.size(),
				monitors.size(),
				outageCandidateIds,
				monitorIds);
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
					- scalar LODF elapsed: %d ms
					- monitor/outage LODFs computed: %d
					- outage/outage LODFs computed: %d
					- outage/outage rows reused from monitor matrix: %d
					- LODF cache hits: monitor/outage=%d, outage/outage=%d

					Upper-bound pruning prescreen selector:
					- dangerous pairs: %d
					- exact evaluated pairs: %d
					- upper-bound pruned pairs: %d
					- exact pair evaluation reduction: %.2f%%
					- elapsed: %d ms
					- measured selector speedup: %.2fx
					- scalar LODF elapsed: %d ms
					- monitor/outage LODFs computed: %d
					- outage/outage LODFs computed: %d
					- outage/outage rows reused from monitor matrix: %d
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
					prunedLodfStats.monitorOutageCacheHitCount(),
					prunedLodfStats.outagePairCacheHitCount());
		}
	}
}
