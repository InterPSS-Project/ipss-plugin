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

import org.interpss.datamodel.bean.datatype.ComplexBean;

public class AclfBranchResultBean extends AclfBranchBean {

	public ComplexBean 
		flow_f2t,		// branch power flow from->to
	    flow_t2f,		// branch power flow to->from
	    loss;			// branch power loss
	
	public double 
		cur;			// branch current in amps, for Xfr, it is at the high voltage side
	
	public AclfBranchResultBean() { }
}