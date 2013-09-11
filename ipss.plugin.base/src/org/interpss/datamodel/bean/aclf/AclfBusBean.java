package org.interpss.datamodel.bean.aclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBusBean;

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
	
	public boolean validate(List<String> msgList) { 
		return true;
	}
}
