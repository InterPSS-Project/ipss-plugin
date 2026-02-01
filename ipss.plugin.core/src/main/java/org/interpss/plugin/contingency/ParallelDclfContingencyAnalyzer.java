package org.interpss.plugin.contingency;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.interpss.plugin.contingency.result.DclfContingencyResultRec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.net.ref.impl.NetworkRefImpl;

public class ParallelDclfContingencyAnalyzer  extends NetworkRefImpl<AclfNetwork>{
    //create logger
    private static final Logger log = LoggerFactory.getLogger(ParallelDclfContingencyAnalyzer.class);

    /**
	 * Constructor
	 * 
	 * @param net  the AC load flow network
	 */
	public ParallelDclfContingencyAnalyzer(AclfNetwork net) {
		setNetwork(net);
	}
    
    /**
	 * Core contingency analysis method without GUI dependencies.
	 * 
	 * @param aclfNet The AC load flow network
	 * @param contingencyList List of contingencies to analyze
	 * @param monitoredBranchIds Set of branch IDs to monitor for violations (null = monitor all)
	 * @param violatingLoadingPercent Threshold for violation detection
	 * @param parallelismLevel Number of threads to use for parallel execution
	 * @return ConcurrentLinkedQueue containing all contingency results that exceed threshold
	 */
	public static ConcurrentLinkedQueue<DclfContingencyResultRec> executeContingencyAnalysis(
	        AclfNetwork aclfNet,
	        List<Contingency> contingencyList,
	        Set<String> monitoredBranchIds,
            DclfContingencyConfig config,
	        int parallelismLevel) {
	    
	    log.debug("Starting core contingency analysis with {} contingencies, {} monitored branches, {} parallelism",
	            contingencyList.size(), 
	            monitoredBranchIds == null ? "all" : monitoredBranchIds.size(),
	            parallelismLevel);
	    
	    // Create DCLF algorithm
	    ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet);
	    dclfAlgo.calculateDclf(config.isDclfInclLoss()? DclfMethod.INC_LOSS : DclfMethod.STD);
	    
	    // Use thread-safe collection for parallel processing
	    ConcurrentLinkedQueue<DclfContingencyResultRec> resultRecords = new ConcurrentLinkedQueue<>();
	    
	    // Define result processor
	    Consumer<BranchCAResultRec> resultProcessor = resultRec -> {
	        // If monitoring only selected branches, check if this branch is in the monitored list
	        if (monitoredBranchIds != null && !monitoredBranchIds.contains(resultRec.aclfBranch.getId())) {
	            return; // Skip this branch - not monitored
	        }
	        
	        if (resultRec.calLoadingPercent() >= config.getOverloadThreshold()) {	            
	            // Create DclfContingencyResultRec object
	            DclfContingencyResultRec caResult = new DclfContingencyResultRec(
	                resultRec.aclfBranch.getId(),
	                resultRec.contingency.getId().replaceFirst("contBranch:", ""),
	                resultRec.getPostFlowMW(),
	                resultRec.aclfBranch.getRatingMva1(), // Line rating in MVA
	                resultRec.calLoadingPercent()
	            );
	            
	            // Thread-safe add without explicit synchronization
	            resultRecords.add(caResult);
	        }
	    };
	    
	    // Execute contingency analysis with configured parallelism
	    executeParallel(
	        contingencyList.stream(),
	        contingency -> {
	            ContingencyAnalysisMonad.of(dclfAlgo, contingency)
	                .ca(resultProcessor);
	        },
	        parallelismLevel
	    );
	    
	    log.info("Dclf contingency analysis completed. Found {} violations out of {} contingencies",
	            resultRecords.size(), contingencyList.size());
	    
	    return resultRecords;
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
        if (parallelism == 1) {
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
                customPool.shutdown();
            }
        }
    }
}
