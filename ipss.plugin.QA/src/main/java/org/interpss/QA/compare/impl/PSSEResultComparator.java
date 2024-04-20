package org.interpss.QA.compare.impl;

import org.interpss.datamodel.bean.aclf.AclfNetBean;

import com.interpss.core.aclf.AclfNetwork;


public class PSSEResultComparator extends AclfResultComparator {
	public PSSEResultComparator(AclfNetwork net, AclfNetBean qaResultSet) {
		super(net, qaResultSet);
		this.resultType = ResultFileType.PSSE;		
	}
} 
