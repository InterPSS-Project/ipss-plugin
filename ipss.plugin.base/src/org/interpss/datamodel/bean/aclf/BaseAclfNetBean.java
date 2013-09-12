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

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.BaseNetBean;

import com.interpss.common.util.IpssLogger;

public class BaseAclfNetBean<TBus extends AclfBusBean, TBra extends AclfBranchBean> extends BaseNetBean {
	
	public List<TBus> 
		bus_list;					// bus result bean list
	public List<TBra> 
		branch_list;                // branch result bean list
	
	public BaseAclfNetBean() { bus_list = new ArrayList<TBus>(); branch_list = new ArrayList<TBra>(); }
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		BaseAclfNetBean<TBus,TBra> bean = (BaseAclfNetBean<TBus,TBra>)b;

		for (TBus bus : this.bus_list) {
			TBus bus1 = bean.getBus(bus.id);
			if (bus.compareTo(bus1) != 0)
				eql = 1;
		}
		
		for (TBra bra : this.branch_list) {
			TBra bra1 = bean.getBranch(bra.id);
			if (bra.compareTo(bra1) != 0)
				eql = 1;
		}

		return eql;
	}	
	
	public TBus getBus(String id) {
		for (TBus bus : this.bus_list) {
			if (bus.id.equals(id))
				return bus;
		}
		IpssLogger.ipssLogger.warning("Bus " + id + " cannot be found");
		return null;
	}
	
	public TBra getBranch(String id) {
		for (TBra bra : this.branch_list) {
			if (bra.id.equals(id))
				return bra;
		}
		IpssLogger.ipssLogger.warning("Branch " + id + " cannot be found");
		return null;
	}
	
	public boolean validate(List<String> msgList) {
		boolean noErr = super.validate(msgList);
		
		for (TBus bean : this.bus_list) 
			if (!bean.validate(msgList))
				noErr = false;
		for (TBra bean : this.branch_list) 
			if (!bean.validate(msgList))
				noErr = false;
		return noErr; 
	}	
}