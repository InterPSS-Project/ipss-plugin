package org.interpss.QA.compare.impl;

import org.interpss.datamodel.bean.aclf.AclfNetBean;

import com.interpss.core.aclf.AclfNetwork;


public class PSLFResultComparator extends AclfResultComparator {
	public PSLFResultComparator(AclfNetwork net, AclfNetBean qaResultSet) {
		super(net, qaResultSet);
		this.resultType = ResultFileType.PSLF;
	}
}
