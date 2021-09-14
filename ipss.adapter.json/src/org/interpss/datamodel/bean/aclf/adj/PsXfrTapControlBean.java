package org.interpss.datamodel.bean.aclf.adj;

import java.util.List;

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.numeric.util.NumericUtil;

public class PsXfrTapControlBean extends BaseTapControlBean{

	public double 
		maxAngle = 0.0,					// max angle
		minAngle = 0.0;					// min angle
	
	public boolean flowFrom2To = true;
	
	//public TapControlModeBean controlMode = TapControlModeBean.MW_Flow;
	
	public PsXfrTapControlBean(){}
	
	@Override public int compareTo(BaseJSONBean b) {
		
		int eql = super.compareTo(b);
		
		PsXfrTapControlBean bean = (PsXfrTapControlBean)b;

		String str = "ID: " + this.id + " PsXfrTapControlBean.";				
		
		if (!NumericUtil.equals(this.maxAngle, bean.maxAngle, PU_ERR)) {
			logCompareMsg(str + "maxAngle is not equal, " + this.maxAngle + ", " + bean.maxAngle); eql = 1; }
		if (!NumericUtil.equals(this.minAngle, bean.minAngle, PU_ERR)) {
			logCompareMsg(str + "minAngle is not equal, " + this.minAngle + ", " + bean.minAngle); eql = 1; }
		if (this.flowFrom2To != bean.flowFrom2To){
			logCompareMsg(str + "flowFrom2To is not equal, " + this.flowFrom2To + ", " + bean.flowFrom2To); eql = 1; }
			
		return eql;
	}	
	
	@Override
	public boolean validate(List<String> msgList) { return true; }
}
