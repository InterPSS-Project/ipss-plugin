package org.interpss.datamodel.bean.dclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.BaseJSONUtilBean;

public class CAResultBean<TExt extends BaseJSONUtilBean> extends BaseJSONBean<TExt> {
	
	public String contId;
	
	public String branchId;
	
	public double preFlow, postFlow,limit, loading;

	@Override
	public boolean validate(List<String> msgList) {		
		return true;
	}	

}
