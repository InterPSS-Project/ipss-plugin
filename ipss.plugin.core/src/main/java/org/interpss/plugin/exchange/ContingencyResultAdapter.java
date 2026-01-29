package org.interpss.plugin.exchange;

import org.interpss.plugin.exchange.bean.ContingencyExchangeInfo;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter class for the contingency analysis result exchange
 * 
 * @author mzhou
 *
 */
public class ContingencyResultAdapter extends BaseResultExchangeAdapter<ContingencyExchangeInfo> {
	// the contingency id
	public String continId;
	// the outage branch
	public AclfBranch outageBranch;
	
	/** 
	 * Constructor
	 * 
	 * @param aclfNet the Aclf network object
	 * @param continId the contingency id
	 * @param outageBranch the outage branch
	 * 
	 */
	public ContingencyResultAdapter(AclfNetwork aclfNet, String continId, AclfBranch outageBranch) {
		super(aclfNet);
		this.continId = continId;
		this.outageBranch = outageBranch;
	}
	
	/** Create AclfNetExchangeInfo bean
	 * 
	 * @param busIds the bus ids array
	 * @param branchIds the branch ids array
	 * @return AclfNetExchangeInfo bean
	 */
	@Override
	public ContingencyExchangeInfo createInfoBean(String[] busIds, String[] branchIds) {
		ContingencyExchangeInfo netInfoBean = 
				new ContingencyExchangeInfo(aclfNet.getId(), aclfNet.getName(), aclfNet.getDesc(),
						this.continId, this.outageBranch);
		netInfoBean.hasElemInfo = true;
		netInfoBean.lfConverged = this.aclfNet.isLfConverged();
		
		this.createBusResult(netInfoBean, busIds);
		
		this.createBranchResult(netInfoBean, branchIds);
		
		return netInfoBean;
	}
}
