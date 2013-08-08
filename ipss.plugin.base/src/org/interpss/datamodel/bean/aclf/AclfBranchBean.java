package org.interpss.datamodel.bean.aclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.BaseBranchBean.BranchCode;
import org.interpss.datamodel.bean.datatype.BranchValueBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;

public class AclfBranchBean extends BaseBranchBean {
	
	
	public BranchValueBean 
	ratio,				// xfr branch turn ratio
	ang;				// PsXfr shifting angle
	
	
	public AclfBranchBean() {}
	
	public boolean validate(List<String> msgList) { return true; }
}
