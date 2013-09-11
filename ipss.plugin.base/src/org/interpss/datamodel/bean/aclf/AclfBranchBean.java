package org.interpss.datamodel.bean.aclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.BaseBranchBean.BranchCode;
import org.interpss.datamodel.bean.datatype.BranchValueBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;

import com.interpss.common.util.IpssLogger;

public class AclfBranchBean extends BaseBranchBean {
	
	
	public BranchValueBean 
	ratio,				// xfr branch turn ratio
	ang;				// PsXfr shifting angle
	
	
	public AclfBranchBean() {}
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		AclfBranchBean bean = (AclfBranchBean)b;

		if (this.f_num != bean.f_num) {
			IpssLogger.ipssLogger.warning("AclfBranchBean.f_num is not equal");
			eql = 1;
		}

		return eql;
	}	
	
	public boolean validate(List<String> msgList) { return true; }
}
