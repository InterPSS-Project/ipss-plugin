package org.interpss.QA.compare.impl;

import org.interpss.datamodel.bean.aclf.ext.AclfNetResultBean;

import com.interpss.core.aclf.AclfNetwork;


public class PSSEResultComparator extends AclfResultComparator {
	public PSSEResultComparator(AclfNetwork net, AclfNetResultBean qaResultSet) {
		super(net, qaResultSet);
		this.resultType = ResultFileType.PSSE;		
	}
}
