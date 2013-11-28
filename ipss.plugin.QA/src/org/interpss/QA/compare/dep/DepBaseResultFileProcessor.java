package org.interpss.QA.compare.dep;

import org.interpss.QA.result.QABranchRec;
import org.interpss.QA.result.QABusRec;
import org.interpss.QA.result.QAResultContainer;
import org.interpss.util.ITextFileProcessor;

/**
 * Base class for implementing result file loader. The info in the reuslt file are stored in the
 * 
 * 
 * @author mzhou
 *
 * @param <TBusRec>
 * @param <TBranchRec>
 */

@Deprecated
public abstract class DepBaseResultFileProcessor<TBusRec extends QABusRec, TBranchRec extends QABranchRec> implements ITextFileProcessor {

	protected QAResultContainer<TBusRec, TBranchRec> qaResultSet = null;

	public QAResultContainer<TBusRec, TBranchRec> getQAResultSet() {
		return this.qaResultSet;
	}
	
	protected double getDbl(String str) {
		if (str.trim().equals(""))
			return 0.0;
		else
			return new Double(str).doubleValue();
	}	
}
