package sample.contingency;


import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.stream.IntStream;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
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
        String filename = "testData/psse/v33/ACTIVSg25k.RAW";

        AclfNetwork seedAclfNet = IpssAdapter.importAclfNet(filename)
                .setFormat(PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
                .load()
                .getImportedObj();
        
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(seedAclfNet);

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
    	
    	runParallelTasks(pool);
    }
    
    public static void runParallelTasks(ObjectPool<AclfNetwork> pool) {
    	IntStream.range(0, 100)
         	.parallel() // // Convert it into a parallel stream
	        .forEach(taskId -> {    // Perform the task on each element
	             AclfNetwork aclfNet = null;

	             try {
	                 // 1. Borrow an object from the pool (or create a new one if necessary)
	                 aclfNet = pool.borrowObject();

	                 // 2. Use the object to do the required work
	                 System.out.println("Performing CA on AclfNetwork instance: " + taskId);
	                 // TODO
	                 Thread.sleep(1000);

	                 // 3. add any cleanup code here if necessary before returning the object
	                 // TODO
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
	         });
    }
}
