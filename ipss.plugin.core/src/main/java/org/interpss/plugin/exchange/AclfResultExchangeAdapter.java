package org.interpss.plugin.exchange;

import org.interpss.plugin.exchange.bean.AclfNetExchangeInfo;

import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter class to create the Aclf analysis result exchange info beans
 * 
 * @author mzhou
 *
 */
public class AclfResultExchangeAdapter extends BaseResultExchangeAdapter<AclfNetExchangeInfo> {
	/** Constructor
	 * 
	 * @param aclfNet the Aclf network object
	 */
	public AclfResultExchangeAdapter(AclfNetwork aclfNet) {
		super(aclfNet);
	}

	/** Create AclfNetExchangeInfo bean
	 * 
	 * @param busIds the bus ids array
	 * @param branchIds the branch ids array
	 * @return AclfNetExchangeInfo bean
	 */
	@Override
	public AclfNetExchangeInfo createInfoBean(String[] busIds, String[] branchIds) {
		AclfNetExchangeInfo netInfoBean = new AclfNetExchangeInfo(aclfNet.getId(), aclfNet.getName(), aclfNet.getDesc());
		netInfoBean.hasElemInfo = true;
		netInfoBean.lfConverged = this.aclfNet.isLfConverged();
		
		this.createBusResult(netInfoBean, busIds);
		
		this.createBranchResult(netInfoBean, branchIds);
		
		return netInfoBean;
	}
}
