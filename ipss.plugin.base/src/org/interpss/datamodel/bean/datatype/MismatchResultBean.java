/*
 * @(#)MismatchResultBean.java   
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

package org.interpss.datamodel.bean.datatype;

import com.interpss.common.util.IpssLogger;

/**
 * Bean for storing AC Loadflow mismatch info
 * 
 * @author mzhou
 *
 */
public class MismatchResultBean implements Comparable<MismatchResultBean> {
	public ComplexBean
		err;				// real/reactive power mismatch
	
	public String
	 	p_bus_id,			// real power mismatch bus id
 		q_bus_id;       	// reactive power mismatch bus id

	public MismatchResultBean() { }
	
	/**
	 * compare this object with the bean object
	 * 
	 * @param bean the bean object to be compared with this object
	 * @return 0 if the two objects are equal, 1 if not equal
	 */
	@Override public int compareTo(MismatchResultBean bean) {
		int eql = 0;
		
		if (this.err.compareTo(bean.err) != 0) {
			IpssLogger.ipssLogger.warning("MismatchResultBean.err is not equal"); eql = 1; }
		
		if (!this.p_bus_id.equals(bean.p_bus_id)) {
			IpssLogger.ipssLogger.warning("MismatchResultBean.p_bus_id is not equal"); eql = 1; }
		if (!this.q_bus_id.equals(bean.q_bus_id)) {
			IpssLogger.ipssLogger.warning("MismatchResultBean.q_bus_id is not equal"); eql = 1; }
		
		return eql;
	}	
}