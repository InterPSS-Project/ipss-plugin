package org.interpss.dep.datamodel.bean.aclf.adj;

import java.util.ArrayList;
import java.util.List;

import org.interpss.dep.datamodel.bean.base.CompareBaseJSONBean;
import org.interpss.dep.datamodel.bean.base.BaseJSONUtilBean;
import org.interpss.numeric.util.NumericUtil;

/**
 * bean class for storing switch shunt info
 * 
 * @author sHou 
 * @param <TExt> template for extension info 
 */
public class SwitchShuntBean<TExt extends BaseJSONUtilBean> extends CompareBaseJSONBean<TExt> {
	
	/**
	 * switch shunt control type  
	 */
	public static enum VarCompensatorControlModeBean {Fixed, Discrete, Continuous};	
	
	public VarCompensatorControlModeBean controlMode;		// control mode
	
	public String remoteBusId;							    // remote control bus id

	public int status = 1;									// 1: in-service, 0: out-of-service
	
	public double 											// control voltage limit
		vmax,
		vmin;
	public double
		qmax,
		qmin;
	
	public double vSpecified; //
	public double bInit;									// initial b value
	
	public ArrayList<QBankBean<TExt>> varBankList;				// var bank list
	
	public SwitchShuntBean(){
		varBankList = new ArrayList<QBankBean<TExt>>();
	}

	@Override public int compareTo(CompareBaseJSONBean<TExt> b) {
		int eql = super.compareTo(b);
		
		SwitchShuntBean<TExt> bean = (SwitchShuntBean<TExt>)b;

		String str = "ID: " + this.id + " SwitchShuntBean.";
		
		if (!java.util.Objects.equals(this.remoteBusId, bean.remoteBusId)) {
			logCompareMsg(str + "remoteBusNumber is not equal, " + this.remoteBusId + ", " + bean.remoteBusId); eql = 1; }

		if (this.status != bean.status) {
			logCompareMsg(str + "status is not equal, " + this.status + ", " + bean.status); eql = 1; }

		if (!NumericUtil.equals(this.vmax, bean.vmax, PU_ERR)) {
			logCompareMsg(str + "vmax is not equal, " + this.vmax + ", " + bean.vmax); eql = 1; }
		if (!NumericUtil.equals(this.vmin, bean.vmin, PU_ERR)) {
			logCompareMsg(str + "vmin is not equal, " + this.vmin + ", " + bean.vmin); eql = 1;	}
		if (!NumericUtil.equals(this.qmax, bean.qmax, PU_ERR)) {
			logCompareMsg(str + "qmax is not equal, " + this.qmax + ", " + bean.qmax); eql = 1; }
		if (!NumericUtil.equals(this.qmin, bean.qmin, PU_ERR)) {
			logCompareMsg(str + "qmin is not equal, " + this.qmin + ", " + bean.qmin); eql = 1;	}
		if (!NumericUtil.equals(this.bInit, bean.bInit, PU_ERR)) {
			logCompareMsg(str + "bInit is not equal, " + this.bInit + ", " + bean.bInit); eql = 1; }
		
		if (!NumericUtil.equals(this.vSpecified, bean.vSpecified, PU_ERR)) {
			logCompareMsg(str + "vSpecified is not equal, " + this.vSpecified + ", " + bean.vSpecified); eql = 1;	}
		
		
		if (this.controlMode != bean.controlMode) {
			logCompareMsg(str + "control mode is not equal"); eql = 1; }
		
		if (this.varBankList == null && bean.varBankList != null ||
				this.varBankList != null && bean.varBankList == null) {
			logCompareMsg(str + "varBankList is not equal"); eql = 1;
		}
		else if (this.varBankList != null && bean.varBankList != null) {
			if (this.varBankList.size() != bean.varBankList.size()) {
				logCompareMsg(str + "varBankList size is not equal, " + this.varBankList.size()
						+ ", " + bean.varBankList.size()); eql = 1;
			}
			else {
				for (int i = 0; i < this.varBankList.size(); i++) {
					if (this.varBankList.get(i).compareTo(bean.varBankList.get(i)) != 0) eql = 1;
				}
			}
		}
		
		return eql;
	}	
	
	
	@Override
	public boolean validate(List<String> msgList) {
		// TODO Auto-generated method stub
		return false;
	}
	

}
