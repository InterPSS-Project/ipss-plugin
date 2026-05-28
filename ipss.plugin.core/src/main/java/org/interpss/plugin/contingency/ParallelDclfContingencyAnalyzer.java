package org.interpss.plugin.contingency;

import static com.interpss.core.DclfAlgoObjectFactory.createCaMonitoringBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createMultiOutageContingency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.DclfContingencyWoodburySolver;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.BaseContingency;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfMonitoringBranch;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.net.ref.impl.NetworkRefImpl;

public class ParallelDclfContingencyAnalyzer  extends NetworkRefImpl<AclfNetwork>{
    //create logger
    private static final Logger log = LoggerFactory.getLogger(ParallelDclfContingencyAnalyzer.class);
	private static final int DEFAULT_WOODBURY_MONITOR_BATCH_SIZE = 5120;
	private static final String WOODBURY_MONITOR_BATCH_SIZE_PROPERTY =
			"interpss.dclf.woodbury.monitorBatchSize";

    /**
	 * Constructor
	 * 
	 * @param net  the AC load flow network
	 */
	public ParallelDclfContingencyAnalyzer(AclfNetwork net) {
		setNetwork(net);
	}
    
    /**
	 * Core contingency analysis method for GUI output.
	 * 
	 * @param aclfNet The AC load flow network
	 * @param contingencyList List of contingencies to analyze
	 * @param monitoredBranchIds Set of branch IDs to monitor for violations (null = monitor all)
	 * @param violatingLoadingPercent Threshold for violation detection
	 * @param parallelismLevel Number of threads to use for parallel execution
	 * @return ConcurrentLinkedQueue containing all contingency results that exceed threshold
	 */
	public static ConcurrentLinkedQueue<BranchCAResultRec> executeContingencyAnalysis(
	        AclfNetwork aclfNet,
	        List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList,
	        Set<String> monitoredBranchIds,
            DclfContingencyConfig config,
	        int parallelismLevel) {
	    log.debug("Starting core contingency analysis with {} contingencies, {} monitored branches, {} parallelism",
	            contingencyList.size(), 
	            monitoredBranchIds == null ? "all" : monitoredBranchIds.size(),
	            parallelismLevel);
	    
	    return performContingencyAnalysis(
				aclfNet,
				contingencyList,
				monitoredBranchIds,
	    		config.getOverloadThreshold(),
				config.isDclfInclLoss(),
				parallelismLevel,
				config.getSolutionMethod());
	}
	
    /**
	 * Core contingency analysis method without GUI output format dependencies.
	 * 
	 * @param aclfNet The AC load flow network
	 * @param contingencyList List of contingencies to analyze
	 * @param monitoredBranchIds Set of branch IDs to monitor for violations (null = monitor all)
	 * @param overloadThreshold Threshold for violation detection
	 * @param dclfInclLoss Whether to include losses in DCLF calculation
	 * @param parallelismLevel Number of threads to use for parallel execution
	 * @return ConcurrentLinkedQueue containing all contingency results that exceed threshold
	 */
	public static ConcurrentLinkedQueue<BranchCAResultRec> performContingencyAnalysis(
	        AclfNetwork aclfNet,
	        List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList,
	        Set<String> monitoredBranchIds,
	        double overloadThreshold,
	        boolean dclfInclLoss,
	        int parallelismLevel) {
		return performContingencyAnalysis(
				aclfNet,
				contingencyList,
				monitoredBranchIds,
				overloadThreshold,
				dclfInclLoss,
				parallelismLevel,
				DclfContingencySolutionMethod.SparseEqnSolve);
	}

	public static ConcurrentLinkedQueue<BranchCAResultRec> performContingencyAnalysis(
	        AclfNetwork aclfNet,
	        List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList,
	        Set<String> monitoredBranchIds,
	        double overloadThreshold,
	        boolean dclfInclLoss,
	        int parallelismLevel,
	        DclfContingencySolutionMethod solutionMethod) {
		if (contingencyList == null) {
			throw new IllegalArgumentException("contingencyList cannot be null");
		}
		if (solutionMethod == null) {
			throw new IllegalArgumentException("solutionMethod cannot be null");
		}

	    ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet);
		dclfAlgo.setSolutionMethod(solutionMethod);
	    DclfMethod method = dclfInclLoss? DclfMethod.INC_LOSS : DclfMethod.STD;
	    dclfAlgo.calculateDclf(method);
	    
		log.info("Dclf calculation using " + method + ", solution method " + solutionMethod);
		log.info("RefBus P :" + dclfAlgo.getBusPower(aclfNet.getRefBusId()) + " @" + aclfNet.getRefBusId() );

