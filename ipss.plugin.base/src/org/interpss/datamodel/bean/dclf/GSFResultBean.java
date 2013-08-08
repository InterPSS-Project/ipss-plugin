package org.interpss.datamodel.bean.dclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.BaseJSONBean;

public class GSFResultBean extends BaseJSONBean{
	
	public String injBus, withdrawBus, monBranch;
	
	public double gsf;

	@Override
	public boolean validate(List<String> msgList) {		
		return true;
	}

}
