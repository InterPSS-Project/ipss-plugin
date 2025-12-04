package org.interpss.plugin.exchange.bean;

/**
 * Aclf bus result info bean
 * 
 * @author mzhou
 *
 */
public class AclfNetExchangeInfo {
	// network info exchange bean id
	public String netId;
	// network info exchange beanname
	public String netName;
	// network info exchange beandescription
	public String desc;
	
	// flag to indicate whether to include the element (Bus/Branch ...) info in the exchange
	public boolean hasElemInfo = true;

	// flag to indicate whether the load flow converged
	public boolean lfConverged = true;
	
	// bus result info bean
	public AclfBusExchangeInfo busResultBean;
	
	// branch result info bean
	public AclfBranchExchangeInfo branchResultBean;
	
	/** Constructor
	 * 
	 * @param id the network id
	 * @param name the network name
	 * @param desc the network description
	 */
	public AclfNetExchangeInfo(String id, String name, String desc) {
		this.netId = id;
		this.netName = name;
		this.desc = desc;
	}
}
