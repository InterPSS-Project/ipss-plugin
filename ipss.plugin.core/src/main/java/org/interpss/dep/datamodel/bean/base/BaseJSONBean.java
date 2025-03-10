/*
 * @(#)BaseJSONBean.java   
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

package org.interpss.dep.datamodel.bean.base;

import java.util.Comparator;
import java.util.List;

import org.interpss.dep.datamodel.util.INetBeanComparator.CompareLog;

import com.interpss.common.util.IpssLogger;

/**
 * Base bean class. The bean data model is intended for use in combination
 * with JSON. Wire communication efficiency is considered in the data structure
 * design while preserve readability.
 * 
 * @author mzhou
 * @param <TExt> template for extension info 
 *
 */
public abstract class BaseJSONBean<TExt extends BaseJSONUtilBean> extends BaseJSONUtilBean implements Comparable<BaseJSONBean<TExt>> {
	
	/**
	 * default error tolerance for Bean object comparison for value in PU
	 */
	public static double PU_ERR = 0.0001;
	
	/**
	 * error tolerance for amps value comparison
	 */
	public static double CUR_ERR = 0.1;
	
	/**
	 * error tolerance for angle in deg value comparison
	 */
	public static double ANG_ERR = 0.01;

	public String 	    
		id = "Not Defined", 	// net, bus, branch id. only bus.id is mandatory
		name,    				// net, bus, branch name, optional
		desc,    				// net, bus, branch description, optional
		info;                   // extra info

	public TExt extension;      // for bean extension
	
	/**
	 * compare this object with the bean object
	 * 
	 * @param bean the bean object to be compared with this object
	 * @return 0 if the two objects are equal, 1 if not equal
	 */
	@Override public int compareTo(BaseJSONBean<TExt> bean) {
		if (this.id.equals(bean.id))
			// some times bean.id is not defined
			return 0;
		else {
			logCompareMsg("BaseJSONBean.id is not equal");
			return 1;
		}
	}
	
	/**
	 * compare this object with the bean object using the comparator
	 * 
	 * @param bean the bean object to be compared with this object
	 * @param comparator 
	 * @return 0 if the two objects are equal, 1 if not equal
	 */
	public int compare(BaseJSONBean<TExt> bean, Comparator<BaseJSONBean<TExt>> comparator) {
		return comparator.compare(this, bean);
	}
	

	/**
	 * validate this object
	 * 
	 * @param msgList contains err messages during the validation.
	 * @return true if passed the validation
	 */
	abstract public boolean validate(List<String> msgList); 
	
	/**
	 * log warning msg during the comparison process
	 * 
	 * @param msg
	 */
	public void logCompareMsg(String msg) {
		if (compareLog == CompareLog.Console)
			IpssLogger.ipssLogger.warning(msg);
		else
			getMsgList().add(msg);
	}
}
