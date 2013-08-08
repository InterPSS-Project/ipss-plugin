/*
 * @(#)VthevFunction.java   
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

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.controller.block.adapt.CMLFunctionAdapter;

/**
 * Function : y = | Kp * Vt + j Ki * It |
 * 
 * @author mzhou
 * 
 */
public class VthevFunction extends CMLFunctionAdapter {
	private double kp = 1.0;
	private double ki = 1.0;

	/**
	 * constructor
	 * 
	 * @param kp
	 * @param ki
	 */
	public VthevFunction(double kp, double ki) {
		this.kp = kp;
		this.ki = ki;
	}

	/**
	 * evaluate function value based on the input double array. The array
	 * matches the input var rec list
	 * 
	 * @param dAry contains two values [ Vt, It ]
	 * @return the function value
	 */
	@Override public double eval(double[] dAry) {
		double vt = dAry[0];
		double it = dAry[1];
		return new Complex(this.kp * vt, this.ki * it).abs();
	}

	@Override public String toString() {
		String str = "Kp, Ki: " + kp + ", " + ki;
		return str;
	}
}
