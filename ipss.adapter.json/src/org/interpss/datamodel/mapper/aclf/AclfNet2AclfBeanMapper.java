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

package org.interpss.datamodel.mapper.aclf;

import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * mapper implementation to map AclfNetwork object to AclfNetBean
 * 
 * 
 */
public class AclfNet2AclfBeanMapper extends BaseAclfNet2BeanMapper<AclfNetBean> {
	/**
	 * constructor
	 */
	public AclfNet2AclfBeanMapper() {
	}
	
	/**
	 * map an AclfNetwork object to a AclfNetBean object
	 * 
	 * @param aclfNet AclfNetwork object
	 * @return AclfNetBean object
	 */
	@Override public AclfNetBean map2Model(AclfNetwork aclfNet) throws InterpssException {
		AclfNetBean aclfResult = new AclfNetBean();

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
	@Override public boolean map2Model(AclfNetwork aclfNet, AclfNetBean netBean) {
		boolean noError = true;
		
		netBean.base_kva = aclfNet.getBaseKva();			
		
		for (AclfBus bus : aclfNet.getBusList()) {
			AclfBusBean bean = new AclfBusBean();
			mapBaseBus(bus, bean);
			netBean.addBusBean(bean);
		}
		
		for (AclfBranch branch : aclfNet.getBranchList()) {
			AclfBranchBean bean = new AclfBranchBean();
			mapBaseBranch(branch, bean);
			netBean.addBranchBean(bean);
		}

		return noError;
	}	
}