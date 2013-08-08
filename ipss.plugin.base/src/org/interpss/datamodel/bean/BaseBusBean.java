package org.interpss.datamodel.bean;

import java.util.List;

import org.interpss.datamodel.bean.datatype.ComplexBean;

public class BaseBusBean extends BaseJSONBean{
		
	public long 
	    number;    
	
	public int status;
	
	public double
		base_v,				// bus base voltage
		v_mag=1.0,          // bus voltage in pu		
		v_ang = 0.0,		// bus voltage angle
	    vmax = 1.1,
	    vmin = 0.9;
		
	public ComplexBean
	     gen, 					// bus generation
	    load, 					// bus load
	    shunt;
	
	public long 
		area =1, 				// bus area number/id
		zone =1;				// bus zone number/id	
		
	public BaseBusBean() {}
	
	public boolean validate(List<String> msgList) { 
		return true;
	}
	

}
