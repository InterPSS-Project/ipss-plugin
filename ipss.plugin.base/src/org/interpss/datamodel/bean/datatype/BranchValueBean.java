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

package org.interpss.datamodel.bean.datatype;

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.util.IpssLogger;

/**
 * Bean class for storing branch value [fromSideValue, toSideValue]
 * 
 * @author mzhou
 *
 */
public class BranchValueBean  implements Comparable<BranchValueBean> {
	public double
		f ,				// value at the from side
		t ;				// value at the to side
	
	public BranchValueBean() { }
	public BranchValueBean(double f, double t) { this.f = f; this.t = t; }
	
	/**
	 * compare this object with the bean object
	 * 
	 * @param bean the bean object to be compared with this object
	 * @return 0 if the two objects are equal, 1 if not equal
	 */
	@Override public int compareTo(BranchValueBean bean) {
		int eql = 0;
		
		if (!NumericUtil.equals(this.f, bean.f, BaseJSONBean.PU_ERR)) {
			IpssLogger.ipssLogger.warning("BranchValueBean.f is not equal, " + this.f + ", " + bean.f); eql = 1; }
		
		if (!NumericUtil.equals(this.t, bean.t, BaseJSONBean.PU_ERR)) {
			IpssLogger.ipssLogger.warning("BranchValueBean.t is not equal, " + this.t + ", " + bean.t); eql = 1; }	
		
		return eql;
	}	
}