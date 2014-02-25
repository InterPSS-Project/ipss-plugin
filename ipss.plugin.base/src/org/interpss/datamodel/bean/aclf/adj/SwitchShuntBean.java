package org.interpss.datamodel.bean.aclf.adj;

import java.util.ArrayList;
import java.util.List;

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.numeric.util.NumericUtil;

/**
 * bean class for storing switch shunt info
 * 
 * @author sHou
 *
 */
public class SwitchShuntBean extends BaseJSONBean {
	
	/**
	 * switch shunt control type  
	 */
	public static enum VarCompensatorControlModeBean {Fixed, Discrete, Continuous};	
	
	public VarCompensatorControlModeBean controlMode;		// control mode
	
	public String remoteBusId;							// remote control bus id
	
	public double 											// control voltage limit
		vmax,
		vmin;
	public double
		qmax,
		qmin;
	
	public double vSpecified; //
	public double bInit;									// initial b value
	
	public ArrayList<QBankBean> varBankList;				// var bank list
	
	public SwitchShuntBean(){
		varBankList = new ArrayList<QBankBean>();
	}

	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		SwitchShuntBean bean = (SwitchShuntBean)b;

		String str = "ID: " + this.id + " SwitchShuntBean.";
		
		if (!this.remoteBusId.equals(bean.remoteBusId)) {
			logCompareMsg(str + "remoteBusNumber is not equal, " + this.remoteBusId + ", " + bean.remoteBusId); eql = 1; }

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
		
		
		if (this.controlMode.compareTo(bean.controlMode) != 0) {
			logCompareMsg(str + "control mode is not equal"); eql = 1; }
		
		/*if (this.varBankList != bean.varBankList) {
			logCompareMsg(str + "varBankList is not equal"); eql = 1; }*/		
		
		return eql;
	}	
	
	
	@Override
	public boolean validate(List<String> msgList) {
		// TODO Auto-generated method stub
		return false;
	}
	

}
