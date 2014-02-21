package org.interpss.datamodel.bean.aclf.adj;

import java.util.List;

import org.interpss.datamodel.bean.BaseBusBean;
import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.numeric.util.NumericUtil;

/**
 * bean class for storing aclf load record
 * 
 * @author sHou
 * 
 */

public class AclfLoadBean extends BaseJSONBean {
	
	public String id; 		// load identifier to distinguish from multiple
							// loads connected at the same bus

	public int status; 		// load service status
	
	public ComplexBean 
		constPload,			// constant P load
		constIload,			// constant I load		
		constZload;			// constant Z load

	//public AclfBusBean connectedBus; // the bus that this load is connected to
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		AclfLoadBean bean = (AclfLoadBean)b;

		String str = "ID: " + this.id + " AclfLoadBean.";
		
		if (!this.id.equals(bean.id)) {
			logCompareMsg(str + "id is not equal, " + this.id + ", " + bean.id); eql = 1; }
		
		if (this.status != bean.status){
			logCompareMsg(str + "status is not equal, " + this.status + ", " + bean.status); eql = 1; }	


		if (this.constPload.compareTo(bean.constPload) != 0) {
			logCompareMsg(str + "constant P component is not equal"); eql = 1; }
		if (this.constIload.compareTo(bean.constIload) != 0) {
			logCompareMsg(str + "constant I component is not equal"); eql = 1; }
		if (this.constZload.compareTo(bean.constZload) != 0) {
			logCompareMsg(str + "constant Z component is not equal"); eql = 1; }				
		
		/*if(this.connectedBus.compareTo(bean.connectedBus) != 0 ){
			logCompareMsg(str + "connected bus is not equal, " + this.connectedBus + ", " + bean.connectedBus); eql = 1; }
		*/
		
		return eql;
	}	

	@Override
	public boolean validate(List<String> msgList) {
		// TODO Auto-generated method stub
		return false;
	}

}
