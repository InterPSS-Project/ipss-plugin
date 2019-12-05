/*
 * @(#)BaseNetBeanComparator.java   
 *
 * Copyright (C) 2008-2019 www.interpss.org
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
 * @Date 12/10/2019
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss.datamodel.util;

import java.util.List;

import org.interpss.datamodel.bean.BaseNetBean;

/**
 * Base NetBean comparator implementation.
 * 
 * @author mzhou
 *
 */
public abstract class BaseNetBeanComparator<TNet extends BaseNetBean> implements INetBeanComparator<TNet> {
	// comparison log option
	private CompareLog logOpt;
	
	// to store comparison log message list
	private List<String> logMsgList;
	
	/**
	 * Default constructor
	 * 
	 * @param opt
	 */
	public BaseNetBeanComparator(CompareLog opt) {
		this.logOpt = opt;
	}
	
	@Override public int compare(BaseNetBean net1, BaseNetBean net2) {
		net1.setCompareLog(this.logOpt);
		this.logMsgList = net1.getMsgList();
		
		return 0;
	}	
	
	@Override
	public List<String> getMsgList() {
		return logMsgList;
	}	
}