		if (!contingencyList.isEmpty()
				&& solutionMethod == DclfContingencySolutionMethod.WoodburyMatrixUpdate
				&& isFastOpenBranchStudy(contingencyList)) {
			return performCachedWoodburyOpenBranchOutageAnalysis(
					dclfAlgo,
					contingencyList,
					monitoredBranchIds,
					overloadThreshold,
					BranchCAResultRec.ContingencyShiftThreshold,
					method,
					parallelismLevel);
		}

		if (!contingencyList.isEmpty() && isFastOpenBranchStudy(contingencyList)) {
			refreshOutagePreFlows(dclfAlgo, contingencyList);
			return performOpenBranchOutageFastAnalysis(
					dclfAlgo,
					contingencyList,
					monitoredBranchIds,
					overloadThreshold,
					parallelismLevel);
		}

		try {
			return performCoreCaAnalysis(
					aclfNet,
					contingencyList,
					monitoredBranchIds,
					overloadThreshold,
					BranchCAResultRec.ContingencyShiftThreshold,
					method,
					solutionMethod,
					parallelismLevel);
		} catch (InterpssException e) {
			throw new RuntimeException(e);
		}
	}

	private static ConcurrentLinkedQueue<BranchCAResultRec> performCachedWoodburyOpenBranchOutageAnalysis(
			ContingencyAnalysisAlgorithm dclfAlgo,
			List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList,
			Set<String> monitoredBranchIds,
			double overloadThreshold,
			double shiftThresholdMw,
			DclfMethod method,
			int parallelismLevel) {
		try {
			DclfContingencyWoodburySolver solver = new DclfContingencyWoodburySolver(dclfAlgo);
			List<DclfBranchOutage> branchOutages = toBranchOutageList(contingencyList);
			AclfBranch[] monitorBranches = monitoredBranches(dclfAlgo, monitoredBranchIds);
			ConcurrentLinkedQueue<BranchCAResultRec> results = analyzeWoodburyOpenBranchOutageBatches(
					solver,
					branchOutages,
					monitorBranches,
					method,
					overloadThreshold,
					shiftThresholdMw,
					parallelismLevel);
			log.info("Dclf Woodbury cached contingency analysis completed. Found {} violations out of {} contingencies",
					results.size(), contingencyList.size());
			return results;
		} catch (InterpssException e) {
			throw new RuntimeException(e);
		}
	}

	private static ConcurrentLinkedQueue<BranchCAResultRec> analyzeWoodburyOpenBranchOutageBatches(
			DclfContingencyWoodburySolver solver,
			List<DclfBranchOutage> branchOutages,
			AclfBranch[] monitorBranches,
			DclfMethod method,
			double overloadThreshold,
			double shiftThresholdMw,
			int parallelismLevel)
			throws InterpssException {
		int monitorBatchSize = woodburyMonitorBatchSize(monitorBranches.length);
		int setupParallelism = Math.max(1, parallelismLevel);
		int scanParallelism = Math.max(1, parallelismLevel);

		ConcurrentLinkedQueue<BranchCAResultRec> results = new ConcurrentLinkedQueue<>();
		for (int from = 0; from < monitorBranches.length; from += monitorBatchSize) {
			int to = Math.min(monitorBranches.length, from + monitorBatchSize);
			AclfBranch[] monitorBatch = Arrays.copyOfRange(monitorBranches, from, to);
			results.addAll(solver.analyzeOpenBranchOutageBatch(
					branchOutages,
					monitorBatch,
					method,
					overloadThreshold,
					shiftThresholdMw,
					monitorBatchSize,
					setupParallelism,
					scanParallelism));
		}
		log.info("Dclf Woodbury monitor batching used for {} monitor branches, {} contingencies, batch size {}",
				monitorBranches.length, branchOutages.size(), monitorBatchSize);
		return results;
	}

	private static int woodburyMonitorBatchSize(int monitorCount) {
		int configuredMonitorBatchSize = Math.max(1,
				Integer.getInteger(WOODBURY_MONITOR_BATCH_SIZE_PROPERTY, DEFAULT_WOODBURY_MONITOR_BATCH_SIZE));
		return Math.max(1, Math.min(configuredMonitorBatchSize, Math.max(1, monitorCount)));
	}

	private static List<DclfBranchOutage> toBranchOutageList(
			List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList) {
		List<DclfBranchOutage> branchOutages = new ArrayList<>(contingencyList.size());
		for (BaseContingency<DclfMonitoringBranch> contingency : contingencyList) {
			branchOutages.add((DclfBranchOutage) contingency);
		}
		return branchOutages;
	}

	private static AclfBranch[] monitoredBranches(
			ContingencyAnalysisAlgorithm dclfAlgo,
			Set<String> monitoredBranchIds) {
		List<AclfBranch> branches = new ArrayList<>();
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			AclfBranch branch = dclfBranch.getBranch();
			if (branch != null
					&& dclfBranch.isActive()
					&& (monitoredBranchIds == null || monitoredBranchIds.contains(branch.getId()))) {
				branches.add(branch);
			}
		}
		return branches.toArray(new AclfBranch[0]);
	}

	private static ConcurrentLinkedQueue<BranchCAResultRec> performOpenBranchOutageFastAnalysis(
			ContingencyAnalysisAlgorithm dclfAlgo,
			List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList,
			Set<String> monitoredBranchIds,
			double overloadThreshold,
			int parallelismLevel) {
	    ConcurrentLinkedQueue<BranchCAResultRec> caResultRecords = new ConcurrentLinkedQueue<>();
	    
	    executeParallel(
	        contingencyList.stream(),
	        contingency -> {
				analyzeOpenBranchOutageFast(
						dclfAlgo,
						(DclfBranchOutage) contingency,
						monitoredBranchIds,
						overloadThreshold,
						caResultRecords);
	        },
	        parallelismLevel
	    );
	    
	    log.info("Dclf contingency analysis completed. Found {} violations out of {} contingencies",
	            caResultRecords.size(), contingencyList.size());
	    
	    return caResultRecords;
	}

	private static ConcurrentLinkedQueue<BranchCAResultRec> performCoreCaAnalysis(
			AclfNetwork aclfNet,
			List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList,
			Set<String> monitoredBranchIds,
			double overloadThreshold,
			double shiftThresholdMw,
			DclfMethod method,
			DclfContingencySolutionMethod solutionMethod,
			int parallelismLevel)
			throws InterpssException {
		ConcurrentLinkedQueue<BranchCAResultRec> caResultRecords = new ConcurrentLinkedQueue<>();
		if (contingencyList.isEmpty()) {
			return caResultRecords;
		}

		int workerCount = Math.max(1, Math.min(parallelismLevel, contingencyList.size()));
		ContingencyAnalysisAlgorithm[] workerAlgorithms =
				buildWorkerAlgorithms(aclfNet, method, solutionMethod, workerCount);
		double baseMva = aclfNet.getBaseMva();

		try {
			executeParallel(
					java.util.stream.IntStream.range(0, workerCount).boxed(),
					workerIndex -> {
						try {
							ContingencyAnalysisAlgorithm workerAlgo = workerAlgorithms[workerIndex];
							for (int contingencyIndex = workerIndex;
									contingencyIndex < contingencyList.size();
									contingencyIndex += workerCount) {
								BaseContingency<DclfMonitoringBranch> workingContingency =
										createWorkingContingency(
												workerAlgo,
												contingencyList.get(contingencyIndex),
												monitoredBranchIds);
								workerAlgo.ca(workingContingency);
								collectMonitoringResults(
										workerAlgo,
										workingContingency,
										baseMva,
										overloadThreshold,
										shiftThresholdMw,
										caResultRecords);
							}
						} catch (InterpssException e) {
							throw new RuntimeException(e);
						}
					},
					workerCount);
		} catch (RuntimeException e) {
			throwInterpssOrRuntime(e);
		}

		log.info("Dclf contingency analysis completed. Found {} violations out of {} contingencies",
				caResultRecords.size(), contingencyList.size());
		return caResultRecords;
	}

	private static void analyzeOpenBranchOutageFast(
			ContingencyAnalysisAlgorithm dclfAlgo,
			DclfBranchOutage contingency,
			Set<String> monitoredBranchIds,
			double overloadThreshold,
			ConcurrentLinkedQueue<BranchCAResultRec> caResultRecords) {
		try {
			/*
			 * Standard N-1 open outages use the same Sherman-Morrison/LODF vector
			 * kernel for both solution-method names. Avoid ContingencyAnalysisAlgorithm.ca()
			 * here because it mutates every DclfAlgoBranch and copies monitoring state;
			 * this analyzer only needs streamed BranchCAResultRec records.
			 */
			DclfOutageBranch outageBranch = contingency.getOutageEquip();
			if (outageBranch == null || outageBranch.getOutageType() != ContingencyBranchOutageType.OPEN) {
				throw new UnsupportedOperationException(
						"ParallelDclfContingencyAnalyzer fast path supports OPEN branch outages only: "
								+ contingency.getId());
			}

			double baseMva = dclfAlgo.getAclfNet().getBaseMva();
			double outagePreFlowMw = outageBranch.getDclfFlow() * baseMva;
			double[] lodfAry = dclfAlgo.lineOutageDFactors(outageBranch);

			for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
				if (!dclfBranch.isActive()) {
					continue;
				}

				AclfBranch aclfBranch = dclfBranch.getBranch();
				if (monitoredBranchIds != null && !monitoredBranchIds.contains(aclfBranch.getId())) {
					continue;
				}

				double shiftedFlowMw = outagePreFlowMw * lodfAry[aclfBranch.getSortNumber()];
				if (Math.abs(shiftedFlowMw) <= BranchCAResultRec.ContingencyShiftThreshold) {
					continue;
				}

				BranchCAResultRec result =
						new BranchCAResultRec(
								contingency,
								aclfBranch,
								dclfBranch.getDclfFlow() * baseMva,
								shiftedFlowMw);
				if (result.calLoadingPercent() >= overloadThreshold) {
					caResultRecords.add(result);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void refreshOutagePreFlows(
			ContingencyAnalysisAlgorithm dclfAlgo,
			List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList) {
		for (BaseContingency<DclfMonitoringBranch> baseContingency : contingencyList) {
			DclfBranchOutage contingency = (DclfBranchOutage) baseContingency;
			if (contingency.getOutageEquip() == null || contingency.getOutageEquip().getBranch() == null) {
				continue;
			}
			String branchId = contingency.getOutageEquip().getBranch().getId();
			contingency.getOutageEquip().setDclfFlow(dclfAlgo.getDclfAlgoBranch(branchId).getDclfFlow());
		}
	}

	private static boolean isFastOpenBranchStudy(
			List<? extends BaseContingency<DclfMonitoringBranch>> contingencyList) {
		for (BaseContingency<DclfMonitoringBranch> contingency : contingencyList) {
			if (!(contingency instanceof DclfBranchOutage)) {
				return false;
			}
			DclfOutageBranch outageBranch = ((DclfBranchOutage) contingency).getOutageEquip();
			if (outageBranch == null || outageBranch.getOutageType() != ContingencyBranchOutageType.OPEN) {
				return false;
			}
		}
		return true;
	}

	private static ContingencyAnalysisAlgorithm[] buildWorkerAlgorithms(
			AclfNetwork aclfNet,
			DclfMethod method,
			DclfContingencySolutionMethod solutionMethod,
			int workerCount)
			throws InterpssException {
		ContingencyAnalysisAlgorithm[] workerAlgorithms =
				new ContingencyAnalysisAlgorithm[workerCount];

		try {
			executeParallel(
					java.util.stream.IntStream.range(0, workerCount).boxed(),
					workerIndex -> {
						ContingencyAnalysisAlgorithm dclfAlgo =
								createContingencyAnalysisAlgorithm(aclfNet);
						dclfAlgo.setSolutionMethod(solutionMethod);
						dclfAlgo.calculateDclf(method);
						workerAlgorithms[workerIndex] = dclfAlgo;
					},
					workerCount);
		} catch (RuntimeException e) {
			throwInterpssOrRuntime(e);
		}

		return workerAlgorithms;
	}

	private static BaseContingency<DclfMonitoringBranch> createWorkingContingency(
			ContingencyAnalysisAlgorithm dclfAlgo,
			BaseContingency<DclfMonitoringBranch> source,
			Set<String> monitoredBranchIds)
			throws InterpssException {
		if (source instanceof DclfBranchOutage) {
			return createWorkingBranchOutage(dclfAlgo, (DclfBranchOutage) source, monitoredBranchIds);
		}
		if (source instanceof DclfMultiOutage) {
			return createWorkingMultiOutage(dclfAlgo, (DclfMultiOutage) source, monitoredBranchIds);
		}
		throw new InterpssException("Unsupported DCLF contingency type: "
				+ (source == null ? "null" : source.getClass().getName()));
	}

	private static DclfBranchOutage createWorkingBranchOutage(
			ContingencyAnalysisAlgorithm dclfAlgo,
			DclfBranchOutage source,
			Set<String> monitoredBranchIds)
			throws InterpssException {
		if (source == null || source.getOutageEquip() == null || source.getOutageEquip().getBranch() == null) {
			throw new InterpssException("DCLF branch outage contingency cannot be null");
		}

		DclfBranchOutage working = createContingency(source.getId());
		working.setOutageType(source.getOutageType());
		DclfOutageBranch sourceOutage = source.getOutageEquip();
		DclfOutageBranch workingOutage =
				createWorkingOutageBranch(dclfAlgo, sourceOutage, sourceOutage.getOutageType());
		working.setOutageEquip(workingOutage);
		addMonitoringBranches(dclfAlgo, working, monitoredBranchIds);
		return working;
	}

	private static DclfMultiOutage createWorkingMultiOutage(
			ContingencyAnalysisAlgorithm dclfAlgo,
			DclfMultiOutage source,
			Set<String> monitoredBranchIds)
			throws InterpssException {
		if (source == null) {
			throw new InterpssException("DCLF multi-outage contingency cannot be null");
		}
		if (source.getOutageEquips().isEmpty()) {
			throw new InterpssException("DCLF multi-outage contingency has no outage branches: "
					+ source.getId());
		}

		DclfMultiOutage working =
				createMultiOutageContingency(source.getId(), source.getOutageType());
		for (DclfOutageBranch sourceOutage : source.getOutageEquips()) {
			working.getOutageEquips().add(
					createWorkingOutageBranch(dclfAlgo, sourceOutage, source.getOutageType()));
		}
		addMonitoringBranches(dclfAlgo, working, monitoredBranchIds);
		return working;
	}

	private static DclfOutageBranch createWorkingOutageBranch(
			ContingencyAnalysisAlgorithm dclfAlgo,
			DclfOutageBranch sourceOutage,
			ContingencyBranchOutageType outageType)
			throws InterpssException {
		if (sourceOutage == null || sourceOutage.getBranch() == null) {
			throw new InterpssException("DCLF outage branch cannot be null");
		}
		DclfAlgoBranch outageDclfBranch =
				dclfAlgo.getDclfAlgoBranch(sourceOutage.getBranch().getId());
		if (outageDclfBranch == null) {
			throw new InterpssException("DCLF outage branch is not available in the analysis algorithm: "
					+ sourceOutage.getBranch().getId());
		}

		DclfOutageBranch workingOutage = createCaOutageBranch(outageDclfBranch, outageType);
		workingOutage.setDclfFlow(outageDclfBranch.getDclfFlow());
		return workingOutage;
	}

	private static void addMonitoringBranches(
			ContingencyAnalysisAlgorithm dclfAlgo,
			BaseContingency<DclfMonitoringBranch> contingency,
			Set<String> monitoredBranchIds) {
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			AclfBranch branch = dclfBranch.getBranch();
			if (branch != null
					&& dclfBranch.isActive()
					&& (monitoredBranchIds == null || monitoredBranchIds.contains(branch.getId()))) {
				contingency.addMonitoringBranch(createCaMonitoringBranch(dclfBranch));
			}
		}
	}

	private static void collectMonitoringResults(
			ContingencyAnalysisAlgorithm dclfAlgo,
			BaseContingency<DclfMonitoringBranch> contingency,
			double baseMva,
			double overloadThreshold,
			double shiftThresholdMw,
			ConcurrentLinkedQueue<BranchCAResultRec> results) {
		for (DclfMonitoringBranch monitoringBranch : contingency.getMonitoringBranches()) {
			AclfBranch monitor = monitoringBranch.getBranch();
			DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(monitor.getId());
			if (dclfBranch == null) {
				continue;
			}

			double preFlowMw = dclfBranch.getDclfFlow() * baseMva;
			double shiftedFlowMw = monitoringBranch.getShiftedFlow() * baseMva;
			if (Math.abs(shiftedFlowMw) <= shiftThresholdMw) {
				continue;
			}

			BranchCAResultRec result =
					new BranchCAResultRec(contingency, monitor, preFlowMw, shiftedFlowMw);
			if (result.calLoadingPercent() >= overloadThreshold) {
				results.add(result);
			}
		}
	}

	private static void throwInterpssOrRuntime(RuntimeException e) throws InterpssException {
		Throwable cause = e;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof InterpssException) {
			throw (InterpssException) cause;
		}
		throw e;
	}
    

    /**
     * Execute a stream in parallel with a specified level of parallelism.
     * 
     * @param <T> Type of stream elements
     * @param stream Stream to execute
     * @param action Action to perform on each element
     * @param parallelism Number of threads to use for parallel execution
     */
	public static <T> void executeParallel(Stream<T> stream, Consumer<T> action, int parallelism) {
        if (parallelism <= 1) {
            // Sequential execution
            stream.forEach(action);
        } else {
            // Parallel execution with custom ForkJoinPool
            ForkJoinPool customPool = new ForkJoinPool(parallelism);
            try {
                customPool.submit(() -> stream.parallel().forEach(action)).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
            	customPool.close(); // Properly close the pool to free resources
                customPool.shutdown();
            }
        }
    }
}
