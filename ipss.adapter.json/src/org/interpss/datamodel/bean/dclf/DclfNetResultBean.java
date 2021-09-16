package org.interpss.datamodel.bean.dclf;

import org.interpss.datamodel.bean.aclf.BaseAclfNetBean;
import org.interpss.datamodel.bean.base.BaseJSONUtilBean;

public class DclfNetResultBean<TBusExt extends BaseJSONUtilBean, 
                               TBraExt extends BaseJSONUtilBean,
                               TNetExt extends BaseJSONUtilBean> 
                                     extends BaseAclfNetBean<DclfBusResultBean<TBusExt>, 
                                                             DclfBranchResultBean<TBraExt>, 
                                                             TBusExt, TBraExt, TNetExt> {	
	
	public DclfNetResultBean() {
		super();
	}
}
