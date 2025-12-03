package org.interpss.plugin.exchange.bean;

import com.interpss.core.aclf.AclfBranch;

/**
 * Aclf bus result info bean
 * 
 * @author mzhou
 *
 */
public class ContingencyExchangeInfo extends AclfNetExchangeInfo {
	// the contingency id
	public String continId;
	// the outage branch
	public AclfBranch outageBranch;
	
	/** Constructor
	 * 
	 * @param id the network id
	 * @param name the network name
	 * @param desc the network description
	 * @param continId the contingency id
	 * @param outageBranch the outage branch
	 */
	public ContingencyExchangeInfo(String id, String name, String desc,
			String continId, AclfBranch outageBranch) {
		super(id, name, desc);
		this.continId = continId;
		this.outageBranch = outageBranch;
	}
}
