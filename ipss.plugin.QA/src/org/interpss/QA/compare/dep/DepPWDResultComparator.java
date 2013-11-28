package org.interpss.QA.compare.dep;

import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.QA.result.QAResultContainer;

import com.interpss.core.aclf.AclfNetwork;

@Deprecated
public class DepPWDResultComparator extends DepAclfResultComparator {
	public DepPWDResultComparator() {
		super();
		this.resultType = ResultFileType.PWD;		
	}

	public DepPWDResultComparator(AclfNetwork net, QAResultContainer<QAAclfBusRec, QAAclfBranchRec> qaResultSet) {
		super(net, qaResultSet);
		this.resultType = ResultFileType.PWD;		
	}
}
