/*
 * @(#) LimitSetHelper.java   
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
 * @Date 04/01/2013
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.aclf;

import java.util.Hashtable;

import org.ieee.odm.schema.LimitSetXmlType;
import org.ieee.odm.schema.PWDNetworkExtXmlType;

import com.interpss.common.exp.InterpssException;

/**
 * LimitSet helper
 * 
 * @author mzhou
 *
 */
public class LimitSetHelper {
	private Hashtable<String,Boolean> lookupTable = null;
	
	/**
	 * constructor
	 * 
	 * @param limitSets
	 */
	public LimitSetHelper(PWDNetworkExtXmlType.LimitSets limitSets) {
		this.lookupTable = new Hashtable<>();
		for (LimitSetXmlType lset : limitSets.getLimitSet()) {
			this.lookupTable.put(lset.getName(), lset.isLsDiabled());
		}
	}
	
	/**
	 * check the status of the LimitSet
	 * 
	 * @param lsName
	 * @return
	 * @throws InterpssException
	 */
	public boolean isDisabled(String lsName) throws InterpssException {
		if (lsName == null)
			return true;
		if (this.lookupTable.get(lsName) == null)
			throw new InterpssException("LimitSet not defined, " + lsName);
		return this.lookupTable.get(lsName);
	}
}