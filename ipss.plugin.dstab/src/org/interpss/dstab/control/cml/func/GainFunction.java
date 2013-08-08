/*
 * @(#)GainFunction.java   
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
 * @Date 10/30/2006
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.dstab.control.cml.func;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.controller.block.adapt.CMLFunctionAdapter;

/**
 * A function for calculating y = k * u
 * 
 * @author mzhou
 *
 */
public class GainFunction extends CMLFunctionAdapter {
	private double k = 1.0;

	/**
	 * constructor
	 * 
	 * @param k
	 */
	public GainFunction(double k) {
		this.k = k;
	}

	/**
	 * Calculate input from the output value
	 * 
	 * @return input u
	 */
	@Override public double getU(double y) throws InterpssException {
		return y / this.k;
	}

	/**
	 * evaluate function value based on the input double array. The array matches the input var rec list
	 *
	 * @param dAry contains only one value
	 * @return the function value
	 */
	@Override public double eval(double[] dAry) {
		return this.k * dAry[0];
	}
}
