package org.interpss.plugin.contingency;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.sparse.impl.klu.KLUSparseEqnDoubleImpl;

import org.interpss.numeric.sparse.ISparseEqnDouble;

/**
 * Public API for accelerated DCLF sensitivity calculations.
 */
public class DclfSensitivityAnalyzer {
	public static final double DEFAULT_MINIMUM_GSF_GENERATOR_RATING_MVA = 50.0;
	public static final double DEFAULT_MINIMUM_PTDF_LOAD_MW = 0.0;
	public static final int DEFAULT_ENDPOINT_RHS_BATCH_SIZE = 128;

	private final boolean cacheDclfAlgorithm;
	private final Map<DclfAlgorithmCacheKey, ContingencyAnalysisAlgorithm> dclfAlgorithmCache =
			new ConcurrentHashMap<>();

	public DclfSensitivityAnalyzer() {
		this(false);
	}

	public DclfSensitivityAnalyzer(boolean cacheDclfAlgorithm) {
		this.cacheDclfAlgorithm = cacheDclfAlgorithm;
	}

	public SensitivityRunResult runGsf(GsfRunRequest request) {
		ContingencyAnalysisAlgorithm dclfAlgo = createDclfAlgo(request.net(), request.dclfMethod(), "GSF");
		Set<String> sourceBusIds = request.sourceBusIds().isEmpty() ? Set.of() : new HashSet<>(request.sourceBusIds());
		double minimumGeneratorRatingMva = Math.max(0.0, request.minimumGeneratorRatingMva());
		List<? extends BaseAclfBus<?, ?>> generatorBuses = request.net().getBusList().stream()
				.filter(bus -> bus.isActive() && (bus.isGenPV() || bus.isGenPQ()))
				.filter(bus -> sourceBusIds.isEmpty() || sourceBusIds.contains(bus.getId()))
				.filter(bus -> meetsMinimumGeneratorRating(bus, minimumGeneratorRatingMva))
				.toList();
		List<AclfBranch> monitorBranches = findUniqueBranches(request.net(), request.branchIds());
		long startedNanos = System.nanoTime();

		List<DclfSensitivityResult> endpointResults = busToBranchFactorsWithEndpointSolves(
				dclfAlgo,
				generatorBuses,
				monitorBranches,
				request.threshold(),
				request.endpointRhsBatchSize());
		if (endpointResults != null) {
			long candidateCount = (long) monitorBranches.size() * generatorBuses.size();
			return new SensitivityRunResult(endpointResults, candidateCount, elapsedMillis(startedNanos), true);
		}

		ConcurrentLinkedQueue<DclfSensitivityResult> results = new ConcurrentLinkedQueue<>();
		generatorBuses.parallelStream().forEach(bus -> {
			String busId = bus.getId();
			for (AclfBranch branch : monitorBranches) {
				double branchRatingMva = branch.getRatingMva1() > 0 ? branch.getRatingMva1() : 0.0;
				double gsf = dclfAlgo.calGenShiftFactor(busId, branch);
				if (Math.abs(gsf) > request.threshold()) {
					results.add(new DclfSensitivityResult(busId, branch.getId(), branchRatingMva, gsf));
				}
			}
		});

		long candidateCount = (long) monitorBranches.size() * generatorBuses.size();
		return new SensitivityRunResult(new ArrayList<>(results), candidateCount, elapsedMillis(startedNanos), false);
	}

	public SensitivityRunResult runPtdf(PtdfRunRequest request) {
		ContingencyAnalysisAlgorithm dclfAlgo = createDclfAlgo(request.net(), request.dclfMethod(), "PTDF");
		List<AclfBranch> monitorBranches = findUniqueBranches(request.net(), request.branchIds());
		List<? extends BaseAclfBus<?, ?>> loadBuses = request.net().getBusList().stream()
				.filter(bus -> bus.isActive() && totalLoadMw(bus) > 0.0)
				.filter(bus -> meetsMinimumLoad(bus, request.minimumLoadMw()))
				.filter(bus -> matchesAreaFilter(bus, request.areaIds()))
				.toList();
		long startedNanos = System.nanoTime();

		List<DclfSensitivityResult> endpointResults = busToBranchFactorsWithEndpointSolves(
				dclfAlgo,
				loadBuses,
				monitorBranches,
				request.threshold(),
				request.endpointRhsBatchSize());
		if (endpointResults != null) {
			long candidateCount = (long) monitorBranches.size() * loadBuses.size();
			return new SensitivityRunResult(endpointResults, candidateCount, elapsedMillis(startedNanos), true);
		}

		ConcurrentLinkedQueue<DclfSensitivityResult> results = new ConcurrentLinkedQueue<>();
		monitorBranches.parallelStream().forEach(branch -> {
			String branchId = branch.getId();
			double branchRatingMva = branch.getRatingMva1() > 0 ? branch.getRatingMva1() : 0.0;
			loadBuses.parallelStream().forEach(bus -> {
				try {
					double ptdf = dclfAlgo.pTransferDistFactor(bus.getId(), branch);
					if (Math.abs(ptdf) > request.threshold()) {
						results.add(new DclfSensitivityResult(bus.getId(), branchId, branchRatingMva, ptdf));
					}
				} catch (Exception ex) {
					throw new IllegalStateException("Failed to calculate PTDF for Load@"
							+ bus.getId() + " on branch " + branchId, ex);
				}
			});
		});

		long candidateCount = (long) monitorBranches.size() * loadBuses.size();
		return new SensitivityRunResult(new ArrayList<>(results), candidateCount, elapsedMillis(startedNanos), false);
	}

