package org.interpss.datamodel.bean.aclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBusBean;

public class AclfBusBean  extends BaseBusBean {	

	/**
	 * bus generator type code 
	 */
	public static enum GenCode {Swing, PV, PQ};
	
	/**
	 * bus load type code 
	 */
	public static enum LoadCode {ConstP, ConstI, ConstZ};	
	
	
	public GenCode 
		gen_code;				// bus generator code
	
	public LoadCode 
		load_code;				// bus load code	
		
	public AclfBusBean() {}
	
	public boolean validate(List<String> msgList) { 
		return true;
	}
}
