/*
 * @(#)BaseAclfNetBean.java   
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

import com.interpss.common.util.NetUtilFunc;

/**
 * Base Bean class for storing AclfNetwork object info
 * 
 * @author mzhou
 *
 * @param <TBus> template for AclfBusBean
 * @param <TBra> template for AclfBranchBean
 */
public class BaseAclfNetBean<TBus extends AclfBusBean, TBra extends AclfBranchBean> extends BaseNetBean {
	
	public List<TBus> 
		bus_list;					// bus result bean list
	public List<TBra> 
		branch_list;                // branch result bean list
	
	public BaseAclfNetBean() { bus_list = new ArrayList<TBus>(); branch_list = new ArrayList<TBra>(); }
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		BaseAclfNetBean<TBus,TBra> bean = (BaseAclfNetBean<TBus,TBra>)b;

		for (TBus bus : this.bus_list) 
			if (bus.compareTo(bean.getBus(bus.id)) != 0) eql = 1; 
		
		for (TBra bra : this.branch_list)
			if (bra.compareTo(bean.getBranch(bra.id)) != 0) eql = 1; 

		return eql;
	}	
	
	public TBus getBus(String id) {
		for (TBus bus : this.bus_list) {
			if (bus.id.equals(id))
				return bus;
		}
		//logCompareMsg("Bus " + id + " cannot be found");
		return null;
	}
	
	public AclfBusBean createAclfBusBean(String id) {
		AclfBusBean bus = new AclfBusBean();
		bus.id = id;
		this.bus_list.add((TBus)bus);
		return bus;
	}
	
	public AclfBranchBean createAclfBranchBean(String fromId, String toId, String cirId) {
		AclfBranchBean bra = new AclfBranchBean();
		bra.f_id = fromId;
		bra.t_id = toId;
		bra.cir_id = cirId;
		bra.id = NetUtilFunc.ToBranchId.f(fromId, toId, cirId);
		this.branch_list.add((TBra)bra);
		return bra;
	}
	
	public AclfBranchResultBean createAclfBranchResultBean(String fromId, String toId, String cirId) {
		AclfBranchResultBean bra = new AclfBranchResultBean();
		bra.f_id = fromId;
		bra.t_id = toId;
		bra.cir_id = cirId;
		bra.id = NetUtilFunc.ToBranchId.f(fromId, toId, cirId);
		this.branch_list.add((TBra)bra);
		return bra;
	}
	
	public TBra getBranch(String id) {
		for (TBra bra : this.branch_list) {
			if (bra.id.equals(id))
				return bra;
		}
		//logCompareMsg("Branch " + id + " cannot be found");
		return null;
	}
	
	public TBra getBranch(String fId, String tId, String cirId) {
		for (TBra bra : this.branch_list) {
			if (bra.f_id.equals(fId) && bra.t_id.equals(tId) && bra.cir_id.equals(cirId))
				return bra;
		}
		//logCompareMsg("Branch " + fId + "->" + tId + "(" + cirId + ") cannot be found");
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