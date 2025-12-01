package org.interpss.plugin.contingency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.interpss.plugin.contingency.result.ContingencyResultContainer;
import org.interpss.plugin.contingency.result.ContingencyResultRec;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;
import com.interpss.core.net.ref.impl.NetworkRefImpl;
import com.interpss.state.aclf.AclfNetworkState;

/**
 * Parallel Contingency Analysis wrapper for Python integration.
 * This class provides thread-safe parallel contingency analysis capabilities
 * that can be easily called from Python using JPype.
 * 
 * @author InterPSS Team
 */
public class ParallelContingencyAnalyzer <TR extends ContingencyResultRec> extends NetworkRefImpl<AclfNetwork> { 
	/**
	 * Constructor
	 * 
	 * @param net  the AC load flow network
	 */
	public ParallelContingencyAnalyzer(AclfNetwork net) {
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
    public ContingencyResultContainer<TR> analyzeContingencies(int totalCases, 
                                                       ContingencyConfig config, boolean useParallel) {
        
        System.out.println("Starting " + (useParallel ? "parallel" : "sequential") + 
                         " contingency analysis with " + totalCases + " cases...");
        System.out.println("Active bus size: " + network.getNoActiveBus());
        System.out.println("Active branch size: " + network.getNoActiveBranch());
        
        long startTime = System.currentTimeMillis();
        
        // Thread-safe map to store results
        Map<String, TR> caResults = new ConcurrentHashMap<>();
        
        // Create stream - parallel or sequential based on parameter
        IntStream stream = IntStream.range(0, totalCases);
        if (useParallel) {
            stream = stream.parallel();
        }
        
        AclfNetworkState clonedNetBean = new AclfNetworkState(network);
		
        long totalSuccessCount = stream
            .mapToObj(i -> {
                try {
                    // Create a copy of the network for each contingency
                    //AclfNetwork copyNet = network.jsonCopy();
                	AclfNetwork copyNet = AclfNetworkState.create(clonedNetBean);
                    
                    // Remove the i-th branch
                    if (i < copyNet.getBranchList().size()) {
                        copyNet.getBranchList().get(i).setStatus(false);
                        String branchId = copyNet.getBranchList().get(i).getId();
                        
                        // Create a new algorithm instance for each thread to avoid conflicts
                        LoadflowAlgorithm parallelAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(copyNet);
                        configureAlgorithm(parallelAlgo, config);
                        
                        boolean isConverged = parallelAlgo.loadflow();
                        
                        // Store result in thread-safe map
                        ContingencyResultRec rec = new ContingencyResultRec(isConverged);
                        caResults.put(branchId, (TR)rec);
                        
                        return isConverged;
                    } else {
                        System.err.println("Warning: Contingency index " + i + 
                                         " exceeds branch list size " + copyNet.getBranchList().size());
                        return false;
                    }
                } catch (Exception e) {
                    synchronized(System.err) {
                        System.err.println("Error processing contingency " + i + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                    return false;
                }
            })
            .mapToLong(converged -> converged ? 1 : 0)
            .sum();
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        System.out.println("Contingency analysis completed!");
        System.out.println("Total time: " + (executionTime / 1000.0) + " seconds");
        System.out.println("Total successful contingencies: " + totalSuccessCount + " out of " + totalCases);
        System.out.println("Success rate: " + String.format("%.2f%%", (double) totalSuccessCount / totalCases * 100));
        
        return new ContingencyResultContainer<TR>(caResults, totalSuccessCount, totalCases, executionTime);
    }
    
    /**
     * Convenience method with default configuration and parallel processing enabled
     */
    public ContingencyResultContainer<TR> analyzeContingencies(int totalCases) {
        return analyzeContingencies(totalCases, new ContingencyConfig(), true);
    }
    
    /**
     * Convenience method with default configuration and configurable parallel processing
     */
    public ContingencyResultContainer<TR> analyzeContingencies(int totalCases, boolean useParallel) {
        return analyzeContingencies(totalCases, new ContingencyConfig(), useParallel);
    }
    
    /**
     * Configure the load flow algorithm with the specified parameters
     */
    private static void configureAlgorithm(LoadflowAlgorithm algo, ContingencyConfig config) {
        algo.getDataCheckConfig().setTurnOffIslandBus(config.isTurnOffIslandBus());
        algo.getDataCheckConfig().setAutoTurnLine2Xfr(config.isAutoTurnLine2Xfr());
        algo.setLfMethod(config.getLfMethod());
         // disable all the controls
        if(config.isApplyAdjustAlgo())
            AclfAdjCtrlFunction.disableAllAdjControls.accept(algo);
        algo.getNrMethodConfig().setNonDivergent(config.isNonDivergent());
        algo.setMaxIterations(config.getMaxIterations());
        algo.setTolerance(config.getTolerance());
    }
    
    /**
     * Print detailed results - useful for debugging from Python
     */
    public static <TR extends ContingencyResultRec> void printDetailedResults(ContingencyResultContainer<TR> result) {
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
