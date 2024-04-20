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
 * A ref data object of type double[]
 * 
 * @author mzhou
 *
 */
public class DblAryRefValue extends ValueBaseId {
	/**
	 * object ref
	 */
	public Object ref;
	/**
	 * double[] field
	 */
	public double[] aryValue;
	
	/**
	 * constructor
	 * 
	 * @param x
	 */
	public DblAryRefValue(double[] x) {
		this.aryValue = x;
	}

	/**
	 * constructor
	 * 
	 * @param id
	 * @param x
	 */
	public DblAryRefValue(String id, double[] x) {
		this.id = id;
		this.aryValue = x;
	}
	
	/**
	 * constructor
	 * 
	 * @param bus
	 * @param x
	 */
	public DblAryRefValue(Object ref, double[] x) {
		this.ref = ref;
		this.aryValue = x;
	}

	/**
	 * constructor
	 * 
	 * @param x
	 */
	public DblAryRefValue(int size) {
		this.aryValue = new double[size];
	}

	/**
	 * constructor
	 * 
	 * @param id
	 * @param x
	 */
	public DblAryRefValue(String id, int size) {
		this.id = id;
		this.aryValue = new double[size];
	}
	
	/**
	 * constructor
	 * 
	 * @param bus
	 * @param x
	 */
	public DblAryRefValue(Object ref, int size) {
		this.ref = ref;
		this.aryValue = new double[size];
	}
}
