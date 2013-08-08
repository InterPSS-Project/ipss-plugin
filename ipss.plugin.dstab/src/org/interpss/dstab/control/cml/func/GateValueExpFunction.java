/*
 * @(#)GateValueExpFunction.java   
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

public class GateValueExpFunction extends CMLFunctionExpressionAdapter {
	/**
	 * evaluate function value based on the input 2D double array. The array matches the input expression list
	 *
	 * @param dAry2D contains two arrays [u1Ary, u2Ary]
	 * @return the function value
	 */
	public double eval(double[][] dAry2D, boolean highValue) throws InterpssException {
		// always there are two input value arrays
		ExpCalculator u1Exp = getInputExpList().get(0);
		ExpCalculator u2Exp = getInputExpList().get(1);
		double u1 = u1Exp.eval(dAry2D[0]);
		double u2 = u2Exp.eval(dAry2D[1]);
		if (highValue)
			return u1 > u2 ? u1 : u2;
		else
			return u1 < u2 ? u1 : u2;
	}
}
