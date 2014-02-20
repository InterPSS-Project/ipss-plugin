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
	
	public long remoteBusNumber;							// remote control bus number
	
	public double 											// control voltage limit
		vmax,
		vmin;
	
	public double bInit;									// initial b value
	
	public ArrayList<QBankBean> varBankList;				// var bank list
	

	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		SwitchShuntBean bean = (SwitchShuntBean)b;

		String str = "ID: " + this.id + " SwitchShuntBean.";
		
		if (this.remoteBusNumber != bean.remoteBusNumber) {
			logCompareMsg(str + "remoteBusNumber is not equal, " + this.remoteBusNumber + ", " + bean.remoteBusNumber); eql = 1; }

		if (!NumericUtil.equals(this.vmax, bean.vmax, PU_ERR)) {
			logCompareMsg(str + "vmax is not equal, " + this.vmax + ", " + bean.vmax); eql = 1; }
		if (!NumericUtil.equals(this.vmin, bean.vmin, PU_ERR)) {
			logCompareMsg(str + "vmin is not equal, " + this.vmin + ", " + bean.vmin); eql = 1;	}
		if (!NumericUtil.equals(this.bInit, bean.bInit, ANG_ERR)) {
			logCompareMsg(str + "bInit is not equal, " + this.bInit + ", " + bean.bInit); eql = 1; }
		
		
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
