/*
 * @(#) AclfNet2BeanMapper.java   
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
 * @Date 01/15/2013
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.dep.datamodel.mapper.base;

import org.interpss.dep.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.dep.datamodel.bean.aclf.AclfBusBean;
import org.interpss.dep.datamodel.bean.aclf.BaseAclfNetBean;
import org.interpss.dep.datamodel.bean.base.BaseJSONUtilBean;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.mapper.AbstractMapper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * mapper implementation to map AclfNetwork object to AclfNetBean
 * 
 * 
 */
public class BaseAclfNet2AclfBeanMapper<
					TBusExt extends BaseJSONUtilBean, 
                    TBraExt extends BaseJSONUtilBean,
                    TNetExt extends BaseJSONUtilBean> extends AbstractMapper<
                                                AclfNetwork,
                                                BaseAclfNetBean<AclfBusBean<TBusExt>, 
                                                                AclfBranchBean<TBraExt>,
                                                                TBusExt,TBraExt,TNetExt>> {
	/**
	 * constructor
	 */
	public BaseAclfNet2AclfBeanMapper() {
	}
	
	/**
	 * map an AclfNetwork object to a AclfNetBean object
	 * 
	 * @param aclfNet AclfNetwork object
	 * @return AclfNetBean object
	 */
	@Override public BaseAclfNetBean<AclfBusBean<TBusExt>,AclfBranchBean<TBraExt>,TBusExt,TBraExt,TNetExt> map2Model(AclfNetwork aclfNet) throws InterpssException {
		BaseAclfNetBean<AclfBusBean<TBusExt>,AclfBranchBean<TBraExt>,TBusExt,TBraExt,TNetExt> aclfResult = new BaseAclfNetBean<>();

		if (map2Model(aclfNet, aclfResult))
			return aclfResult;
		else
			throw new InterpssException("Error during mapping AclfNetwork to AclfNetBean");
	}	
	
	/**
	 * map an AclfNetwork object to a AclfNetBean object
	 * 
	 * @param aclfNet AclfNetwork object
	 * @param AclfNetBean object
	 * @return false if there is any issue during the mapping process
	 */
	@Override public boolean map2Model(AclfNetwork aclfNet, BaseAclfNetBean<AclfBusBean<TBusExt>,AclfBranchBean<TBraExt>,TBusExt,TBraExt,TNetExt> netBean) {
		boolean noError = true;
		
		netBean.base_kva = aclfNet.getBaseKva();			
		
		for (AclfBus bus : aclfNet.getBusList()) {
			AclfBusBean<TBusExt> bean = new AclfBusBean<>();
			AclfNet2BeanUtilFunc.mapAclfBus(bus, bean);
			netBean.addBusBean(bean);
		}
		
		for (AclfBranch branch : aclfNet.getBranchList()) {
			AclfBranchBean<TBraExt> bean = new AclfBranchBean<>();
			AclfNet2BeanUtilFunc.mapAclfBranch(branch, bean);
			netBean.addBranchBean(bean);
		}

		return noError;
	}	
}