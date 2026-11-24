package org.interpss.plugin.contingency;

import com.interpss.core.license.EngineStudyAccess;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.interpss.plugin.contingency.result.AclfContingencyResultContainer;
import org.interpss.plugin.contingency.result.AclfContingencyResultRec;
import org.interpss.plugin.contingency.result.AclfContingencyDiagnostic;

import com.interpss.common.util.BoundedParallelExecutor;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;
import com.interpss.core.algo.contingency.aclf.AclfContingencyCaseRequest;
import com.interpss.core.algo.contingency.aclf.AclfContingencyCaseSolver;
import com.interpss.core.contingency.aclf.AclfMultiOutage;
import com.interpss.core.net.ref.impl.NetworkRefImpl;
import com.interpss.state.snapshot.BaseAclfNetworkSnapshotFactory;

/**
 * Parallel Contingency Analysis wrapper for Python integration.
 * This class provides thread-safe parallel contingency analysis capabilities
 * that can be easily called from Python using JPype.
 * 
 * @author InterPSS Team
 */
public class ParallelAclfContingencyAnalyzer <TR extends AclfContingencyResultRec>
        extends NetworkRefImpl<BaseAclfNetwork<?, ?>> {
	private static final Logger log = LoggerFactory.getLogger(ParallelAclfContingencyAnalyzer.class);

	private record CaseResult<T extends AclfContingencyResultRec>(
	        String branchId,
	        T result,
	        List<AclfContingencyDiagnostic> diagnostics) {
	}
	/**
	 * Constructor
	 * 
	 * @param net  the AC load flow network
	 */
	public ParallelAclfContingencyAnalyzer(BaseAclfNetwork<?, ?> net) {
		setNetwork(net);
	}
	
    /**
     * Perform parallel contingency analysis by removing branches one at a time.
     * This method is thread-safe and can be called from Python.
     * 
     * @param totalCases Number of contingency cases to analyze
     * @param config Configuration parameters for the analysis
     * @param useParallel Whether to use parallel processing
     * @return ContingencyResult containing analysis results
     */
    public AclfContingencyResultContainer<TR> analyzeContingencies(int totalCases, 
                                                       AclfContingencyConfig config, boolean useParallel) {
        EngineStudyAccess.require(EngineStudyAccess.AC_CONTINGENCY);
        
        long startTime = System.currentTimeMillis();
        List<AclfContingencyDiagnostic> diagnostics = new ArrayList<>();

        int caseCount = Math.min(Math.max(totalCases, 0), network.getBranchList().size());
        List<String> branchIds = new ArrayList<>(caseCount);
        for (int i = 0; i < caseCount; i++) {
            branchIds.add(network.getBranchList().get(i).getId());
        }

        boolean snapshotSupported = BaseAclfNetworkSnapshotFactory.supports(network);
        boolean runParallel = useParallel && snapshotSupported && caseCount > 1;
        if (useParallel && !snapshotSupported) {
            String message = "No parallel snapshot provider for " + network.getClass().getName()
                    + "; using sequential fallback";
            log.info(message);
            diagnostics.add(new AclfContingencyDiagnostic(
                    null, "INFO", "SNAPSHOT_PROVIDER_UNAVAILABLE", message));
        }

        List<CaseResult<TR>> caseResults = runParallel
                ? analyzeParallel(branchIds, config, diagnostics)
                : analyzeSequential(branchIds, config, snapshotSupported);
        return summarize(caseResults, diagnostics, caseCount, startTime);
    }

    /** Run a batch of typed outages against a solved AC base in the provided order. */
    public AclfContingencyResultContainer<TR> analyzeOutages(List<AclfMultiOutage> outages,
            AclfContingencyConfig config, boolean useParallel, boolean adaptiveRecovery) {
        EngineStudyAccess.require(EngineStudyAccess.AC_CONTINGENCY);
        Objects.requireNonNull(config, "config");
        if (!(network instanceof AclfNetwork base)) {
            throw new IllegalArgumentException("Typed outages require an ACLF network");
        }
        var cases = List.copyOf(outages);
        var ids = new HashSet<String>();
        for (var outage : cases) {
            String id = outage.getId();
            if (id == null || id.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException("Each outage requires a distinct nonblank ID");
            }
        }

        long startTime = System.currentTimeMillis();
        List<CaseResult<TR>> results;
        List<AclfContingencyDiagnostic> diagnostics = new ArrayList<>();
        if (useParallel && cases.size() > 1) {
            int threads = Math.min(cases.size(), config.getParallelism());
            try (var executor = new BoundedParallelExecutor(threads)) {
                results = executor.mapOrdered(cases, outage -> analyzeTyped(base, outage, config, adaptiveRecovery));
            } catch (CancellationException ex) {
                diagnostics.add(new AclfContingencyDiagnostic(null, "WARN", "ANALYSIS_CANCELLED",
                        "Contingency analysis was cancelled"));
                results = List.of();
            }
        } else {
            results = cases.stream().map(outage -> analyzeTyped(base, outage, config, adaptiveRecovery)).toList();
        }
        return summarize(results, diagnostics, cases.size(), startTime);
    }

    @SuppressWarnings("unchecked")
    private CaseResult<TR> analyzeTyped(AclfNetwork base, AclfMultiOutage outage,
            AclfContingencyConfig config, boolean adaptiveRecovery) {
        String id = outage.getId();
        try {
            var request = new AclfContingencyCaseRequest(id, base, outage, null, List.of(), null);
            var result = new AclfContingencyCaseSolver().solve(request,
                    algorithm -> config.applySolverOptions(algorithm, hasImportedProfile(algorithm.getAclfNet())),
                    adaptiveRecovery);
            var diagnostics = result.diagnostics().stream()
                    .map(item -> new AclfContingencyDiagnostic(id, item.severity().name(), item.code(), item.message()))
                    .toList();
            return new CaseResult<>(id, (TR) new AclfContingencyResultRec(
                    result.successful(), result.terminationReport()), diagnostics);
        } catch (RuntimeException ex) {
            log.error("Error processing contingency {}", id, ex);
            return failedCase(id, ex);
        }
    }

    private AclfContingencyResultContainer<TR> summarize(List<CaseResult<TR>> caseResults,
            List<AclfContingencyDiagnostic> diagnostics, int caseCount, long startTime) {
        Map<String, TR> caResults = new LinkedHashMap<>();
        long totalSuccessCount = 0;
        for (CaseResult<TR> caseResult : caseResults) {
            if (caseResult.result() != null) {
                caResults.put(caseResult.branchId(), caseResult.result());
                if (caseResult.result().isConverged()) {
                    totalSuccessCount++;
                }
            }
            diagnostics.addAll(caseResult.diagnostics());
        }
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        return new AclfContingencyResultContainer<TR>(
                caResults, totalSuccessCount, caseCount, executionTime, diagnostics);
    }

    private List<CaseResult<TR>> analyzeParallel(
            List<String> branchIds,
            AclfContingencyConfig config,
            List<AclfContingencyDiagnostic> diagnostics) {
        int threadCount = Math.min(branchIds.size(), config.getParallelism());
        try (BoundedParallelExecutor executor = new BoundedParallelExecutor(threadCount)) {
            return executor.mapOrdered(branchIds, branchId -> analyzeIsolated(branchId, config));
        } catch (CancellationException ex) {
            String message = "Contingency analysis was cancelled";
            log.warn(message);
            diagnostics.add(new AclfContingencyDiagnostic(
                    null, "WARN", "ANALYSIS_CANCELLED", message));
            return List.of();
        }
    }

    private List<CaseResult<TR>> analyzeSequential(
            List<String> branchIds,
            AclfContingencyConfig config,
            boolean snapshotSupported) {
        List<CaseResult<TR>> results = new ArrayList<>(branchIds.size());
        for (String branchId : branchIds) {
            results.add(snapshotSupported
                    ? analyzeIsolated(branchId, config)
                    : analyzeOnSource(branchId, config));
        }
        return results;
    }

    private CaseResult<TR> analyzeIsolated(String branchId, AclfContingencyConfig config) {
        try {
            BaseAclfNetwork<?, ?> copy = BaseAclfNetworkSnapshotFactory.createCopy(network);
            copy.getBranch(branchId).setStatus(false);
            return new CaseResult<>(branchId, runLoadflow(copy, config), List.of());
        } catch (Exception ex) {
            log.error("Error processing contingency {}", branchId, ex);
            return failedCase(branchId, ex);
        }
    }

    private CaseResult<TR> analyzeOnSource(String branchId, AclfContingencyConfig config) {
        boolean originalStatus = network.getBranch(branchId).isActive();
        try {
            network.getBranch(branchId).setStatus(false);
            return new CaseResult<>(branchId, runLoadflow(network, config), List.of());
        } catch (Exception ex) {
            log.error("Error processing sequential contingency {}", branchId, ex);
            return failedCase(branchId, ex);
        } finally {
            network.getBranch(branchId).setStatus(originalStatus);
        }
    }

    @SuppressWarnings("unchecked")
    private TR runLoadflow(BaseAclfNetwork<?, ?> caseNetwork,
            AclfContingencyConfig config) throws Exception {
        LoadflowAlgorithm algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(caseNetwork);
        config.applySolverOptions(algorithm, hasImportedProfile(caseNetwork));
        algorithm.setContingencyMismatchReportingEnabled(true);
        boolean converged = algorithm.loadflow();
        return (TR) new AclfContingencyResultRec(converged, algorithm.getTerminationReport());
    }

    private static boolean hasImportedProfile(BaseAclfNetwork<?, ?> network) {
        return network.getExtraInfo().get(LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY)
                instanceof LoadflowAlgorithmInitializer;
    }

    private CaseResult<TR> failedCase(String branchId, Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
        return new CaseResult<>(branchId, null, List.of(new AclfContingencyDiagnostic(
                branchId, "ERROR", "CONTINGENCY_FAILED", message)));
    }
    
    /**
     * Convenience method with default configuration and parallel processing enabled
     */
    public AclfContingencyResultContainer<TR> analyzeContingencies(int totalCases) {
        return analyzeContingencies(totalCases, new AclfContingencyConfig(), true);
    }
    
    /**
     * Convenience method with default configuration and configurable parallel processing
     */
    public AclfContingencyResultContainer<TR> analyzeContingencies(int totalCases, boolean useParallel) {
        return analyzeContingencies(totalCases, new AclfContingencyConfig(), useParallel);
    }
    
    /**
     * Print detailed results - useful for debugging from Python
     */
    public static <TR extends AclfContingencyResultRec> void printDetailedResults(AclfContingencyResultContainer<TR> result) {
        System.out.println("\n=== Detailed Contingency Analysis Results ===");
        System.out.println("Total Cases: " + result.getTotalCases());
        System.out.println("Successful Cases: " + result.getTotalSuccessCount());
        System.out.println("Success Rate: " + String.format("%.2f%%", result.getSuccessRate() * 100));
        System.out.println("Planning-Reportable Cases: " + result.getReportableCaseCount());
        System.out.println("Execution Time: " + result.getExecutionTimeSeconds() + " seconds");
        System.out.println("\nIndividual Results:");
        
        result.getCAResults().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                var outage = entry.getValue();
                var termination = outage.getTerminationReport();
                System.out.println("Branch " + entry.getKey() + ": " + outage.reportStatus()
                        + " reportable=" + outage.isReportable()
                        + " termination=" + termination.status()
                        + " unsettledControls=" + termination.unsettledControlCount()
                        + " details=" + termination.unsettledControlDetails());
            });
        System.out.println("============================================\n");
    }
}
