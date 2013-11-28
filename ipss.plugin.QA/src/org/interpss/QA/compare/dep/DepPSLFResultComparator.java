package org.interpss.QA.compare.dep;

import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.QA.result.QAResultContainer;

import com.interpss.core.aclf.AclfNetwork;

@Deprecated
public class DepPSLFResultComparator extends DepAclfResultComparator {
	public DepPSLFResultComparator(AclfNetwork net, QAResultContainer<QAAclfBusRec, QAAclfBranchRec> qaResultSet) {
		super(net, qaResultSet);
		this.resultType = ResultFileType.PSLF;
	}
}
