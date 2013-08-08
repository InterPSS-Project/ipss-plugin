/*
 * @(#)ODMAclfDataMapper.java   
 *
 * Copyright (C) 2008 www.interpss.org
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
 * @Date 11/15/2010
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm;

import org.ieee.odm.schema.LoadflowNetXmlType;
import org.interpss.mapper.odm.impl.aclf.AbstractODMAclfNetMapper;

/**
 * mapper implementation to map ODM to InterPSS object model for Aclf
 * 
 * @author mzhou
 *
 */
public class ODMAclfNetMapper extends AbstractODMAclfNetMapper<LoadflowNetXmlType> {
	/**
	 * Xformer branch model
	 * 
	 * @author mzhou
	 *
	 */
	public static enum XfrBranchModel { InterPSS, PSSE}
	
	/**
	 * constructor
	 */
	public ODMAclfNetMapper() {}
}