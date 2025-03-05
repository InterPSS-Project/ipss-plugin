/*
 * @(#) AclfBean2NetMapper.java   
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

import org.interpss.dep.datamodel.bean.aclf.ext.AclfBranchResultBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfBusResultBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfNetResultBean;
import org.interpss.dep.datamodel.mapper.base.BaseAclfBean2AclfNetMapper;

/**
 * Mapper to map an AclfNetBean object to an AclfNetwork (SimuContext) object
 * 
 * @author mzhou
 *
 */
public class AclfBean2AclfNetMapper extends BaseAclfBean2AclfNetMapper<AclfBusResultBean, AclfBranchResultBean, AclfNetResultBean> {
	/**
	 * constructor
	 */
	public AclfBean2AclfNetMapper() {
	}
}