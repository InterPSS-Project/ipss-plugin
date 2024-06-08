package org.interpss.mvel;

import com.interpss.core.aclf.AclfNetwork;

/**
 * AclfNetwork MEVL expression evaluator implementation.
 *
 */
public class AclfNetMvelExprEvaluator extends BaseMvelExprEvaluator {
	private AclfNetwork aclfNet;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet
	 */
	public AclfNetMvelExprEvaluator(AclfNetwork aclfNet) {
		super();
		this.aclfNet = aclfNet;
		this.context.put("aclfnet", aclfNet);
	}
}
