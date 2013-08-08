package org.interpss.datamodel.bean.dclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.BaseJSONBean;

public class CAResultBean extends BaseJSONBean{
	
	public String branchId;
	
	public double preFlow, postFlow,limit, loading;

	@Override
	public boolean validate(List<String> msgList) {		
		return true;
	}	

}
