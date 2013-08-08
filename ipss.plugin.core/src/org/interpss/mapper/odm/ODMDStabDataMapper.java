/*
 * @(#)ODMDStabDataMapper.java   
 *
 * Copyright (C) 2008-2010 www.interpss.org
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
 * @Author Mike Zhou, Stephen Hau
 * @Version 1.0
 * @Date 11/15/2010
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm;

import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.mapper.odm.impl.dstab.AbstractODMDStabDataMapper;

import com.interpss.common.msg.IPSSMsgHub;

/**
 * mapper implementation to map ODM to InterPSS object model for DStab
 * 
 * @author mzhou
 *
 */
public class ODMDStabDataMapper extends AbstractODMDStabDataMapper<DStabModelParser> {
	/**
	 * constructor
	 * 
	 * @param msg
	 */
	public ODMDStabDataMapper(IPSSMsgHub msg) {
		this.msg = msg;
	}
}