	public SensitivityRunResult runPtdfTransfer(PtdfTransferRunRequest request) {
		ContingencyAnalysisAlgorithm dclfAlgo = createDclfAlgo(request.net(), request.dclfMethod(), "PTDF transfer");
		ConcurrentLinkedQueue<DclfSensitivityResult> results = new ConcurrentLinkedQueue<>();
		long startedNanos = System.nanoTime();

		request.branchIds().parallelStream().forEach(branchId -> {
			AclfBranch branch = findBranch(request.net(), branchId);
			double branchRatingMva = branch.getRatingMva1() > 0 ? branch.getRatingMva1() : 0.0;
			try {
				double ptdf = dclfAlgo.pTransferDistFactor(request.injectionBusId(), request.withdrawalBusId(), branch);
				if (Math.abs(ptdf) > request.threshold()) {
					results.add(new DclfSensitivityResult(
							request.injectionBusId() + "->" + request.withdrawalBusId(),
							branchId,
							branchRatingMva,
							ptdf));
				}
			} catch (Exception ex) {
				throw new IllegalStateException("Failed to calculate PTDF for injection "
						+ request.injectionBusId() + ", withdrawal " + request.withdrawalBusId()
						+ " on branch " + branchId, ex);
			}
		});

		return new SensitivityRunResult(
				new ArrayList<>(results),
				request.branchIds().size(),
				elapsedMillis(startedNanos),
				false);
	}

	public SensitivityRunResult runLodf(LodfRunRequest request) {
		ContingencyAnalysisAlgorithm dclfAlgo = createDclfAlgo(request.net(), request.dclfMethod(), "LODF");
		ConcurrentLinkedQueue<DclfSensitivityResult> results = new ConcurrentLinkedQueue<>();
		long startedNanos = System.nanoTime();

		request.outageBranchIds().parallelStream().forEach(outageBranchId -> {
			AclfBranch outageBranch = findBranch(request.net(), outageBranchId);
			DclfOutageBranch outage = createCaOutageBranch(
					dclfAlgo.getDclfAlgoBranch(outageBranchId),
					ContingencyBranchOutageType.OPEN);
			request.monitorBranchIds().parallelStream().forEach(monitorBranchId -> {
				AclfBranch monitorBranch = findBranch(request.net(), monitorBranchId);
				double branchRatingMva = monitorBranch.getRatingMva1() > 0 ? monitorBranch.getRatingMva1() : 0.0;
				try {
					double lodf = dclfAlgo.lineOutageDFactor(outage, monitorBranch);
					if (Math.abs(lodf) > request.threshold()) {
						results.add(new DclfSensitivityResult(
								outageBranch.getId(), monitorBranchId, branchRatingMva, lodf));
					}
				} catch (Exception ex) {
					throw new IllegalStateException("Failed to calculate LODF for outage "
							+ outageBranchId + " on branch " + monitorBranchId, ex);
				}
			});
		});

		long candidateCount = (long) request.outageBranchIds().size() * request.monitorBranchIds().size();
		return new SensitivityRunResult(new ArrayList<>(results), candidateCount, elapsedMillis(startedNanos), false);
	}

	public void clearCache() {
		dclfAlgorithmCache.clear();
	}

	public int cachedDclfAlgorithmCount() {
		return dclfAlgorithmCache.size();
	}

