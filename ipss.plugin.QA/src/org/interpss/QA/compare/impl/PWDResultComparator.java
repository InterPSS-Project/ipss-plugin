package org.interpss.QA.compare.impl;

import org.interpss.datamodel.bean.aclf.AclfNetResultBean;

import com.interpss.core.aclf.AclfNetwork;

public class PWDResultComparator extends AclfResultComparator {
	public PWDResultComparator() {
		super();
		this.resultType = ResultFileType.PWD;		
	}

	public PWDResultComparator(AclfNetwork net, AclfNetResultBean qaResultSet) {
		super(net, qaResultSet);
		this.resultType = ResultFileType.PWD;		
	}
}
