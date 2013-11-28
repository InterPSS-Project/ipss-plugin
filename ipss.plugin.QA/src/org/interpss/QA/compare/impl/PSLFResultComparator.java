package org.interpss.QA.compare.impl;

import org.interpss.datamodel.bean.aclf.AclfNetResultBean;

import com.interpss.core.aclf.AclfNetwork;


public class PSLFResultComparator extends AclfResultComparator {
	public PSLFResultComparator(AclfNetwork net, AclfNetResultBean qaResultSet) {
		super(net, qaResultSet);
		this.resultType = ResultFileType.PSLF;
	}
}
