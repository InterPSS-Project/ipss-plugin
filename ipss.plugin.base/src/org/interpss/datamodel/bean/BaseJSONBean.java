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

package org.interpss.datamodel.bean;

import java.util.List;

/**
 * Base bean class. The bean data model is intended for use in combination
 * with JSON. Wire communication efficiency is considered in the data structure
 * design while preserve readability.
 * 
 * @author mzhou
 *
 */
public abstract class BaseJSONBean {
	public String 	    
		id,      		// net, bus, branch id. only bus.id is mandatory
		name,    		// net, bus, branch name, optional
		desc;    		// net, bus, branch description, optional
	
	abstract public boolean validate(List<String> msgList); 
}
