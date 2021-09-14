package org.interpss.datamodel.bean.aclf.adj;

import java.util.List;

import org.interpss.datamodel.bean.BaseJSONBean;

/**
 * bean class for storing qbank info
 * 
 * @author sHou
 *
 */
public class QBankBean extends BaseJSONBean {
	
	public int step;
	
	public double UnitQMvar;

	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		QBankBean bean = (QBankBean)b;
		
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
