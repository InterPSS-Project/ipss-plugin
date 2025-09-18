package org.interpss.plugin.contingency;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Parallel Contingency Analysis wrapper for Python integration.
 * This class provides thread-safe parallel contingency analysis capabilities
 * that can be easily called from Python using JPype.
 * 
 * @author InterPSS Team
 */
public class ParallelContingencyAnalyzer {
    
    /**
     * Configuration class for contingency analysis parameters
     */
    public static class ContingencyConfig {
        private boolean turnOffIslandBus = true;
        private boolean autoTurnLine2Xfr = true;
        private AclfMethodType lfMethod = AclfMethodType.NR;
        private boolean applyAdjustAlgo = false;
        private boolean nonDivergent = true;
        private int maxIterations = 50;
        private double tolerance = 0.005;
        
        // Getters and setters
        public boolean isTurnOffIslandBus() { return turnOffIslandBus; }
        public void setTurnOffIslandBus(boolean turnOffIslandBus) { this.turnOffIslandBus = turnOffIslandBus; }
        
        public boolean isAutoTurnLine2Xfr() { return autoTurnLine2Xfr; }
        public void setAutoTurnLine2Xfr(boolean autoTurnLine2Xfr) { this.autoTurnLine2Xfr = autoTurnLine2Xfr; }
        
        public AclfMethodType getLfMethod() { return lfMethod; }
        public void setLfMethod(AclfMethodType lfMethod) { this.lfMethod = lfMethod; }
        
        public boolean isApplyAdjustAlgo() { return applyAdjustAlgo; }
        public void setApplyAdjustAlgo(boolean applyAdjustAlgo) { this.applyAdjustAlgo = applyAdjustAlgo; }
        
        public boolean isNonDivergent() { return nonDivergent; }
        public void setNonDivergent(boolean nonDivergent) { this.nonDivergent = nonDivergent; }
        
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        
        public double getTolerance() { return tolerance; }
        public void setTolerance(double tolerance) { this.tolerance = tolerance; }
    }
    
    /**
     * Result class for contingency analysis
     */
    public static class ContingencyResult {
        private final Map<String, Boolean> convergenceResults;
        private final long totalSuccessCount;
        private final int totalCases;
        private final long executionTimeMs;
        
        public ContingencyResult(Map<String, Boolean> convergenceResults, long totalSuccessCount, 
                               int totalCases, long executionTimeMs) {
            this.convergenceResults = new HashMap<>(convergenceResults);
            this.totalSuccessCount = totalSuccessCount;
            this.totalCases = totalCases;
            this.executionTimeMs = executionTimeMs;
        }
        
        public Map<String, Boolean> getConvergenceResults() { return convergenceResults; }
        public long getTotalSuccessCount() { return totalSuccessCount; }
        public int getTotalCases() { return totalCases; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public double getExecutionTimeSeconds() { return executionTimeMs / 1000.0; }
        public double getSuccessRate() { return (double) totalSuccessCount / totalCases; }
    }
    
    /**
     * Perform parallel contingency analysis by removing branches one at a time.
     * This method is thread-safe and can be called from Python.
     * 
     * @param network The base AC load flow network
     * @param totalCases Number of contingency cases to analyze
     * @param config Configuration parameters for the analysis
     * @param useParallel Whether to use parallel processing
     * @return ContingencyResult containing analysis results
     */
    public static ContingencyResult analyzeContingencies(AclfNetwork network, int totalCases, 
                                                       ContingencyConfig config, boolean useParallel) {
        
        System.out.println("Starting " + (useParallel ? "parallel" : "sequential") + 
                         " contingency analysis with " + totalCases + " cases...");
        System.out.println("Active bus size: " + network.getNoActiveBus());
        System.out.println("Active branch size: " + network.getNoActiveBranch());
        
        long startTime = System.currentTimeMillis();
        
        // Thread-safe map to store results
        Map<String, Boolean> convergenceResults = new ConcurrentHashMap<>();
        
        // Create stream - parallel or sequential based on parameter
        IntStream stream = IntStream.range(0, totalCases);
        if (useParallel) {
            stream = stream.parallel();
        }
        
        long totalSuccessCount = stream
            .mapToObj(i -> {
                try {
                    // Create a copy of the network for each contingency
                    AclfNetwork copyNet = network.jsonCopy();
                    
                    // Remove the i-th branch
                    if (i < copyNet.getBranchList().size()) {
                        copyNet.getBranchList().get(i).setStatus(false);
                        String branchId = copyNet.getBranchList().get(i).getId();
                        
                        // Create a new algorithm instance for each thread to avoid conflicts
                        LoadflowAlgorithm parallelAlgo = CoreObjectFactory.createLoadflowAlgorithm(copyNet);
                        configureAlgorithm(parallelAlgo, config);
                        
                        boolean isConverged = parallelAlgo.loadflow();
                        
                        // Store result in thread-safe map
                        convergenceResults.put(branchId, isConverged);
                        
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
        
        return new ContingencyResult(convergenceResults, totalSuccessCount, totalCases, executionTime);
    }
    
    /**
     * Convenience method with default configuration and parallel processing enabled
     */
    public static ContingencyResult analyzeContingencies(AclfNetwork network, int totalCases) {
        return analyzeContingencies(network, totalCases, new ContingencyConfig(), true);
    }
    
    /**
     * Convenience method with default configuration and configurable parallel processing
     */
    public static ContingencyResult analyzeContingencies(AclfNetwork network, int totalCases, boolean useParallel) {
        return analyzeContingencies(network, totalCases, new ContingencyConfig(), useParallel);
    }
    
    /**
     * Configure the load flow algorithm with the specified parameters
     */
    private static void configureAlgorithm(LoadflowAlgorithm algo, ContingencyConfig config) {
        algo.getDataCheckConfig().setTurnOffIslandBus(config.isTurnOffIslandBus());
        algo.getDataCheckConfig().setAutoTurnLine2Xfr(config.isAutoTurnLine2Xfr());
        algo.setLfMethod(config.getLfMethod());
        algo.getLfAdjAlgo().setApplyAdjustAlgo(config.isApplyAdjustAlgo());
        algo.setNonDivergent(config.isNonDivergent());
        algo.setMaxIterations(config.getMaxIterations());
        algo.setTolerance(config.getTolerance());
    }
    
    /**
     * Create a default configuration instance for Python convenience
     */
    public static ContingencyConfig createDefaultConfig() {
        return new ContingencyConfig();
    }
    
    /**
     * Print detailed results - useful for debugging from Python
     */
    public static void printDetailedResults(ContingencyResult result) {
        System.out.println("\n=== Detailed Contingency Analysis Results ===");
        System.out.println("Total Cases: " + result.getTotalCases());
        System.out.println("Successful Cases: " + result.getTotalSuccessCount());
        System.out.println("Success Rate: " + String.format("%.2f%%", result.getSuccessRate() * 100));
        System.out.println("Execution Time: " + result.getExecutionTimeSeconds() + " seconds");
        System.out.println("\nIndividual Results:");
        
        result.getConvergenceResults().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                System.out.println("Branch " + entry.getKey() + ": " + 
                                 (entry.getValue() ? "CONVERGED" : "FAILED"));
            });
        System.out.println("============================================\n");
    }
}
