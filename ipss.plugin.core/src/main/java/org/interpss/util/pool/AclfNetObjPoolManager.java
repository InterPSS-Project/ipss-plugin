package org.interpss.util.pool;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import com.interpss.core.aclf.AclfNetwork;

/**
 * AclfNetwork Object Pool Manager
 * 
 * @author mzhou
 *
 */
public class AclfNetObjPoolManager {
	// The object pool
    private ObjectPool<AclfNetwork> pool;
    
    /**
	 * Constructor
	 * 
	 * @param seedAclfNet The seed AclfNetwork object
	 * @param config The pool configuration
	 */
    public AclfNetObjPoolManager(AclfNetwork seedAclfNet, GenericObjectPoolConfig<AclfNetwork> config) {
        // Initialize the pool with the factory and config
        this.pool = new GenericObjectPool<>(new PooledAclfNetObjectFactory(seedAclfNet), config);
    }
    
    /**
	 * Get the AclfNetwork object pool
	 * 
	 * @return The AclfNetwork object pool
	 */
    public ObjectPool<AclfNetwork> getPool() {
        return this.pool;
    }
    
    public static GenericObjectPoolConfig<AclfNetwork> createConfig() {
        // Create a configuration object
        GenericObjectPoolConfig<AclfNetwork> config = new GenericObjectPoolConfig<>();
        
        // --- Essential Configurations ---
        
        // Maximum number of objects that can be allocated by the pool (active + idle)
        config.setMaxTotal(10); 
        
        // Maximum number of objects that can remain idle in the pool
        config.setMaxIdle(3); 
        
        // Minimum number of idle objects to maintain in the pool
        config.setMinIdle(1); 
        
        // Block (wait) when the pool is exhausted (MaxTotal reached)
        config.setBlockWhenExhausted(true); 
        
        // Maximum wait time in milliseconds for pool.borrowObject()
        config.setMaxWaitMillis(5000); 
        
		return config;
    }
}

