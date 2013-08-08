/*
 * @(#)IMultiNetProcessor.java   
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
 * @Date 03/15/2012
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm;

import org.ieee.odm.schema.NetworkXmlType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;

/**
 * interface for processing multi-network
 * 
 * @author mzhou
 *
 */
public interface IMultiNetProcessor {
	/**
	 * get the main parent network object
	 * 
	 * @param net
	 * @return
	 */
	IMultiNetProcessor setMainNet(AclfNetwork net);
	
	/**
	 * process the child network xml object
	 * 
	 * @param netXml
	 */
	void process(NetworkXmlType netXml) throws InterpssException;
}