	private ContingencyAnalysisAlgorithm createDclfAlgo(AclfNetwork net, DclfMethod dclfMethod, String analysisName) {
		if (net == null) {
			throw new IllegalArgumentException("Network data is not available.");
		}
		if (cacheDclfAlgorithm) {
			DclfAlgorithmCacheKey key = new DclfAlgorithmCacheKey(System.identityHashCode(net), dclfMethod);
			return dclfAlgorithmCache.computeIfAbsent(key,
					ignored -> createCalculatedDclfAlgo(net, dclfMethod, analysisName));
		}
		return createCalculatedDclfAlgo(net, dclfMethod, analysisName);
	}

	private ContingencyAnalysisAlgorithm createCalculatedDclfAlgo(
			AclfNetwork net,
			DclfMethod dclfMethod,
			String analysisName) {
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		if (!dclfAlgo.calculateDclf(dclfMethod)) {
			throw new IllegalStateException("Could not calculate DC load flow for " + analysisName + " analysis.");
		}
		return dclfAlgo;
	}

	private AclfBranch findBranch(AclfNetwork net, String branchId) {
		AclfBranch branch = net.getBranch(branchId);
		if (branch == null) {
			throw new IllegalArgumentException("Branch " + branchId + " not found in network");
		}
		return branch;
	}

	private List<AclfBranch> findUniqueBranches(AclfNetwork net, List<String> branchIds) {
		return branchIds.stream()
				.distinct()
				.map(branchId -> findBranch(net, branchId))
				.toList();
	}

	private static boolean meetsMinimumGeneratorRating(BaseAclfBus<?, ?> bus, double minimumGeneratorRatingMva) {
		if (minimumGeneratorRatingMva <= 0.0) {
			return true;
		}
		double baseMva = bus.getNetwork() != null ? bus.getNetwork().getBaseMva() : 1.0;
		boolean hasGeneratorWithMvaBase = false;
		for (AclfGen gen : bus.getContributeGenList()) {
			if (!gen.isActive()) {
				continue;
			}
			if (gen.getMvaBase() > 0.0) {
				hasGeneratorWithMvaBase = true;
				if (gen.getMvaBase() >= minimumGeneratorRatingMva) {
					return true;
				}
			}
		}
		if (hasGeneratorWithMvaBase) {
			return false;
		}
		return Math.abs(bus.getGenP() * baseMva) >= minimumGeneratorRatingMva;
	}

	private static boolean meetsMinimumLoad(BaseAclfBus<?, ?> bus, double minimumLoadMw) {
		if (minimumLoadMw <= 0.0) {
			return true;
		}
		return Math.abs(totalLoadMw(bus)) >= minimumLoadMw;
	}

	public static double totalLoadMw(BaseAclfBus<?, ?> bus) {
		double baseMva = bus.getNetwork() != null ? bus.getNetwork().getBaseMva() : 1.0;
		if (!bus.getContributeLoadList().isEmpty()) {
			double totalLoadPu = 0.0;
			for (AclfLoad load : bus.getContributeLoadList()) {
				if (!load.isActive()) {
					continue;
				}
				Complex loadValue = load.getLoad(bus.getVoltageMag());
				if (loadValue != null) {
					totalLoadPu += loadValue.getReal();
				}
			}
			return totalLoadPu * baseMva;
		}
		Complex load = bus.calNetLoadResults();
		return load != null ? load.getReal() * baseMva : 0.0;
	}

	private static boolean matchesAreaFilter(BaseAclfBus<?, ?> bus, List<String> areaIds) {
		if (areaIds.isEmpty()) {
			return true;
		}
		String areaId = bus.getAreaId();
		return areaId != null && areaIds.contains(areaId);
	}

	private List<DclfSensitivityResult> busToBranchFactorsWithEndpointSolves(
			ContingencyAnalysisAlgorithm dclfAlgo,
			List<? extends BaseAclfBus<?, ?>> sourceBuses,
			List<AclfBranch> monitorBranches,
			double threshold,
			int endpointRhsBatchSize) {
		if (sourceBuses.isEmpty() || monitorBranches.isEmpty()) {
			return List.of();
		}

		Map<Integer, EndpointSensitivityVector> endpointVectors;
		try {
			endpointVectors = solveEndpointSensitivityVectors(dclfAlgo, monitorBranches, endpointRhsBatchSize);
		} catch (Exception ex) {
			return null;
		}
		if (endpointVectors == null) {
			return null;
		}

		List<SourceBusSensitivity> sources = sourceBuses.stream()
				.map(bus -> new SourceBusSensitivity(bus.getId(), activeSensitivitySortNumber(bus)))
				.filter(source -> source.sortNumber() >= 0)
				.toList();
		if (sources.isEmpty()) {
			return List.of();
		}

		List<MonitorBranchSensitivity> branches = monitorBranches.stream().map(branch -> {
			int fromSortNumber = activeSensitivitySortNumber(branch.getFromAclfBus());
			int toSortNumber = activeSensitivitySortNumber(branch.getToAclfBus());
			return new MonitorBranchSensitivity(
					branch.getId(),
					branch.getRatingMva1() > 0 ? branch.getRatingMva1() : 0.0,
					-branch.b1ft(),
					fromSortNumber >= 0 ? endpointVectors.get(fromSortNumber) : null,
					toSortNumber >= 0 ? endpointVectors.get(toSortNumber) : null);
		}).toList();

		List<List<DclfSensitivityResult>> branchResults = branches.parallelStream()
				.map(branch -> sensitivityResultsForBranch(branch, sources, threshold))
				.toList();
		int resultCount = branchResults.stream().mapToInt(List::size).sum();
		List<DclfSensitivityResult> results = new ArrayList<>(resultCount);
		branchResults.forEach(results::addAll);
		return results;
	}

