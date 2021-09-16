package org.interpss.datamodel.bean.dclf;

import java.util.List;

import org.interpss.datamodel.bean.base.BaseJSONBean;
import org.interpss.datamodel.bean.base.BaseJSONUtilBean;

public class LODFResultBean<TExt extends BaseJSONUtilBean> extends BaseJSONBean<TExt>{
	
	public String outageBranchId;
	public String monBranchId;
	public double lodf;

	@Override
	public boolean validate(List<String> msgList) {		
		return true;
	}

}
