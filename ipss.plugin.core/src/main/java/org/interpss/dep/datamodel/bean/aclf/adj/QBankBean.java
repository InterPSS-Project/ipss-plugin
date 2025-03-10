package org.interpss.dep.datamodel.bean.aclf.adj;

import java.util.List;

import org.interpss.dep.datamodel.bean.base.BaseJSONBean;
import org.interpss.dep.datamodel.bean.base.BaseJSONUtilBean;

/**
 * bean class for storing qbank info
 * 
 * @author sHou
 * 
 * @param <TExt> template for extension info 
 */
public class QBankBean<TExt extends BaseJSONUtilBean> extends BaseJSONBean<TExt> {
	
	public int step;
	
	public double UnitQMvar;

	@Override public int compareTo(BaseJSONBean<TExt> b) {
		int eql = super.compareTo(b);
		
		QBankBean<TExt> bean = (QBankBean<TExt>)b;
		
		String str = "ID: " + this.id + " QBankBean.";
		
		if (this.step != bean.step) {
			logCompareMsg(str + "step is not equal, " + this.step + ", " + bean.step); eql = 1; }
		
		if (this.UnitQMvar != bean.UnitQMvar) {
			logCompareMsg(str + "UnitQMvar is not equal, " + this.UnitQMvar + ", " + bean.UnitQMvar); eql = 1; }
		
		return eql;

	}
	@Override
	public boolean validate(List<String> msgList) {
		// TODO Auto-generated method stub
		return false;
	}

}
