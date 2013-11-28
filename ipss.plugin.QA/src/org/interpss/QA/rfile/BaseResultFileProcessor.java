package org.interpss.QA.rfile;

import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
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

public abstract class BaseResultFileProcessor implements ITextFileProcessor {
	public static enum Version {
		PSSE_ALL, PWD_ALL,
	}

	protected Version version;
	
	protected AclfNetResultBean qaResultSet = null;

	public AclfNetResultBean getQAResultSet() {
		return this.qaResultSet;
	}
	
	protected double getDbl(String str) {
		if (str.trim().equals(""))
			return 0.0;
		else
			return new Double(str).doubleValue();
	}	
}
