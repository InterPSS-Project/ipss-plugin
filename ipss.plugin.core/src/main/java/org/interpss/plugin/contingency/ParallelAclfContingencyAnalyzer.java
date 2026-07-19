package org.interpss.plugin.contingency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.interpss.plugin.contingency.result.AclfContingencyResultContainer;
import org.interpss.plugin.contingency.result.AclfContingencyResultRec;
import org.interpss.plugin.contingency.result.AclfContingencyDiagnostic;

import com.interpss.common.util.BoundedParallelExecutor;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;
import com.interpss.core.net.ref.impl.NetworkRefImpl;
import com.interpss.state.aclf.snapshot.BaseAclfNetworkSnapshotFactory;

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
	        AclfContingencyDiagnostic diagnostic) {
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
        
        long startTime = System.currentTimeMillis();
        Map<String, TR> caResults = new LinkedHashMap<>();
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
        long totalSuccessCount = 0;
        for (CaseResult<TR> caseResult : caseResults) {
            if (caseResult.result() != null) {
                caResults.put(caseResult.branchId(), caseResult.result());
                if (caseResult.result().isConverged()) {
                    totalSuccessCount++;
                }
            }
            if (caseResult.diagnostic() != null) {
                diagnostics.add(caseResult.diagnostic());
            }
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
            return new CaseResult<>(branchId, runLoadflow(copy, config), null);
        } catch (Exception ex) {
            log.error("Error processing contingency {}", branchId, ex);
            return failedCase(branchId, ex);
        }
    }

    private CaseResult<TR> analyzeOnSource(String branchId, AclfContingencyConfig config) {
        boolean originalStatus = network.getBranch(branchId).isActive();
        try {
            network.getBranch(branchId).setStatus(false);
            return new CaseResult<>(branchId, runLoadflow(network, config), null);
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
        configureAlgorithm(algorithm, config);
        boolean converged = algorithm.loadflow();
        return (TR) new AclfContingencyResultRec(converged);
    }

    private CaseResult<TR> failedCase(String branchId, Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
        return new CaseResult<>(branchId, null, new AclfContingencyDiagnostic(
                branchId, "ERROR", "CONTINGENCY_FAILED", message));
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
     * Configure the load flow algorithm with the specified parameters
     */
    private static void configureAlgorithm(LoadflowAlgorithm algo, AclfContingencyConfig config) {
        algo.getDataCheckConfig().setTurnOffIslandBus(config.isTurnOffIslandBus());
        algo.getDataCheckConfig().setAutoTurnLine2Xfr(config.isAutoTurnLine2Xfr());
        algo.setLfMethod(config.getLfMethod());
         // disable all the controls
        if(config.isApplyAdjustAlgo())
            AclfAdjCtrlFunction.disableAllAdjControls.accept(algo);
        algo.setNonDivergent(config.isNonDivergent());
        algo.setMaxIterations(config.getMaxIterations());
        algo.setTolerance(config.getTolerance());
    }
    
    /**
     * Print detailed results - useful for debugging from Python
     */
    public static <TR extends AclfContingencyResultRec> void printDetailedResults(AclfContingencyResultContainer<TR> result) {
        System.out.println("\n=== Detailed Contingency Analysis Results ===");
        System.out.println("Total Cases: " + result.getTotalCases());
        System.out.println("Successful Cases: " + result.getTotalSuccessCount());
        System.out.println("Success Rate: " + String.format("%.2f%%", result.getSuccessRate() * 100));
        System.out.println("Execution Time: " + result.getExecutionTimeSeconds() + " seconds");
        System.out.println("\nIndividual Results:");
        
        result.getCAResults().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                System.out.println("Branch " + entry.getKey() + ": " + 
                                 (entry.getValue().isConverged() ? "CONVERGED" : "FAILED"));
            });
        System.out.println("============================================\n");
    }
}
