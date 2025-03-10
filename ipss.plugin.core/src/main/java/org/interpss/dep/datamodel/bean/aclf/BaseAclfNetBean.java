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

package org.interpss.dep.datamodel.bean.aclf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.interpss.dep.datamodel.bean.base.BaseJSONBean;
import org.interpss.dep.datamodel.bean.base.BaseJSONUtilBean;
import org.interpss.dep.datamodel.bean.base.BaseNetBean;

import com.interpss.common.util.NetUtilFunc;

/**
 * Base Bean class for storing AclfNetwork object info
 * 
 * @author mzhou
 *
 * @param <TBus> template for AclfBusBean
 * @param <TBra> template for AclfBranchBean
 * @param <TBusExt> template for AclfBusBeanBean extension info
 * @param <TBraExt> template for AclfBranchBeanBean extension info
 * @param <TNetExt> template for AclfNetBean extension info
 */
public class BaseAclfNetBean<TBus extends AclfBusBean<TBusExt>, 
                             TBra extends AclfBranchBean<TBraExt>, 
                             TBusExt extends BaseJSONUtilBean, 
                             TBraExt extends BaseJSONUtilBean,
                             TNetExt extends BaseJSONUtilBean> extends BaseNetBean<TNetExt> {
	
	private List<TBus> busBeanList;					// bus result bean list
	private transient Map<String,TBus> busIdMapper;
	
	private List<TBra> branchBeanList;                // branch result bean list
	private transient Map<String,TBra> branchIdMapper;
	
	public BaseAclfNetBean() { 
		busBeanList = new ArrayList<TBus>(); 
		busIdMapper = new HashMap<>();
		branchBeanList = new ArrayList<TBra>(); 
		branchIdMapper = new HashMap<>();	
	}
	
	public List<TBus> getBusBeanList() {
		return busBeanList;
	}
	
	public List<TBra> getBranchBeanList() {
		return branchBeanList;
	}
	
	public void addBusBean(TBus bus) {
		this.busBeanList.add((TBus)bus);
		this.busIdMapper.put(bus.id, (TBus)bus);			
	}

	public void addBranchBean(TBra branch) {
		this.branchBeanList.add((TBra)branch);
		this.branchIdMapper.put(branch.id, (TBra)branch);		
	}
	
	@Override public int compareTo(BaseJSONBean<TNetExt> b) {
		int eql = super.compareTo(b);
		
		BaseAclfNetBean<TBus,TBra,TBusExt,TBraExt,TNetExt> netBean = (BaseAclfNetBean<TBus,TBra, TBusExt, TBraExt, TNetExt>)b;

		for (TBus bus : this.busBeanList) 
			if (bus.compareTo(netBean.getBus(bus.id)) != 0) 
				eql = 1; 
		
		for (TBra bra : this.branchBeanList)
			if (bra.compareTo(netBean.getBranch(bra.id)) != 0) 
				eql = 1; 

		return eql;
	}	
	
	public TBus getBus(String busId) {
		return this.busIdMapper.get(busId);
	}
	
	public AclfBusBean<TBusExt> createAclfBusBean(String busId) {
		AclfBusBean<TBusExt> bus = new AclfBusBean<>();
		bus.id = busId;
		this.addBusBean((TBus)bus);
		return bus;
	}
	
	public AclfBranchBean<TBraExt> createAclfBranchBean(String fromId, String toId, String cirId) {
		AclfBranchBean<TBraExt> bra = new AclfBranchBean<>();
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
		
		for (TBus bean : this.busBeanList) 
			if (!bean.validate(msgList))
				noErr = false;
		for (TBra bean : this.branchBeanList) 
			if (!bean.validate(msgList))
				noErr = false;
		return noErr; 
	}	
}