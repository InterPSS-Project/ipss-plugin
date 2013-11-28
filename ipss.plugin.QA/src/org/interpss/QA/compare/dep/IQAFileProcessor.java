package org.interpss.QA.compare.dep;

import java.util.List;

import org.interpss.util.ITextFileProcessor;

public interface IQAFileProcessor extends ITextFileProcessor {
	List<String> getErrMsgList();
	
	int getTotalBus();
}
