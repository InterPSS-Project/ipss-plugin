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
			ratio = new BranchValueBean(1.0,1.0),				// xfr branch turn ratio
			ang = new BranchValueBean(0.0,0.0);				// PsXfr shifting angle
	
	
	public AclfBranchBean() {}
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		AclfBranchBean bean = (AclfBranchBean)b;

		if (this.ratio.compareTo(bean.ratio) != 0) {
			IpssLogger.ipssLogger.warning("AclfBranchBean.ratio is not equal");	eql = 1; }

		if (this.ang.compareTo(bean.ang) != 0) {
			IpssLogger.ipssLogger.warning("AclfBranchBean.ang is not equal");	eql = 1; }

		return eql;
	}	
	
	public boolean validate(List<String> msgList) { return true; }
}
