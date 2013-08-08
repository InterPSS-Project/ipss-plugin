/*
 * @(#)AclfNetBean.java   
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

public class AclfNetBean extends BaseNetBean {
	
	public List<AclfBusBean> 
		bus_list;					// bus result bean list
	public List<AclfBranchBean> 
		branch_list;                // branch result bean list
	
	public AclfNetBean() { bus_list = new ArrayList<AclfBusBean>(); branch_list = new ArrayList<AclfBranchBean>(); }
	
	public boolean validate(List<String> msgList) {
		boolean noErr = super.validate(msgList);
		
		for (AclfBusBean bean : this.bus_list) 
			if (!bean.validate(msgList))
				noErr = false;
		for (AclfBranchBean bean : this.branch_list) 
			if (!bean.validate(msgList))
				noErr = false;
		return noErr; 
	}	
}