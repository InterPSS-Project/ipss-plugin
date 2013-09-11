package org.interpss.datamodel.bean.aclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBusBean;
import org.interpss.datamodel.bean.BaseJSONBean;

import com.interpss.common.util.IpssLogger;

public class AclfBusBean  extends BaseBusBean {	

	/**
	 * bus generator type code 
	 */
	public static enum GenCode {Swing, PV, PQ, NonGen};
	
	/**
	 * bus load type code 
	 */
	public static enum LoadCode {ConstP, ConstI, ConstZ, NonLoad};	
	
	
	public GenCode 
		gen_code = GenCode.NonGen;				// bus generator code
	
	public LoadCode 
		load_code = LoadCode.NonLoad;				// bus load code	
		
	public AclfBusBean() {}
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		AclfBusBean bean = (AclfBusBean)b;

		if (this.gen_code != bean.gen_code) {
			IpssLogger.ipssLogger.warning("AclfBusBean.gen_code is not equal");
			eql = 1;
		}

		return eql;
	}	
	
	public boolean validate(List<String> msgList) { 
		return true;
	}
}
