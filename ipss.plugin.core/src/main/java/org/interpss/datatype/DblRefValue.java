/*
  * @(#)DblRefValue.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.datatype;

import org.interpss.datatype.base.ValueBaseId;


/**
 * A bus data object of type double
 * 
 * @author mzhou
 *
 */
public class DblRefValue extends ValueBaseId {
	public Object ref;
	public double value;
	
	/**
	 * constructor
	 * 
	 * @param x
	 */
	public DblRefValue(double x) {
		this.value = x;
	}

	/**
	 * constructor
	 * 
	 * @param id
	 * @param x
	 */
	public DblRefValue(String id, double x) {
		this.id = id;
		this.value = x;
	}
	
	/**
	 * constructor
	 * 
	 * @param bus
	 * @param x
	 */
	public DblRefValue(Object ref, double x) {
		this.ref = ref;
		this.value = x;
	}
}
