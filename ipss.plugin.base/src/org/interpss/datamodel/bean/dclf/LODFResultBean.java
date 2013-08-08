package org.interpss.datamodel.bean.dclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.BaseJSONBean;

public class LODFResultBean extends BaseJSONBean{
	
	public String outageBranchId;
	public String monBranchId;
	public double lodf;

	@Override
	public boolean validate(List<String> msgList) {		
		return true;
	}

}
