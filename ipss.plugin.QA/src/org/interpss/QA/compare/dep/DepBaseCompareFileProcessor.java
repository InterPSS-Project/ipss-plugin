package org.interpss.QA.compare.dep;

import java.util.ArrayList;
import java.util.List;

import org.interpss.QA.result.QABranchRec;
import org.interpss.QA.result.QABusRec;
import org.interpss.QA.result.QAResultContainer;

import com.interpss.core.aclf.AclfNetwork;

@Deprecated
public abstract class DepBaseCompareFileProcessor<TBusRec extends QABusRec, TBranchRec extends QABranchRec> implements IQAFileProcessor {
	protected QAProcessType procType = QAProcessType.CompareResult;
	
	// if true, only compare network structure
	protected boolean compareNetworkOnly = false;
	
	protected QAResultContainer<TBusRec, TBranchRec> qaResultSet = null;

	protected AclfNetwork net = null;
	protected int totalBus = 0, busDataLineNo = 0;
	
	protected int busNo;
	protected String busId;
	protected double baseMva, busVoltage, busAngle, busP, busQ, shuntQ;
	
	protected boolean baseKvaProcessed = false,
    busDataBegin = false;

	protected List<String> errMsgList = new ArrayList<String>();
	
	public int getTotalBus() {
		return this.totalBus;
	}	
	
	public List<String> getErrMsgList() {
		return this.errMsgList;
	}
	
	protected void addErrMsg(String msg) {
		this.errMsgList.add("\n" + msg);
	}

	protected void addErrMsg(String msg, String lineStr) {
		this.errMsgList.add("\n" + msg + "\n                                         ->" + lineStr);
	}
	
	public void postProcessing() {
		//do nothing
	}

	public QAResultContainer<TBusRec, TBranchRec> getQAResultSet() {
		return this.qaResultSet;
	}
}
