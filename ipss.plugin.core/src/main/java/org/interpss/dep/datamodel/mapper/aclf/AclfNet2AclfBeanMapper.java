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

package org.interpss.dep.datamodel.mapper.aclf;

import org.interpss.dep.datamodel.bean.aclf.AclfNetBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfBranchResultBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfBusResultBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfNetResultBean;
import org.interpss.dep.datamodel.mapper.base.BaseAclfNet2AclfBeanMapper;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;


/**
 * mapper implementation to map AclfNetwork object to AclfNetBean
 * 
 * 
 */
public class AclfNet2AclfBeanMapper extends BaseAclfNet2AclfBeanMapper<AclfBusResultBean, AclfBranchResultBean, AclfNetResultBean> {
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
		AclfNetBean bean = new AclfNetBean();
		super.map2Model(aclfNet, bean);
		return bean;
	}	

}