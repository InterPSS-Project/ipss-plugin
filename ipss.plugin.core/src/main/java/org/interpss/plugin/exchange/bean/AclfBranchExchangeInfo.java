package org.interpss.plugin.exchange.bean;

/**
 * Aclf branch result info bean
 * 
 * @author mzhou
 *
 */
public class AclfBranchExchangeInfo extends BaseElemExchangeBean {
	// branch flow results in MVA
	public double[] p_f2t;
	public double[] q_f2t;
	public double[] p_t2f;
	public double[] q_t2f;
	
	/** Constructor
	 * 
	 * @param ids the branch ids
	 */
	public AclfBranchExchangeInfo(String[] ids) {
		super(ids);
	}
}
