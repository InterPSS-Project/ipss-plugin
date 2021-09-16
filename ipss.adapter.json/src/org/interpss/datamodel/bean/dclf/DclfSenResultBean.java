package org.interpss.datamodel.bean.dclf;

import java.util.ArrayList;
import java.util.List;

import org.interpss.datamodel.bean.base.BaseJSONBean;
import org.interpss.datamodel.bean.base.BaseJSONUtilBean;

public class DclfSenResultBean<TExt extends BaseJSONUtilBean> extends BaseJSONBean<TExt> {	
	

	public List<GSFResultBean<TExt>> gsf_list; // bus bean list
	public List<LODFResultBean<TExt>> lodf_list; // branch bean list	

	public List<CAResultBean<TExt>> ca_list;
	
	public DclfSenResultBean() {
		gsf_list = new ArrayList<GSFResultBean<TExt>>();
		lodf_list = new ArrayList<LODFResultBean<TExt>>();
		ca_list = new ArrayList<CAResultBean<TExt>>();
	}

	@Override
	public boolean validate(List<String> msgList) {		
		return true;
	}
}
