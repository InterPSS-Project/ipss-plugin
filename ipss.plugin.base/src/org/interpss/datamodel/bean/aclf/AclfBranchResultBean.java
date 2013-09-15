/*
 * @(#)AclfBusResultBean.java   
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

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.util.IpssLogger;

/**
 * Bean class for storing AclfBranch result info
 * 
 * @author mzhou
 *
 */
public class AclfBranchResultBean extends AclfBranchBean {

	public ComplexBean 
		flow_f2t,		// branch power flow from->to
	    flow_t2f,		// branch power flow to->from
	    loss;			// branch power loss
	
	public double 
		cur;			// branch current in amps, for Xfr, it is at the high voltage side
	
	public AclfBranchResultBean() { }
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		AclfBranchResultBean bean = (AclfBranchResultBean)b;
		
		String str = "ID: " + this.id + " AclfBranchResultBean.";

		if (this.flow_f2t.compareTo(bean.flow_f2t) != 0) {
			IpssLogger.ipssLogger.warning(str + "flow_f2t is not equal"); eql = 1; }
		if (this.flow_t2f.compareTo(bean.flow_t2f) != 0) {
			IpssLogger.ipssLogger.warning(str + "flow_t2f is not equal"); eql = 1; }
		if (this.loss.compareTo(bean.loss) != 0) {
			IpssLogger.ipssLogger.warning(str + "loss is not equal"); eql = 1; }
		
		if (!NumericUtil.equals(this.cur, bean.cur, BaseJSONBean.ACT_ERR)) {  // in amps
			IpssLogger.ipssLogger.warning(str + "cur is not equal, " + this.cur + ", " + bean.cur); eql = 1; }

		return eql;
	}	
}