/*
 * @(#)AclfNetResultBean.java   
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

import java.util.ArrayList;
import java.util.List;

import org.interpss.datamodel.bean.BaseNetBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.datamodel.bean.datatype.MismatchResultBean;

public class AclfNetResultBean extends BaseNetBean {
	public boolean
		lf_converge;				// AC loadflow convergence
	
	public ComplexBean
		gen,						// total gen power
		load,						// total load power
		loss;						// total network power loss
	
	public MismatchResultBean
		max_mis;					// max mismatch
	
	public List<AclfBusBean> 
		bus_list;					// bus bean list
	public List<AclfBranchResultBean> 
		branch_list;                // branch bean list
	
	public AclfNetResultBean() { bus_list = new ArrayList<AclfBusBean>(); branch_list = new ArrayList<AclfBranchResultBean>(); }
}