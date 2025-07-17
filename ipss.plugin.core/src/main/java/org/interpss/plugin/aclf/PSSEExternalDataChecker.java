package org.interpss.plugin.aclf;

import java.util.function.Consumer;

import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * This class is used to perform external data checking for PSSE created AclfNetwork.
 *   
 */
public class PSSEExternalDataChecker implements Consumer<BaseAclfNetwork<?,?>>{
	/**
	 * Constructor
	 * 
	 * @param dataChecker a Consumer that initialize this data checker object
	 */
	public PSSEExternalDataChecker(Consumer<PSSEExternalDataChecker> dataChecker) {
		dataChecker.accept(this);
	}
	
	@Override
	public void accept(BaseAclfNetwork<?,?> aclfNet) {
  	}

}
