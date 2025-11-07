package org.interpss.util.pool;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.compare.AclfNetObjectUpdater;


public class PooledAclfNetObjectFactory extends BasePooledObjectFactory<AclfNetwork> {
	// Seed AclfNetwork object to create copies from
	private AclfNetwork seedAclfNet = null;
	
	/**
	 * Constructor
	 * 
	 * @param net The seed AclfNetwork object
	 */
	public PooledAclfNetObjectFactory(AclfNetwork net) {
		this.seedAclfNet = net;
	}

    // Create a new instance of your object
    @Override
    public AclfNetwork create() throws Exception {
        // Put logic here to create your expensive object
        return this.seedAclfNet.jsonCopy();
    }

    // Wrap the object in a PooledObject
    @Override
    public PooledObject<AclfNetwork> wrap(AclfNetwork aclfNet) {
        return new DefaultPooledObject<AclfNetwork>(aclfNet);
    }
    
    // Called when an object is returned to the pool (e.g., reset its state)
    @Override
    public void passivateObject(PooledObject<AclfNetwork> pooledObject) {
    	// Reset the object state using the seed object
    	new AclfNetObjectUpdater(this.seedAclfNet, pooledObject.getObject()).update();
    }
    
    /*
    // Called to validate an object before it's borrowed or returned
    @Override
    public boolean validateObject(PooledObject<MyObject> pooledObject) {
        return pooledObject.getObject().isValid(); // Example
    }
    
    // Called when an object is "destroyed" (removed from the pool)
    @Override
    public void destroyObject(PooledObject<MyObject> pooledObject) throws Exception {
        pooledObject.getObject().closeResources(); // Example
    }
    */
}

