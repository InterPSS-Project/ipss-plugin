/*
 * @(#)GainExpFunction.java   
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
import com.interpss.dstab.controller.block.adapt.CMLFunctionExpressionAdapter;
import com.interpss.dstab.datatype.ExpCalculator;

/**
 * A function for calculating y = k * (u1-u2+u3)
 * 
 * @author mzhou
 *
 */
public class GainExpFunction extends CMLFunctionExpressionAdapter {
	private double k = 1.0;

	/**
	 * constructor
	 * 
	 * @param k
	 */
	public GainExpFunction(double k) {
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
	 * evaluate function value based on the input 2D double array. The array matches the input expression list
	 *
	 * @param dAry2D contains only array [uAry]
	 * @return the function value
	 */
	@Override public double eval(double[][] dAry2D) throws InterpssException {
		ExpCalculator uExp = getInputExpList().get(0);
		return this.k * uExp.eval(dAry2D[0]);
	}
}
