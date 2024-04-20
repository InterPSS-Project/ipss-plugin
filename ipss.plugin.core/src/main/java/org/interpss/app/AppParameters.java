 /*
  * @(#)AppParameters.java   
  *
  * Copyright (C) 2006 www.interpss.org
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
  * @Date 01/30/2007
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.app;

import java.util.HashMap;
import java.util.Map;

/**
 * App launch parameter help class
 * 
 * @author mzhou
 *
 */
public class AppParameters {
	/**
	 * This is where we store every sort of session parameters
	 */
	private Map<String, String> appParameters;

	/**
	 * default constructor
	 */
	public AppParameters() {
		appParameters = new HashMap<String, String>();
	}

	/**
	 * set a kay-value pair
	 * 
	 * @param key
	 * @param value
	 */
	public void setParam(String key, String value) {
		appParameters.put(key, value);
	}

	/**
	 * get value by key
	 * 
	 * @param key
	 * @return
	 */
	public String getParam(String key) {
		Object object = appParameters.get(key);
		if (object != null) {
			return (String) object;
		}
		return null;
	}

	/**
	 * get value by key and turn the value string to lower case
	 * 
	 * @param key
	 * @return
	 */
	public String getParamLowerCase(String key) {
		if (getParam(key) != null)
			return getParam(key).toLowerCase();
		else 
			return null;
	}

	/**
	 * get the Map, where key-value pairs are stored
	 * 
	 * @return
	 */
	public Map<String, String> getSessionParameters() {
		return appParameters;
	}
}
