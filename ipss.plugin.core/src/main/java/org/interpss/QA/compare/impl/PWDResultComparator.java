package org.interpss.QA.compare.impl;

import org.interpss.datamodel.bean.aclf.AclfNetBean;

import com.interpss.core.aclf.AclfNetwork;

public class PWDResultComparator extends AclfResultComparator {
	public PWDResultComparator() {
		super();
		this.resultType = ResultFileType.PWD;		
	}

	public PWDResultComparator(AclfNetwork net, AclfNetBean qaResultSet) {
		super(net, qaResultSet);
		this.resultType = ResultFileType.PWD;		
	}
}