	private List<DclfSensitivityResult> sensitivityResultsForBranch(
			MonitorBranchSensitivity branch,
			List<SourceBusSensitivity> sources,
			double threshold) {
		List<DclfSensitivityResult> results = new ArrayList<>();
		for (SourceBusSensitivity source : sources) {
			double fromSensitivity = branch.fromVector() == null ? 0.0 : branch.fromVector().value(source.sortNumber());
			double toSensitivity = branch.toVector() == null ? 0.0 : branch.toVector().value(source.sortNumber());
			double factor = branch.branchFactor() * (fromSensitivity - toSensitivity);
			if (Math.abs(factor) > threshold) {
				results.add(new DclfSensitivityResult(
						source.busId(), branch.branchId(), branch.branchRatingMva(), factor));
			}
		}
		return results;
	}

	private Map<Integer, EndpointSensitivityVector> solveEndpointSensitivityVectors(
			ContingencyAnalysisAlgorithm dclfAlgo,
			List<AclfBranch> monitorBranches,
			int endpointRhsBatchSize) throws Exception {
		ISparseEqnDouble b1Matrix = dclfAlgo.getB1Matrix();
		List<Integer> endpointSortNumbers = collectEndpointSortNumbers(monitorBranches);
		if (b1Matrix instanceof KLUSparseEqnDoubleImpl kluB1 && endpointRhsBatchSize > 1) {
			return solveEndpointSensitivityVectorsBatched(kluB1, endpointSortNumbers, endpointRhsBatchSize);
		}

		Map<Integer, EndpointSensitivityVector> endpointVectors = new HashMap<>(endpointSortNumbers.size());
		int dimension = b1Matrix.getDimension();
		for (Integer endpointSortNumber : endpointSortNumbers) {
			double[] rhs = new double[dimension];
			rhs[endpointSortNumber] = 1.0;
			endpointVectors.put(endpointSortNumber, new EndpointSensitivityVector(
					b1Matrix.solveLUedEqn(rhs), 0));
		}
		return endpointVectors;
	}

	private Map<Integer, EndpointSensitivityVector> solveEndpointSensitivityVectorsBatched(
			KLUSparseEqnDoubleImpl kluB1,
			List<Integer> endpointSortNumbers,
			int endpointRhsBatchSize) throws Exception {
		Map<Integer, EndpointSensitivityVector> endpointVectors = new HashMap<>(endpointSortNumbers.size());
		int dimension = kluB1.getDimension();
		int batchSize = Math.max(1, endpointRhsBatchSize);
		for (int from = 0; from < endpointSortNumbers.size(); from += batchSize) {
			int to = Math.min(endpointSortNumbers.size(), from + batchSize);
			int[] rhsIndexes = new int[to - from];
			for (int i = 0; i < rhsIndexes.length; i++) {
				rhsIndexes[i] = endpointSortNumbers.get(from + i);
			}
			double[] panel = kluB1.solveUnitRhsBatch(rhsIndexes);
			for (int i = 0; i < rhsIndexes.length; i++) {
				endpointVectors.put(rhsIndexes[i], new EndpointSensitivityVector(panel, i * dimension));
			}
		}
		return endpointVectors;
	}

	private static List<Integer> collectEndpointSortNumbers(List<AclfBranch> monitorBranches) {
		Set<Integer> endpointSortNumbers = new LinkedHashSet<>();
		monitorBranches.forEach(branch -> {
			int fromSortNumber = activeSensitivitySortNumber(branch.getFromAclfBus());
			if (fromSortNumber >= 0) {
				endpointSortNumbers.add(fromSortNumber);
			}
			int toSortNumber = activeSensitivitySortNumber(branch.getToAclfBus());
			if (toSortNumber >= 0) {
				endpointSortNumbers.add(toSortNumber);
			}
		});
		return new ArrayList<>(endpointSortNumbers);
	}

