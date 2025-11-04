package sample.contingency;


import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.interpss.plugin.contingency.ParallelContingencyAnalyzer;
import org.interpss.plugin.contingency.result.ContingencyResultRec;
import org.interpss.plugin.contingency.result.ContingencyResults;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.util.pool.AclfNetObjPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfAdjCtrlFunction;

/**
 * Sample code to demonstrate the usage of Object Pooling for AclfNetwork objects
 * in a contingency analysis scenario.
 * 
 * @author mzhou
 *
 */
public class ObjectPoolSample {
	private static final Logger log = LoggerFactory.getLogger(ObjectPoolSample.class);
    
    public static void main(String[] args) throws InterpssException {
		String filename = "ipss-plugin/ipss.test.plugin.core/testData/psse/v33/ACTIVSg25k.RAW";
        //String filename = "testData/psse/v33/ACTIVSg25k.RAW";

        AclfNetwork seedAclfNet = IpssAdapter.importAclfNet(filename)
                .setFormat(PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
                .load()
                .getImportedObj();
        
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(seedAclfNet);

		aclfAlgo.getDataCheckConfig().setTurnOffIslandBus(true);
		aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		aclfAlgo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(false);

		// disable all the controls
		AclfAdjCtrlFunction.disableAllAdjControls.accept(aclfAlgo);
		
		aclfAlgo.setTolerance(1.0E-6);
		aclfAlgo.setMaxIterations(50);
		
		System.out.println("MaxMismatch: " + seedAclfNet.maxMismatch(AclfMethodType.NR));
		
		aclfAlgo.loadflow();
		

    	//AclfNetwork seedAclfNet = net;
		GenericObjectPoolConfig<AclfNetwork> config = AclfNetObjPoolManager.createConfig();
    	ObjectPool<AclfNetwork> pool = new AclfNetObjPoolManager(seedAclfNet, config)
    										.getPool(); 
    	
    	ContingencyResults<ContingencyResultRec> result = runParallelTasks(pool);

		new ParallelContingencyAnalyzer<ContingencyResultRec>().printDetailedResults(result);
    }
    
    public static ContingencyResults<ContingencyResultRec> runParallelTasks(ObjectPool<AclfNetwork> pool) {

		 long startTime = System.currentTimeMillis();

		    // Thread-safe map to store results
        Map<String, ContingencyResultRec> convergenceResults = new ConcurrentHashMap<>();

		int totalCases = 100;

		long totalSuccessCount = IntStream.range(0, totalCases)
         	.parallel() // // Convert it into a parallel stream
	        .mapToObj(taskId -> {    // Perform the task on each element
	             AclfNetwork aclfNet = null;
	             try {
	                 // 1. Borrow an object from the pool (or create a new one if necessary)
	                 aclfNet = pool.borrowObject();

	                 // 2. Use the object to do the required work
	                 System.out.println("Performing CA on AclfNetwork instance: " + taskId);
					 
	                 // TODO
	                 //Thread.sleep(1000);
					aclfNet.getBranchList().get(taskId).setStatus(false);
					String branchId = aclfNet.getBranchList().get(taskId).getId();

					// Create a new algorithm instance for each thread to avoid conflicts
					LoadflowAlgorithm parallelAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

					parallelAlgo.getDataCheckConfig().setTurnOffIslandBus(true);
					parallelAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

					parallelAlgo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(true);

					// disable all the controls
					AclfAdjCtrlFunction.disableAllAdjControls.accept(parallelAlgo);
					
					parallelAlgo.setTolerance(1.0E-6);
					parallelAlgo.setMaxIterations(50);
					
					
					boolean isConverged = parallelAlgo.loadflow();
					
					// Store result in thread-safe map
					ContingencyResultRec rec = new ContingencyResultRec(isConverged);
					convergenceResults.put(branchId, rec);

	                 // 3. add any cleanup code here if necessary before returning the object
					aclfNet.getBranchList().get(taskId).setStatus(true);	

					 return isConverged;
	             } catch (Exception e) {
	                 // If an exception occurs, the object might be in a bad state.
	                 // Invalidate it so the pool doesn't reuse it.
	                 if (aclfNet != null) {
	                     try {
	                         pool.invalidateObject(aclfNet);
	                         aclfNet = null; // Important: set to null so it's not returned twice
	                     } catch (Exception invalidateEx) {
	                         // log error
	                     	log.error("Error invalidating object from pool", invalidateEx);
	                     }
	                 }
					 return false;
	             } finally {
	                 // 4. ALWAYS return the object to the pool, unless you invalidated it
	                 if (aclfNet != null) {
	                     try {
	                     	// Return the object to the pool
	                         pool.returnObject(aclfNet);
	                     } catch (Exception returnEx) {
	                         // log error
	                     	log.error("Error returning object to pool", returnEx);
	                     }
	                 }
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
        
        return new ContingencyResults(convergenceResults, totalSuccessCount, totalCases, executionTime);
			 

    }
}
