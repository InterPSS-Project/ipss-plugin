package org.interpss.datamodel.bean.dclf;

import java.util.List;

import org.interpss.datamodel.bean.base.BaseJSONBean;
import org.interpss.datamodel.bean.base.BaseJSONUtilBean;

public class GSFResultBean<TExt extends BaseJSONUtilBean> extends BaseJSONBean<TExt> {
	
	public String injBus, withdrawBus, monBranch;
	
	public double gsf;

	@Override
	public boolean validate(List<String> msgList) {		
		return true;
	}

}
