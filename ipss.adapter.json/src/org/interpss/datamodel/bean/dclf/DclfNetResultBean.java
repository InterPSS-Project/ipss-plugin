package org.interpss.datamodel.bean.dclf;

import org.interpss.datamodel.bean.BaseJSONUtilBean;
import org.interpss.datamodel.bean.aclf.BaseAclfNetBean;

public class DclfNetResultBean<TBusExt extends BaseJSONUtilBean, TBraExt extends BaseJSONUtilBean> extends BaseAclfNetBean<DclfBusResultBean<TBusExt>, DclfBranchResultBean<TBraExt>, TBusExt, TBraExt> {	
	
	public DclfNetResultBean() {
		super();
	}
}
