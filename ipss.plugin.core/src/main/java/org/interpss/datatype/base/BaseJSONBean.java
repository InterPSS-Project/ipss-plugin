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

package org.interpss.datatype.base;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Base bean class. The bean data model is intended for use in combination
 * with JSON. Wire communication efficiency is considered in the data structure
 * design while preserve readability.
 * 
 * @author mzhou
 *
 */
public abstract class BaseJSONBean {
	
	@Override
	public String toString() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson(this);
	}
	
	@SuppressWarnings("unchecked") 
	public <T extends BaseJSONBean> T fromString(String json) {
		return (T) new Gson().fromJson(json, this.getClass());
	}
	
	
}