	private static int activeSensitivitySortNumber(BaseAclfBus<?, ?> bus) {
		if (bus == null || bus.isRefBus()) {
			return -1;
		}
		return bus.getSortNumber();
	}

	private static long elapsedMillis(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	public record GsfRunRequest(
			AclfNetwork net,
			DclfMethod dclfMethod,
			List<String> branchIds,
			double threshold,
			List<String> sourceBusIds,
			double minimumGeneratorRatingMva,
			int endpointRhsBatchSize) {
		public GsfRunRequest {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
			sourceBusIds = sourceBusIds == null ? List.of() : List.copyOf(sourceBusIds);
			minimumGeneratorRatingMva = Math.max(0.0, minimumGeneratorRatingMva);
			endpointRhsBatchSize = endpointRhsBatchSize <= 0
					? DEFAULT_ENDPOINT_RHS_BATCH_SIZE : endpointRhsBatchSize;
		}

		public GsfRunRequest(
				AclfNetwork net,
				DclfMethod dclfMethod,
				List<String> branchIds,
				double threshold,
				List<String> sourceBusIds,
				double minimumGeneratorRatingMva) {
			this(net, dclfMethod, branchIds, threshold, sourceBusIds,
					minimumGeneratorRatingMva, DEFAULT_ENDPOINT_RHS_BATCH_SIZE);
		}
	}

	public record PtdfRunRequest(
			AclfNetwork net,
			DclfMethod dclfMethod,
			List<String> branchIds,
			double threshold,
			double minimumLoadMw,
			List<String> areaIds,
			int endpointRhsBatchSize) {
		public PtdfRunRequest {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
			minimumLoadMw = Math.max(0.0, minimumLoadMw);
			areaIds = areaIds == null ? List.of() : areaIds.stream()
					.filter(areaId -> areaId != null && !areaId.isBlank())
					.map(String::trim)
					.distinct()
					.toList();
			endpointRhsBatchSize = endpointRhsBatchSize <= 0
					? DEFAULT_ENDPOINT_RHS_BATCH_SIZE : endpointRhsBatchSize;
		}

		public PtdfRunRequest(
				AclfNetwork net,
				DclfMethod dclfMethod,
				List<String> branchIds,
				double threshold,
				double minimumLoadMw,
				List<String> areaIds) {
			this(net, dclfMethod, branchIds, threshold, minimumLoadMw,
					areaIds, DEFAULT_ENDPOINT_RHS_BATCH_SIZE);
		}
	}

	public record PtdfTransferRunRequest(
			AclfNetwork net,
			DclfMethod dclfMethod,
			List<String> branchIds,
			String injectionBusId,
			String withdrawalBusId,
			double threshold) {
		public PtdfTransferRunRequest {
			branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
			if (injectionBusId == null || injectionBusId.isBlank()) {
				throw new IllegalArgumentException("Injection bus id is required.");
			}
			if (withdrawalBusId == null || withdrawalBusId.isBlank()) {
				throw new IllegalArgumentException("Withdrawal bus id is required.");
			}
		}
	}

	public record LodfRunRequest(
			AclfNetwork net,
			DclfMethod dclfMethod,
			List<String> monitorBranchIds,
			List<String> outageBranchIds,
			double threshold) {
		public LodfRunRequest {
			monitorBranchIds = monitorBranchIds == null ? List.of() : List.copyOf(monitorBranchIds);
			outageBranchIds = outageBranchIds == null ? List.of() : List.copyOf(outageBranchIds);
		}
	}

	public record SensitivityRunResult(
			List<DclfSensitivityResult> results,
			long candidateCount,
			long elapsedMillis,
			boolean endpointFastPathUsed) {
		public SensitivityRunResult {
			results = results == null ? List.of() : List.copyOf(results);
		}
	}

	private record DclfAlgorithmCacheKey(
			int networkIdentity,
			DclfMethod dclfMethod) {
	}

	private record SourceBusSensitivity(
			String busId,
			int sortNumber) {
	}

	private record MonitorBranchSensitivity(
			String branchId,
			double branchRatingMva,
			double branchFactor,
			EndpointSensitivityVector fromVector,
			EndpointSensitivityVector toVector) {
	}

	private record EndpointSensitivityVector(
			double[] values,
			int offset) {
		private double value(int index) {
			return values[offset + index];
		}
	}
}
