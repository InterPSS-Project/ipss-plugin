/*
 * @(#)AclfBusBean.java   
 *
 * Copyright (C) 2008-2013 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * @Author Mike Zhou
 * @Version 1.0
 * @Date 01/10/2013
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss.datamodel.bean.aclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBusBean;
import org.interpss.datamodel.bean.BaseJSONBean;

/**
 * Bean class for storing AclfBus info
 * 
 * @author mzhou
 *
 */
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
		
		String str = "ID: " + this.id + " AclfBusBean.";

		if (this.gen_code != bean.gen_code) {
			logCompareMsg(str + "gen_code is not equal, " + this.gen_code + ", " + bean.gen_code); eql = 1; }
		if (this.load_code != bean.load_code) {
			logCompareMsg(str + "load_code is not equal, " + this.load_code + ", " + bean.load_code); eql = 1; }

		return eql;
	}	
	
	@Override public boolean validate(List<String> msgList) { 
		return true;
	}
}
