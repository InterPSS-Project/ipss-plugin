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
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.BaseJSONUtilBean;
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
public class BaseAclfNetBean<TBus extends AclfBusBean<TExt>, TBra extends AclfBranchBean<TExt>, TExt extends BaseJSONUtilBean> extends BaseNetBean<TExt> {
	
	private List<TBus> bus_list;					// bus result bean list
	private Map<String,TBus> busIdMapper;
	
	private List<TBra> branch_list;                // branch result bean list
	private Map<String,TBra> branchIdMapper;
	
	public BaseAclfNetBean() { 
		bus_list = new ArrayList<TBus>(); 
		busIdMapper = new Hashtable<>();
		branch_list = new ArrayList<TBra>(); 
		branchIdMapper = new Hashtable<>();	
	}
	
	public List<TBus> getBusBeanList() {
		return bus_list;
	}
	
	public List<TBra> getBranchBeanList() {
		return branch_list;
	}
	
	public void addBusBean(TBus bus) {
		this.bus_list.add((TBus)bus);
		this.busIdMapper.put(bus.id, (TBus)bus);			
	}

	public void addBranchBean(TBra branch) {
		this.branch_list.add((TBra)branch);
		this.branchIdMapper.put(branch.id, (TBra)branch);		
	}
	
	@Override public int compareTo(BaseJSONBean<TExt> b) {
		int eql = super.compareTo(b);
		
		BaseAclfNetBean<TBus,TBra,TExt> netBean = (BaseAclfNetBean<TBus,TBra, TExt>)b;

		for (TBus bus : this.bus_list) 
			if (bus.compareTo(netBean.getBus(bus.id)) != 0) 
				eql = 1; 
		
		for (TBra bra : this.branch_list)
			if (bra.compareTo(netBean.getBranch(bra.id)) != 0) 
				eql = 1; 

		return eql;
	}	
	
	public TBus getBus(String busId) {
		return this.busIdMapper.get(busId);
	}
	
	public AclfBusBean<TExt> createAclfBusBean(String busId) {
		AclfBusBean<TExt> bus = new AclfBusBean<>();
		bus.id = busId;
		this.addBusBean((TBus)bus);
		return bus;
	}
	
	public AclfBranchBean<TExt> createAclfBranchBean(String fromId, String toId, String cirId) {
		AclfBranchBean<TExt> bra = new AclfBranchBean<>();
		bra.f_id = fromId;
		bra.t_id = toId;
		bra.cir_id = cirId;
		bra.id = NetUtilFunc.ToBranchId.f(fromId, toId, cirId);
		this.addBranchBean((TBra)bra);
		return bra;
	}
	
	public AclfBranchResultBean<TExt> createAclfBranchResultBean(String fromId, String toId, String cirId) {
		AclfBranchResultBean<TExt> bra = new AclfBranchResultBean<>();
		bra.f_id = fromId;
		bra.t_id = toId;
		bra.cir_id = cirId;
		bra.id = NetUtilFunc.ToBranchId.f(fromId, toId, cirId);
		this.addBranchBean((TBra)bra);
		return bra;
	}
	
	public TBra getBranch(String branchId) {
		return this.branchIdMapper.get(branchId);
	}
	
	public TBra getBranch(String fId, String tId, String cirId) {
		String braId = NetUtilFunc.ToBranchId.f(fId, tId, cirId);
		return this.branchIdMapper.get(braId);
	}

	@Override
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