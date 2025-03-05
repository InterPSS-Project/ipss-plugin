/*
 * @(#)BranchValueBean.java   
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

package org.interpss.dep.datamodel.bean.datatype;

import org.interpss.dep.datamodel.bean.base.BaseJSONBean;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.util.IpssLogger;

/**
 * Bean class for storing limit value
 * 
 * @author mzhou
 *
 */
public class LimitValueBean  implements Comparable<LimitValueBean> {
	public double
		max ,				
		min ;				
	
	public LimitValueBean() { }
	public LimitValueBean(double max, double min) { this.max = max; this.min = min; }
	
	/**
	 * compare this object with the bean object
	 * 
	 * @param bean the bean object to be compared with this object
	 * @return 0 if the two objects are equal, 1 if not equal
	 */
	@Override public int compareTo(LimitValueBean bean) {
		int eql = 0;
		
		if (!NumericUtil.equals(this.max, bean.max, BaseJSONBean.PU_ERR)) {
			IpssLogger.ipssLogger.warning("BranchValueBean.f is not equal, " + this.max + ", " + bean.max); eql = 1; }
		
		if (!NumericUtil.equals(this.min, bean.min, BaseJSONBean.PU_ERR)) {
			IpssLogger.ipssLogger.warning("BranchValueBean.t is not equal, " + this.min + ", " + bean.min); eql = 1; }	
		
		return eql;
	}	
}